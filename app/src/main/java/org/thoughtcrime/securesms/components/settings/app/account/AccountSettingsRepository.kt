/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import kotlinx.coroutines.withContext
import org.signal.appsettings.account.PasskeyCreationParameters
import org.signal.core.models.MasterKey
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.MfaMetadata
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.TooManyMfaKeysException
import org.signal.libsignal.net.WebAuthnRegistrationUnsuccessfulException
import org.signal.network.api.AccountApiV2
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.pin.SvrRepository
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.kbs.PinHashUtil
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/**
 * All of the storage and network access behind [AccountSettingsViewModel].
 *
 * @param defaultPasskeyName What a new passkey is filed under until the user names it on the screen after the ceremony.
 */
class AccountSettingsRepository(
  private val defaultPasskeyName: String,
  private val api: AccountApiV2 = SignalNetwork.accountApiV2,
  private val masterKeyProvider: () -> MasterKey = { SignalStore.svr.masterKey },
  private val clock: () -> Long = System::currentTimeMillis,
  private val zoneId: () -> ZoneId = ZoneId::systemDefault,
  private val relyingPartyId: String = BuildConfig.WEBAUTHN_RP_ID
) {

  companion object {
    private val TAG = Log.tag(AccountSettingsRepository::class)

    /** What the passkey provider files the credential under, which is the same for everyone using this app. */
    private const val RELYING_PARTY_NAME = "Signal"
  }

  fun hasPin(): Boolean = SignalStore.svr.hasPin() && !SignalStore.svr.hasOptedOut()

  fun hasRestoredAep(): Boolean = SignalStore.account.restoredAccountEntropyPool

  fun arePinRemindersEnabled(): Boolean = SignalStore.pin.arePinRemindersEnabled() && SignalStore.svr.hasPin()

  fun setPinRemindersEnabled(enabled: Boolean) = SignalStore.pin.setPinRemindersEnabled(enabled)

  fun isRegistrationLockEnabled(): Boolean = SignalStore.svr.isRegistrationLockEnabled

  fun isUserUnregistered(): Boolean = SignalStore.account.isUnauthorizedReceived

  fun isClientDeprecated(): Boolean = SignalStore.misc.isClientDeprecated

  fun getPinKeyboardType(): PinKeyboardType = SignalStore.pin.keyboardType

  fun isPhoneNumberless(): Boolean = SignalStore.account.isPhoneNumberless

  fun getMaxTotpApps(): Int = RemoteConfig.maxTotpApps

  /**
   * How many two-factor methods of every kind the account is allowed at once. Authenticator apps share this limit with
   * passkeys, so it can be reached even when there's room left under [getMaxTotpApps].
   */
  fun getMaxTwoFactorMethods(): Int {
    return RemoteConfig.maxTwoFactorMethods
  }

  fun verifyLocalPin(pin: String): Boolean {
    val localPinHash = SignalStore.svr.localPinHash
    if (localPinHash == null) {
      Log.w(TAG, "No local PIN hash to verify against!")
      return false
    }

    return PinHashUtil.verifyLocalPinHash(localPinHash, pin)
  }

  /**
   * Turns registration lock on or off on the service, returning whether it worked.
   */
  suspend fun setRegistrationLockEnabled(enabled: Boolean): Boolean = withContext(SignalDispatchers.IO) {
    try {
      if (enabled) {
        SvrRepository.enableRegistrationLockForUserWithPin()
      } else {
        SvrRepository.disableRegistrationLockForUserWithPin()
      }
      true
    } catch (e: IOException) {
      Log.w(TAG, "Failed to ${if (enabled) "enable" else "disable"} registration lock.", e)
      false
    }
  }

  fun masterKey(): MasterKey {
    return masterKeyProvider()
  }

  /**
   * Asks the service for what a passkey provider needs to create a credential for this account, which is the first leg
   * of the ceremony [SignalPasskeyManager][org.signal.passwordmanager.SignalPasskeyManager] describes.
   */
  suspend fun startPasskeyRegistration(): StartPasskeyRegistrationResult {
    return when (val result = api.startWebAuthnRegistration()) {
      is RequestResult.Success -> {
        val parameters = result.result

        StartPasskeyRegistrationResult.Success(
          PasskeyCreationParameters(
            relyingPartyId = relyingPartyId,
            relyingPartyName = RELYING_PARTY_NAME,
            userHandle = parameters.userHandle,
            userName = credentialLabel(),
            allowedAlgorithms = parameters.allowedAlgorithms,
            excludeCredentialIds = parameters.excludeCredentialIds
          )
        )
      }
      is RequestResult.NonSuccess -> when (result.error) {
        is TooManyMfaKeysException -> StartPasskeyRegistrationResult.TooManyMethods
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "Couldn't start the registration ceremony.", result.networkError)
        StartPasskeyRegistrationResult.NetworkFailure
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Couldn't start the registration ceremony.", result.cause)
        StartPasskeyRegistrationResult.NetworkFailure
      }
    }
  }

  /**
   * Hands the completed ceremony to the service, which adds the passkey and assigns it an id.
   *
   * The passkey is registered under [defaultPasskeyName], because the service wants metadata at registration time and
   * the user doesn't name it until the screen after this one.
   */
  suspend fun finishPasskeyRegistration(attestationObject: ByteArray, collectedClientDataJson: String): FinishPasskeyRegistrationResult {
    val createdAt = clock()

    val result = api.finishWebAuthnRegistration(
      attestationObject = attestationObject,
      collectedClientDataJson = collectedClientDataJson,
      metadata = MfaMetadata(name = defaultPasskeyName, createdAt = Instant.ofEpochMilli(createdAt)),
      masterKey = masterKeyProvider()
    )

    return when (result) {
      is RequestResult.Success -> FinishPasskeyRegistrationResult.Success(passkeyId = result.result.toLong(), createdAt = createdAt)
      is RequestResult.NonSuccess -> when (val e = result.error) {
        is TooManyMfaKeysException -> {
          Log.w(TAG, "The account filled up with methods while the ceremony was running.", e)
          FinishPasskeyRegistrationResult.TooManyMethods
        }
        is WebAuthnRegistrationUnsuccessfulException -> {
          Log.w(TAG, "The service would not accept the ceremony's response.", e)
          FinishPasskeyRegistrationResult.CeremonyRejected
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "Couldn't finish the registration ceremony.", result.networkError)
        FinishPasskeyRegistrationResult.NetworkFailure
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Couldn't finish the registration ceremony.", result.cause)
        FinishPasskeyRegistrationResult.NetworkFailure
      }
    }
  }

  /** Mirrors the authenticator app flow in labeling the credential with a date rather than anything identifying. */
  private fun credentialLabel(): String {
    return Instant.ofEpochMilli(clock()).atZone(zoneId()).toLocalDate().toString()
  }

  sealed interface StartPasskeyRegistrationResult {
    data class Success(val parameters: PasskeyCreationParameters) : StartPasskeyRegistrationResult

    /** The account already has as many second factors of all kinds as it's allowed. */
    data object TooManyMethods : StartPasskeyRegistrationResult

    /** Transient, retryable network failure. */
    data object NetworkFailure : StartPasskeyRegistrationResult
  }

  sealed interface FinishPasskeyRegistrationResult {
    /** The passkey is on the account under a default name, waiting for the user to give it a real one. */
    data class Success(val passkeyId: Long, val createdAt: Long) : FinishPasskeyRegistrationResult

    /** Another device filled the account up while the ceremony was running. */
    data object TooManyMethods : FinishPasskeyRegistrationResult

    /** The service could not verify what the authenticator produced. */
    data object CeremonyRejected : FinishPasskeyRegistrationResult

    /** Transient, retryable network failure. */
    data object NetworkFailure : FinishPasskeyRegistrationResult
  }
}
