/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import org.signal.core.util.censor
import org.signal.registration.RegistrationFlowState

sealed class VerificationCodeScreenEvents {
  /** The parent registration flow state changed and needs to be merged into this screen's state. */
  data class ParentStateChanged(val parentState: RegistrationFlowState) : VerificationCodeScreenEvents()

  data class CodeEntered(val code: String) : VerificationCodeScreenEvents() {
    override fun toString(): String = "CodeEntered(code=${code.censor()})"
  }

  /**
   * The raw [value] of the digit field at [index] changed. The view model interprets it: a single digit is recorded
   * (submitting once the full code is present), an empty [value] is a backspace (deleting a digit and shifting the
   * following ones left), and multi-character input (e.g. a pasted "123-456" or an auto-filled SMS code) populates
   * every field at once and submits.
   */
  data class DigitChanged(val index: Int, val value: String) : VerificationCodeScreenEvents() {
    override fun toString(): String = "DigitChanged(index=$index)"
  }

  /**
   * A verification code was automatically retrieved from an incoming SMS via the Play Services SMS retriever.
   */
  data class CodeAutoFilled(val code: String) : VerificationCodeScreenEvents() {
    override fun toString(): String = "CodeAutoFilled(code=${code.censor()})"
  }

  data object ConsumeAutoFillCode : VerificationCodeScreenEvents()

  data object WrongNumber : VerificationCodeScreenEvents()

  data object ResendSms : VerificationCodeScreenEvents()

  data object CallMe : VerificationCodeScreenEvents()

  data object HavingTrouble : VerificationCodeScreenEvents()

  data object DismissContactSupport : VerificationCodeScreenEvents()

  /** The network error snackbar was shown and dismissed. */
  data object NetworkErrorSnackbarDismissed : VerificationCodeScreenEvents()

  /** The unknown error snackbar was shown and dismissed. */
  data object UnknownErrorSnackbarDismissed : VerificationCodeScreenEvents()

  /** The rate limited snackbar was shown and dismissed. */
  data object RateLimitedSnackbarDismissed : VerificationCodeScreenEvents()

  /** The unable-to-send-SMS snackbar was shown and dismissed. */
  data object UnableToSendSmsSnackbarDismissed : VerificationCodeScreenEvents()

  /** The could-not-request-code-with-selected-transport snackbar was shown and dismissed. */
  data object CouldNotRequestCodeWithSelectedTransportSnackbarDismissed : VerificationCodeScreenEvents()

  /** The incorrect verification code snackbar was shown and dismissed. */
  data object IncorrectVerificationCodeSnackbarDismissed : VerificationCodeScreenEvents()

  /** The registration error snackbar was shown and dismissed. */
  data object RegistrationErrorSnackbarDismissed : VerificationCodeScreenEvents()

  /**
   * Event to update countdown timers. Should be triggered periodically (e.g., every second).
   */
  data object CountdownTick : VerificationCodeScreenEvents()

  /**
   * The screen returned to the foreground. Used to check whether the in-progress registration data (and thus the
   * verification session) has grown too stale to keep waiting on, in which case we restart the flow.
   */
  data object Foregrounded : VerificationCodeScreenEvents()
}
