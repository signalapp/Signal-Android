/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import org.signal.core.util.censor
import org.signal.registration.RegistrationFlowState
import org.signal.uicomponents.codeentryfield.CodeEntryFieldEvents
import org.signal.uicomponents.codeentryfield.CodeEntryFieldState

sealed class VerificationCodeScreenEvents {
  /** The parent registration flow state changed and needs to be merged into this screen's state. */
  data class ParentStateChanged(val parentState: RegistrationFlowState) : VerificationCodeScreenEvents()

  data class CodeEntered(val code: String) : VerificationCodeScreenEvents() {
    override fun toString(): String = "CodeEntered(code=${code.censor()})"
  }

  /** An event for the code entry field, forwarded to its presenter. */
  data class CodeEntryEvent(val event: CodeEntryFieldEvents) : VerificationCodeScreenEvents()

  /** The code entry field's presenter has a new state to mirror into this screen's state. */
  data class CodeEntryStateChanged(val codeEntryState: CodeEntryFieldState) : VerificationCodeScreenEvents()

  /**
   * A verification code was automatically retrieved from an incoming SMS via the Play Services SMS retriever.
   */
  data class CodeAutoFilled(val code: String) : VerificationCodeScreenEvents() {
    override fun toString(): String = "CodeAutoFilled(code=${code.censor()})"
  }

  data object WrongNumber : VerificationCodeScreenEvents()

  data object ResendSms : VerificationCodeScreenEvents()

  data object CallMe : VerificationCodeScreenEvents()

  data object HavingTrouble : VerificationCodeScreenEvents()

  data object DismissContactSupport : VerificationCodeScreenEvents()

  data object ContactSupportDialog : VerificationCodeScreenEvents()

  data object DismissContactSupportDialog : VerificationCodeScreenEvents()

  /** The network error snackbar was shown and dismissed. */
  data object NetworkErrorSnackbarDismissed : VerificationCodeScreenEvents()

  /** The unknown error snackbar was shown and dismissed. */
  data object UnknownErrorSnackbarDismissed : VerificationCodeScreenEvents()

  /** The rate limited snackbar was shown and dismissed. */
  data object RateLimitedSnackbarDismissed : VerificationCodeScreenEvents()

  /** The network error dialog from requesting a code was dismissed. */
  data object NetworkErrorDialogDismissed : VerificationCodeScreenEvents()

  /** The unknown error dialog from requesting a code was dismissed. */
  data object UnknownErrorDialogDismissed : VerificationCodeScreenEvents()

  /** The rate limited dialog from requesting a code was dismissed. */
  data object RateLimitedDialogDismissed : VerificationCodeScreenEvents()

  /** The unable-to-send-SMS dialog was dismissed. */
  data object UnableToSendSmsDialogDismissed : VerificationCodeScreenEvents()

  /** The could-not-request-code-with-selected-transport dialog was dismissed. */
  data object CouldNotRequestCodeWithSelectedTransportDialogDismissed : VerificationCodeScreenEvents()

  /** The delivery-provider-rejected dialog was dismissed. */
  data object ProviderRejectedDialogDismissed : VerificationCodeScreenEvents()

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
