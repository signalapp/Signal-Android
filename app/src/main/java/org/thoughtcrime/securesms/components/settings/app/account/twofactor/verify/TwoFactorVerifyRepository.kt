/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.twofactor.verify

import org.signal.appsettings.twofactorverify.PasskeyAssertionParameters
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.MfaNotVerifiedException
import org.signal.libsignal.net.MfaVerificationCredential
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.AccountApiV2
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.megaphone.TwoFactorReminderSchedule
import org.thoughtcrime.securesms.net.SignalNetwork

/**
 * The network and storage behind [TwoFactorVerifyViewModel]: the service's two-phase verification check, plus the
 * bookkeeping that decides when the reminder comes back.
 */
class TwoFactorVerifyRepository(
  private val api: AccountApiV2 = SignalNetwork.accountApiV2,
  private val relyingPartyId: String = BuildConfig.WEBAUTHN_RP_ID
) {

  companion object {
    private val TAG = Log.tag(TwoFactorVerifyRepository::class)
  }

  /**
   * Asks the service which kinds of method the account has. The passkey challenge this returns is only good for a
   * little while, so call again right before running a ceremony rather than holding onto an old one.
   */
  suspend fun startVerification(): StartResult {
    return when (val result = api.startMfaVerification()) {
      is RequestResult.Success -> {
        val response = result.result

        StartResult.Success(
          hasAuthenticatorApp = response.hasTotp,
          passkey = response.webauthnParams?.let { params ->
            PasskeyAssertionParameters(
              relyingPartyId = relyingPartyId,
              challenge = params.challenge,
              timeoutSeconds = params.timeoutSeconds.toLong(),
              allowedCredentialIds = params.allowedCredentialIds
            )
          }
        )
      }
      is RequestResult.NonSuccess -> {
        error("Code branch is unreachable")
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "Couldn't start the verification check.", result.networkError)
        StartResult.NetworkFailure
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Couldn't start the verification check.", result.cause)
        StartResult.NetworkFailure
      }
    }
  }

  /** Checks a code from the user's authenticator app against the account's keys. */
  suspend fun verifyCode(code: String): VerifyResult {
    val oneTimePassword = code.toIntOrNull() ?: return VerifyResult.NotVerified

    return finishVerification(MfaVerificationCredential.Totp(oneTimePassword))
  }

  /** Checks a completed passkey assertion against the account's keys. */
  suspend fun verifyPasskey(responseJson: String): VerifyResult {
    return finishVerification(MfaVerificationCredential.WebAuthn(responseJson))
  }

  /** Records that the user proved they still hold a method, which pushes the next reminder out. */
  fun markVerified() {
    TwoFactorReminderSchedule.onVerified()
  }

  /** Records that the account turned out to have no methods after all, so the reminder stops showing. */
  fun markNoMethods() {
    TwoFactorReminderSchedule.onMethodsObserved(false)
  }

  private suspend fun finishVerification(credential: MfaVerificationCredential): VerifyResult {
    return when (val result = api.finishMfaVerification(credential)) {
      is RequestResult.Success -> {
        VerifyResult.Success
      }
      is RequestResult.NonSuccess -> {
        when (result.error) {
          is MfaNotVerifiedException -> {
            VerifyResult.NotVerified
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "Couldn't finish the verification check.", result.networkError)
        VerifyResult.NetworkFailure
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Couldn't finish the verification check.", result.cause)
        VerifyResult.NetworkFailure
      }
    }
  }

  sealed interface StartResult {
    /** Which methods the account has. Both can be absent when it has none this build understands. */
    data class Success(val hasAuthenticatorApp: Boolean, val passkey: PasskeyAssertionParameters?) : StartResult

    data object NetworkFailure : StartResult
  }

  sealed interface VerifyResult {
    data object Success : VerifyResult

    /** The service couldn't verify the credential, whether a wrong code, an expired challenge, or a bad assertion. */
    data object NotVerified : VerifyResult

    data object NetworkFailure : VerifyResult
  }
}
