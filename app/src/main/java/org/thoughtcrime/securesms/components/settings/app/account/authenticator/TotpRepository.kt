/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import org.signal.core.models.MasterKey
import org.signal.core.util.Base32
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.MfaMetadata
import org.signal.libsignal.net.MfaNotVerifiedException
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.TooManyMfaKeysException
import org.signal.libsignal.net.TooManyTotpKeysException
import org.signal.libsignal.net.TotpParameters
import org.signal.network.api.AccountApiV2
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.util.RemoteConfig
import java.net.URLEncoder
import java.time.Instant
import org.signal.appsettings.R as AppSettingsR

/**
 * Pairing an authenticator app, sitting between the setup screens and the TOTP endpoints on [AccountApiV2].
 *
 * Everything that isn't specific to TOTP -- listing, renaming, removing -- is the same request whatever kind of second
 * factor it acts on, and lives on [TwoFactorMethodService][org.signal.network.service.TwoFactorMethodService].
 */
class TotpRepository(
  private val api: AccountApiV2 = SignalNetwork.accountApiV2,
  private val masterKeyProvider: () -> MasterKey = { SignalStore.svr.masterKey },
  private val clock: () -> Long = System::currentTimeMillis,
  private val defaultAppName: () -> String = { AppDependencies.application.getString(AppSettingsR.string.TotpRepository__authenticator) }
) {

  companion object {
    private val TAG = Log.tag(TotpRepository::class)

    private const val ISSUER = "Signal"

    /** The algorithm names the Key Uri Format defines, keyed by what [TotpParameters.algorithm] calls them. */
    private val URI_ALGORITHMS = mapOf(
      "HmacSHA1" to "SHA1",
      "HmacSHA256" to "SHA256",
      "HmacSHA512" to "SHA512"
    )

    /** How many characters of the display form go between spaces. */
    private const val DISPLAY_GROUP_SIZE = 4
  }

  /**
   * How many authenticator apps the account is allowed at once. libsignal reports hitting the limit but doesn't expose
   * the number, so the screens that want to show it get it from here.
   */
  fun getMaxApps(): Int {
    return RemoteConfig.maxTotpApps
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
   * The key is confirmed with a default name, because the service wants metadata at confirmation time and the user
   * doesn't name their app until the screen after this one. Anything that lists keys in that window shows the default
   * rather than a nameless entry.
   */
  suspend fun confirmPendingApp(code: String): ConfirmResult {
    val oneTimePassword = code.toIntOrNull() ?: return ConfirmResult.IncorrectCode

    val createdAt = clock()
    val metadata = MfaMetadata(name = defaultAppName(), createdAt = Instant.ofEpochMilli(createdAt))

    return when (val result = api.confirmTotpKey(oneTimePassword = oneTimePassword, metadata = metadata, masterKey = masterKeyProvider())) {
      is RequestResult.Success -> {
        ConfirmResult.Success(appId = result.result.toLong(), createdAt = createdAt)
      }
      is RequestResult.NonSuccess -> when (result.error) {
        is MfaNotVerifiedException -> ConfirmResult.IncorrectCode
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
    data class Success(val appId: Long, val createdAt: Long) : ConfirmResult

    data object IncorrectCode : ConfirmResult

    /** Another device filled the account up between generating the key and confirming it. */
    data object TooManyApps : ConfirmResult

    data object NetworkFailure : ConfirmResult
  }
}
