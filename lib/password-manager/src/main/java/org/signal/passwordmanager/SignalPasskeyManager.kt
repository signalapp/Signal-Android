/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.passwordmanager

import android.content.Context
import android.os.Build
import androidx.annotation.UiContext
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import kotlinx.serialization.SerializationException
import org.signal.core.util.Base64
import org.signal.core.util.PlayServicesUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.serialization.SignalJson
import java.io.IOException
import java.security.SecureRandom
import kotlin.time.Duration.Companion.seconds

/**
 * Runs WebAuthn ceremonies through Android's Credential Manager, which delegates to whichever passkey provider the user
 * has set up.
 *
 * WebAuthn is basically public key cryptography with some extra layers.
 *
 * Adding a passkey to the account is a registration ceremony with three main phases:
 *
 * (1) The service hands out the parameters. This has a bunch of metadata and config params.
 * (2) We take those params and give them to the CredentialManager, which performs a bunch of validations. If it's all
 *     good, the system will pop a bottom sheet letting them pick how they want to save their passkey. Then it'll create
 *     a public/private key pair, sign some stuff, and give you back a result that includes the public key.
 * (3) We send that result back to the service, which verifies and stores everything.
 *
 * That leaves the CredentialManager holding the private key and the service holding the public one.
 *
 * There's a similar three-phase ceremony for proving ownership of the pass key to the service:
 *
 * (1) The service hands out a challenge, along with the ids of the passkeys already on the account.
 * (2) We give those to the CredentialManager, which pops a bottom sheet asking the user to unlock one of them. It signs
 *     the challenge with the matching private key and gives you back a result that includes the signature.
 * (3) We send that result back to the service, which verifies the signature against the public key it stored during
 *     registration.
 *
 * This file manages phase (2) of these ceremonies: the CredentialManager stuff. Steps (1) and (3) are handled by the call sites,
 * since the network calls are situational and app-specific.
 *
 * Note: It *should* be the case that everything is configured properly for prod/staging environments and release/debug keys. But if you're
 * using your own debug key, it won't work.
 */
object SignalPasskeyManager {

  private val TAG = Log.tag(SignalPasskeyManager::class)

  /** How many bytes of local challenge a registration ceremony gets, the channel it runs over being authenticated already. */
  private const val CHALLENGE_LENGTH_BYTES = 32

  private val random = SecureRandom()

  /**
   * Whether this device can run a passkey ceremony at all.
   *
   * From API 34 the platform has its own credential provider. Below that the ceremony runs through Play services, which
   * only speaks passkeys from API 28. There's no API for telling whether the user has actually set a provider up, so a
   * capable device is assumed willing and only finds out otherwise once a ceremony comes back empty.
   */
  fun isSupported(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 34) {
      return true
    }

    if (Build.VERSION.SDK_INT < 28) {
      return false
    }

    return PlayServicesUtil.getPlayServicesStatus(context) == PlayServicesUtil.PlayServicesStatus.SUCCESS
  }

  /**
   * Asks the user's passkey provider to create a credential for the account, returning the two pieces the service needs
   * to verify the ceremony.
   *
   * @param rpId The relying party Id. A domain that needs to match what's on the server's digital asset links file.
   * @param userHandle The account's WebAuthn user handle, as issued by the service.
   * @param userName What the provider shows the user when it offers them the credential later.
   * @param allowedAlgorithms The COSE algorithm identifiers the service will accept a key for.
   * @param excludeCredentialIds Credentials already on the account, so a provider that holds one of them declines
   *   rather than registering a duplicate.
   */
  suspend fun createPasskey(
    @UiContext activityContext: Context,
    rpId: String,
    rpName: String,
    userHandle: ByteArray,
    userName: String,
    allowedAlgorithms: List<Int>,
    excludeCredentialIds: List<ByteArray>
  ): PasskeyCreationResult {
    val options = PublicKeyCredentialCreationOptions(
      rp = RelyingParty(id = rpId, name = rpName),
      user = User(
        id = Base64.encodeUrlSafeWithoutPadding(userHandle),
        name = userName,
        displayName = userName
      ),
      challenge = Base64.encodeUrlSafeWithoutPadding(randomChallenge()),
      pubKeyCredParams = allowedAlgorithms.map { PublicKeyCredentialParameters(alg = it) },
      excludeCredentials = credentialDescriptors(excludeCredentialIds)
    )

    val response = try {
      CredentialManager.create(activityContext).createCredential(
        context = activityContext,
        request = CreatePublicKeyCredentialRequest(requestJson = SignalJson.json.encodeToString(options))
      )
    } catch (e: CreateCredentialException) {
      return when (e) {
        is CreateCredentialCancellationException -> {
          Log.w(TAG, "Passkey creation was canceled.", e)
          PasskeyCreationResult.UserCanceled
        }
        is CreateCredentialNoCreateOptionException, is CreateCredentialProviderConfigurationException -> {
          Log.w(TAG, "No passkey provider could take the registration.", e)
          PasskeyCreationResult.NoProviderAvailable
        }
        is CreatePublicKeyCredentialDomException -> {
          Log.w(TAG, "The provider turned the registration ceremony down: ${e.domError}", e)
          PasskeyCreationResult.CeremonyFailed
        }
        else -> {
          Log.w(TAG, "Couldn't create a passkey.", e)
          PasskeyCreationResult.CeremonyFailed
        }
      }
    }

    if (response !is CreatePublicKeyCredentialResponse) {
      Log.w(TAG, "Asked for a passkey and got back a ${response.type}.")
      return PasskeyCreationResult.CeremonyFailed
    }

    return parseRegistrationResponse(response.registrationResponseJson)
  }

  /**
   * Asks the user's passkey provider to assert one of the account's credentials, returning the JSON the service
   * verifies it with.
   *
   * @param challenge The challenge the service issued for this ceremony.
   * @param allowedCredentialIds The credentials registered to the account, which is what stops the provider offering
   *   one belonging to some other account.
   */
  suspend fun getPasskeyAssertion(
    @UiContext activityContext: Context,
    rpId: String,
    challenge: ByteArray,
    timeoutSeconds: Long,
    allowedCredentialIds: List<ByteArray>
  ): PasskeyAssertionResult {
    val options = PublicKeyCredentialRequestOptions(
      challenge = Base64.encodeUrlSafeWithoutPadding(challenge),
      rpId = rpId,
      timeout = timeoutSeconds.seconds.inWholeMilliseconds,
      allowCredentials = credentialDescriptors(allowedCredentialIds)
    )

    val response = try {
      CredentialManager.create(activityContext).getCredential(
        context = activityContext,
        request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestJson = SignalJson.json.encodeToString(options))))
      )
    } catch (e: GetCredentialException) {
      return when (e) {
        is GetCredentialCancellationException -> PasskeyAssertionResult.UserCanceled
        is NoCredentialException -> {
          Log.w(TAG, "The user has no passkey for this account on this device.", e)
          PasskeyAssertionResult.NoCredentialAvailable
        }
        is GetPublicKeyCredentialDomException -> {
          Log.w(TAG, "The provider turned the assertion ceremony down: ${e.domError}", e)
          PasskeyAssertionResult.CeremonyFailed
        }
        else -> {
          Log.w(TAG, "Couldn't get a passkey assertion.", e)
          PasskeyAssertionResult.CeremonyFailed
        }
      }
    }

    val credential = response.credential
    if (credential !is PublicKeyCredential) {
      Log.w(TAG, "Asked for a passkey and got back a ${credential.type}.")
      return PasskeyAssertionResult.CeremonyFailed
    }

    return PasskeyAssertionResult.Success(credential.authenticationResponseJson)
  }

  /**
   * Pulls the attestation object and the collected client data back out of the provider's response. The client data
   * has to come back as the exact bytes that were hashed for the authenticator, so it's decoded rather than re-encoded.
   */
  private fun parseRegistrationResponse(registrationResponseJson: String): PasskeyCreationResult {
    return try {
      val response = SignalJson.json.decodeFromString<RegistrationResponseJson>(registrationResponseJson).response

      PasskeyCreationResult.Success(
        attestationObject = Base64.decode(response.attestationObject),
        collectedClientDataJson = Base64.decode(response.clientDataJson).decodeToString()
      )
    } catch (e: SerializationException) {
      Log.w(TAG, "The passkey provider returned a registration response we couldn't read.", e)
      PasskeyCreationResult.CeremonyFailed
    } catch (e: IllegalArgumentException) {
      Log.w(TAG, "The passkey provider returned a registration response we couldn't read.", e)
      PasskeyCreationResult.CeremonyFailed
    } catch (e: IOException) {
      Log.w(TAG, "The passkey provider returned a registration response we couldn't read.", e)
      PasskeyCreationResult.CeremonyFailed
    }
  }

  private fun credentialDescriptors(credentialIds: List<ByteArray>): List<PublicKeyCredentialDescriptor> {
    return credentialIds.map { PublicKeyCredentialDescriptor(id = Base64.encodeUrlSafeWithoutPadding(it)) }
  }

  private fun randomChallenge(): ByteArray = ByteArray(CHALLENGE_LENGTH_BYTES).also { random.nextBytes(it) }
}

/** The outcome of asking the user's passkey provider to register a new credential. */
sealed interface PasskeyCreationResult {
  /** The pieces of the completed ceremony that the service verifies. */
  class Success(val attestationObject: ByteArray, val collectedClientDataJson: String) : PasskeyCreationResult {
    override fun toString(): String = "Success()"
  }

  data object UserCanceled : PasskeyCreationResult

  /** The device has no passkey provider that could take the registration. */
  data object NoProviderAvailable : PasskeyCreationResult

  /** The ceremony ran but didn't produce a credential we can hand to the service. */
  data object CeremonyFailed : PasskeyCreationResult
}

/** The outcome of asking the user's passkey provider to assert an existing credential. */
sealed interface PasskeyAssertionResult {
  /** The assertion JSON, exactly as the service expects to verify it. */
  data class Success(val responseJson: String) : PasskeyAssertionResult {
    override fun toString(): String = "Success()"
  }

  data object UserCanceled : PasskeyAssertionResult

  /** The device holds none of the account's passkeys. */
  data object NoCredentialAvailable : PasskeyAssertionResult

  /** The ceremony ran but didn't produce an assertion we can hand to the service. */
  data object CeremonyFailed : PasskeyAssertionResult
}
