/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import org.signal.registration.NetworkController.SessionMetadata
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
  val snackbars: Snackbars = Snackbars()
) {
  override fun toString(): String = "VerificationCodeState(sessionMetadata=${sessionMetadata?.let { "present" }}, e164=$e164, isSubmittingCode=$isSubmittingCode, rateLimits=$rateLimits, incorrectCodeAttempts=$incorrectCodeAttempts, autoFillCode=${autoFillCode?.let { "present" }}, digitsEntered=${digits.count { it.isNotEmpty() }}, focusedDigitIndex=$focusedDigitIndex, showContactSupportSheet=$showContactSupportSheet, snackbars=$snackbars)"

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

  /** Transient error messages shown as snackbars. Cleared once the snackbar has been shown and dismissed. */
  data class Snackbars(
    val networkError: Boolean = false,
    val unknownError: Boolean = false,
    val rateLimitedRetryAfter: Duration? = null,
    val unableToSendSms: Boolean = false,
    val couldNotRequestCodeWithSelectedTransport: Boolean = false,
    val incorrectVerificationCode: Boolean = false,
    val registrationError: Boolean = false
  )

  /**
   * Returns true if the user can resend SMS (timer has expired)
   */
  fun canResendSms(): Boolean = rateLimits.smsResendTimeRemaining <= 0.seconds

  /**
   * Returns true if the user can request a call (timer has expired)
   */
  fun canRequestCall(): Boolean = rateLimits.callRequestTimeRemaining <= 0.seconds

  /**
   * Returns true if the "Having Trouble" button should be shown.
   * Matches the old behavior of showing after 3 incorrect code attempts.
   */
  fun shouldShowHavingTrouble(): Boolean = incorrectCodeAttempts >= 3
}

/**
 * Rate limit data for SMS resend and phone call request countdown timers.
 */
data class SmsAndCallRateLimits(
  val smsResendTimeRemaining: Duration = 0.seconds,
  val callRequestTimeRemaining: Duration = 0.seconds
)
