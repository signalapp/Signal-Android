/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import org.signal.appsettings.totp.TotpApp
import org.signal.core.models.MasterKey
import org.signal.core.util.Base32
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.MfaKeyNotFoundException
import org.signal.libsignal.net.MfaMetadata
import org.signal.libsignal.net.OneTimePasswordNotVerifiedException
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.TooManyMfaKeysException
import org.signal.libsignal.net.TooManyTotpKeysException
import org.signal.libsignal.net.TotpParameters
import org.signal.network.api.AccountApiV2
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import java.net.URLEncoder
import java.time.Instant

/**
 * Everything the authenticator app screens need, sitting between them and the TOTP endpoints on [AccountApiV2].
 *
 * The name the user gives an app and the time they set it up live in the metadata the service stores against each
 * key. libsignal encrypts that metadata under a key derived from the master key, so the service never reads it --
 * all this layer does is hand the master key over and map the results into what the screens show.
 */
class TotpRepository(
  private val api: AccountApiV2 = SignalNetwork.accountV2,
  private val masterKeyProvider: () -> MasterKey = { SignalStore.svr.masterKey },
  private val clock: () -> Long = System::currentTimeMillis
) {

  companion object {
    private val TAG = Log.tag(TotpRepository::class)

    /**
     * How many authenticator apps an account may have, which the service enforces. libsignal reports hitting the
     * limit but doesn't expose the number, so the screens that want to show it get it from here.
     */
    const val MAX_APPS = 2

    private const val ISSUER = "Signal"

    /** The algorithm names the Key Uri Format defines, keyed by what [TotpParameters.algorithm] calls them. */
    private val URI_ALGORITHMS = mapOf(
      "HmacSHA1" to "SHA1",
      "HmacSHA256" to "SHA256",
      "HmacSHA512" to "SHA512"
    )

    /** How many characters of the display form go between spaces. */
    private const val DISPLAY_GROUP_SIZE = 4

    const val MAX_NAME_LENGTH_BYTES = MfaMetadata.NAME_MAX_LENGTH
    const val MAX_NAME_LENGTH_GRAPHEMES = 30
  }

  fun getMaxApps(): Int {
    return MAX_APPS
  }

  /**
   * Asks the service for a new key, returning what the setup screen needs to hand it to an authenticator app. The
   * service holds the pending key from here until [confirmPendingApp], so nothing is kept on this side.
   */
  suspend fun beginSetup(accountName: String): BeginSetupResult {
    return when (val result = api.generateTotpKey()) {
      is RequestResult.Success -> {
        val generated = result.result

        val setupUri = buildSetupUri(key = generated.key, parameters = generated.parameters, accountName = accountName)
        if (setupUri == null) {
          Log.w(TAG, "The service generated a key with parameters a setup link can't describe: ${generated.parameters}")
          return BeginSetupResult.NetworkFailure
        }

        BeginSetupResult.Success(
          setupUri = setupUri,
          displayKey = Base32.encode(generated.key).chunked(DISPLAY_GROUP_SIZE).joinToString(" "),
          clipboardKey = Base32.encode(generated.key)
        )
      }
      is RequestResult.NonSuccess -> {
        when (result.error) {
          is TooManyTotpKeysException, is TooManyMfaKeysException -> BeginSetupResult.TooManyApps
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "Couldn't generate a key.", result.networkError)
        BeginSetupResult.NetworkFailure
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Couldn't generate a key.", result.cause)
        BeginSetupResult.NetworkFailure
      }
    }
  }

  /**
   * Confirms the pending key with a code from the user's authenticator app.
   *
   * The key is confirmed without a name, because the service wants metadata at confirmation time and the user doesn't
   * name their app until the screen after this one. Naming it later means a brief window where a key has no name, which
   * is a better failure than a window where the second factor isn't active yet.
   */
  suspend fun confirmPendingApp(code: String): ConfirmResult {
    val oneTimePassword = code.toIntOrNull() ?: return ConfirmResult.IncorrectCode

    val metadata = MfaMetadata(name = "", createdAt = Instant.ofEpochMilli(clock()))

    return when (val result = api.confirmTotpKey(oneTimePassword = oneTimePassword, metadata = metadata, masterKey = masterKeyProvider())) {
      is RequestResult.Success -> {
        ConfirmResult.Success(appId = result.result.toLong())
      }
      is RequestResult.NonSuccess -> when (result.error) {
        is OneTimePasswordNotVerifiedException -> ConfirmResult.IncorrectCode
        is TooManyMfaKeysException -> {
          Log.w(TAG, "The account filled up with keys between generating this one and confirming it.")
          ConfirmResult.TooManyApps
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "Couldn't confirm the pending key.", result.networkError)
        ConfirmResult.NetworkFailure
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Couldn't confirm the pending key.", result.cause)
        ConfirmResult.NetworkFailure
      }
    }
  }

  /** The authenticator apps on the account, newest id last, with anything we can't read left out. */
  suspend fun getTotpApps(): AppsResult {
    val keys = when (val result = api.listMfaKeys(masterKeyProvider())) {
      is RequestResult.Success -> result.result
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "Couldn't list keys.", result.networkError)
        return AppsResult.NetworkFailure
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Couldn't list keys.", result.cause)
        return AppsResult.NetworkFailure
      }
      is RequestResult.NonSuccess -> error("Code branch is unreachable")
    }

    val apps = keys.mapNotNull { key ->
      val metadata = key.metadata
      if (metadata == null) {
        Log.w(TAG, "Couldn't read the metadata for key ${key.id}. Leaving it out of the list.")
        null
      } else {
        TotpApp(
          id = key.id.toLong(),
          name = metadata.name,
          createdAt = metadata.createdAt.toEpochMilli()
        )
      }
    }

    return AppsResult.Success(apps)
  }

  /** Renames [app], which means re-encrypting its metadata and handing the whole blob back to the service. */
  suspend fun renameTotpApp(app: TotpApp, name: String): UpdateResult {
    return setMetadata(app.id, MfaMetadata(name = name, createdAt = Instant.ofEpochMilli(app.createdAt)))
  }

  /** Names a newly confirmed app, which was confirmed without one moments ago. */
  suspend fun nameNewTotpApp(appId: Long, name: String): UpdateResult {
    return setMetadata(appId, MfaMetadata(name = name, createdAt = Instant.ofEpochMilli(clock())))
  }

  suspend fun removeTotpApp(appId: Long): UpdateResult {
    return when (val result = api.removeMfaKey(appId.toInt())) {
      is RequestResult.Success -> UpdateResult.Success
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "Couldn't remove the key.", result.networkError)
        UpdateResult.NetworkFailure
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Couldn't remove the key.", result.cause)
        UpdateResult.NetworkFailure
      }
      is RequestResult.NonSuccess -> error("Code branch is unreachable")
    }
  }

  private suspend fun setMetadata(appId: Long, metadata: MfaMetadata): UpdateResult {
    return when (val result = api.setMfaKeyMetadata(keyId = appId.toInt(), metadata = metadata, masterKey = masterKeyProvider())) {
      is RequestResult.Success -> UpdateResult.Success
      is RequestResult.NonSuccess -> when (result.error) {
        is MfaKeyNotFoundException -> UpdateResult.AppNotFound
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "Couldn't set key metadata.", result.networkError)
        UpdateResult.NetworkFailure
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Couldn't set key metadata.", result.cause)
        UpdateResult.NetworkFailure
      }
    }
  }

  /**
   * The `otpauth://` URI that hands the key to an authenticator app, following the de facto Key Uri Format every app
   * implements, or null for parameters the format can't describe. Note that a lot of apps ignore params like
   * "algorithm", but we set them just in case.
   */
  private fun buildSetupUri(key: ByteArray, parameters: TotpParameters, accountName: String): String? {
    val algorithm = URI_ALGORITHMS[parameters.algorithm] ?: return null

    val label = if (accountName.isBlank()) encode(ISSUER) else "${encode(ISSUER)}:${encode(accountName)}"

    val query = listOf(
      "secret" to Base32.encode(key),
      "issuer" to ISSUER,
      "algorithm" to algorithm,
      "digits" to parameters.passwordLength.toString(),
      "period" to parameters.timeStep.seconds.toString()
    ).joinToString("&") { (name, value) -> "$name=${encode(value)}" }

    return "otpauth://totp/$label?$query"
  }

  /**
   * [URLEncoder] targets form encoding rather than URIs, so it renders a space as `+` where a URI needs `%20`, and
   * escapes `~` where a URI leaves it alone.
   */
  private fun encode(value: String): String {
    return URLEncoder.encode(value, Charsets.UTF_8.name())
      .replace("+", "%20")
      .replace("%7E", "~")
  }

  sealed interface BeginSetupResult {
    data class Success(val setupUri: String, val displayKey: String, val clipboardKey: String) : BeginSetupResult {
      override fun toString(): String = "Success()"
    }

    /** The account already has as many authenticator apps as it's allowed. */
    data object TooManyApps : BeginSetupResult

    data object NetworkFailure : BeginSetupResult
  }

  sealed interface ConfirmResult {
    data class Success(val appId: Long) : ConfirmResult

    data object IncorrectCode : ConfirmResult

    /** Another device filled the account up between generating the key and confirming it. */
    data object TooManyApps : ConfirmResult

    data object NetworkFailure : ConfirmResult
  }

  sealed interface AppsResult {
    data class Success(val apps: List<TotpApp>) : AppsResult

    data object NetworkFailure : AppsResult
  }

  sealed interface UpdateResult {
    data object Success : UpdateResult

    data object AppNotFound : UpdateResult

    data object NetworkFailure : UpdateResult
  }
}
