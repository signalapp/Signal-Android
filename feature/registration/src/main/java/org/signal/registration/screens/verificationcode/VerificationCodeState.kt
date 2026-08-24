/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import org.signal.network.api.RegistrationApiV2.SessionMetadata
import org.signal.network.api.RegistrationApiV2.VerificationCodeTransport
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class VerificationCodeState(
  val sessionMetadata: SessionMetadata? = null,
  val e164: String = "",
  val isSubmittingCode: Boolean = false,
  val rateLimits: SmsAndCallRateLimits = SmsAndCallRateLimits(),
  val incorrectCodeAttempts: Int = 0,
  val autoFillCode: String? = null,
  val digits: List<String> = List(CODE_LENGTH) { "" },
  val focusedDigitIndex: Int = 0,
  val showContactSupportSheet: Boolean = false,
  val showContactSupportDialog: Boolean = false,
  val snackbars: Snackbars = Snackbars(),
  val dialogs: Dialogs = Dialogs()
) {
  override fun toString(): String = "VerificationCodeState(sessionMetadata=$sessionMetadata, e164=$e164, isSubmittingCode=$isSubmittingCode, rateLimits=$rateLimits, incorrectCodeAttempts=$incorrectCodeAttempts, autoFillCode=${autoFillCode?.let { "present" }}, digitsEntered=${digits.count { it.isNotEmpty() }}, focusedDigitIndex=$focusedDigitIndex, showContactSupportSheet=$showContactSupportSheet,  showContactSupportDialog=$showContactSupportDialog, snackbars=$snackbars, dialogs=$dialogs)"

  /**
   * The full code as currently entered. Only meaningful when [isComplete] is true.
   */
  val code: String get() = digits.joinToString("")

  /**
   * True once every digit field has a value.
   */
  val isComplete: Boolean get() = digits.size == CODE_LENGTH && digits.all { it.isNotEmpty() }

  companion object {
    const val CODE_LENGTH = 6

    /**
     * A fully empty set of digits, used to reset the fields.
     */
    fun emptyDigits(): List<String> = List(CODE_LENGTH) { "" }
  }

  /** Transient errors from submitting a code or registering, shown as snackbars. Cleared once shown and dismissed. */
  data class Snackbars(
    val networkError: Boolean = false,
    val unknownError: Boolean = false,
    val rateLimitedRetryAfter: Duration? = null,
    val incorrectVerificationCode: Boolean = false,
    val registrationError: Boolean = false
  )

  /** Errors from requesting a verification code (resend SMS / call me), shown as modal dialogs so they aren't missed. */
  data class Dialogs(
    val networkError: Boolean = false,
    val unknownError: Boolean = false,
    val rateLimitedRetryAfter: Duration? = null,
    val unableToSendSms: Boolean = false,
    val couldNotRequestCodeWithSelectedTransport: Boolean = false,
    /** Nonnull when the delivery provider rejected the request. Carries the failed transport for accurate wording. */
    val providerRejectedTransport: VerificationCodeTransport? = null
  )

  /**
   * Returns true if the user can resend SMS right now (a timer exists and has expired). False while a timer is still
   * counting down, and also when SMS resend is unavailable ([SmsAndCallRateLimits.smsResendTimeRemaining] is null).
   */
  fun canResendSms(): Boolean = rateLimits.smsResendTimeRemaining.let { it != null && it <= 0.seconds }

  /**
   * Returns true if the user can request a call right now (a timer exists and has expired). False while a timer is
   * still counting down, and also when calls are unavailable ([SmsAndCallRateLimits.callRequestTimeRemaining] is null).
   */
  fun canRequestCall(): Boolean = rateLimits.callRequestTimeRemaining.let { it != null && it <= 0.seconds }

  /**
   * The SMS resend cooldown to display as a countdown, or null when there is nothing to count down — either because
   * resend is available now or because it is unavailable.
   */
  fun smsResendCountdown(): Duration? = rateLimits.smsResendTimeRemaining?.takeIf { it > 0.seconds }

  /**
   * The call request cooldown to display as a countdown, or null when there is nothing to count down — either because
   * a call can be requested now or because it is unavailable.
   */
  fun callRequestCountdown(): Duration? = rateLimits.callRequestTimeRemaining?.takeIf { it > 0.seconds }

  /**
   * Returns true if the "Having Trouble" button should be shown.
   * Matches the old behavior of showing after 3 incorrect code attempts.
   */
  fun shouldShowHavingTrouble(): Boolean = incorrectCodeAttempts >= 3
}

/**
 * Rate limit data for SMS resend and phone call request countdown timers.
 *
 * For each transport: `null` means the server won't allow that request at all (its button is disabled with no
 * countdown), [kotlin.time.Duration.ZERO] means it can be requested now, and a positive value is the countdown until
 * it becomes available.
 */
data class SmsAndCallRateLimits(
  val smsResendTimeRemaining: Duration? = 0.seconds,
  val callRequestTimeRemaining: Duration? = 0.seconds
)
