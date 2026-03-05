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
  val oneTimeEvent: OneTimeEvent? = null
) {
  sealed interface OneTimeEvent {
    data object NetworkError : OneTimeEvent
    data object UnknownError : OneTimeEvent
    data class RateLimited(val retryAfter: Duration) : OneTimeEvent
    data object ThirdPartyError : OneTimeEvent
    data object CouldNotRequestCodeWithSelectedTransport : OneTimeEvent
    data object IncorrectVerificationCode : OneTimeEvent
    data object RegistrationError : OneTimeEvent
  }

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
