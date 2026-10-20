/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.twofactorverify

import org.signal.uicomponents.codeentryfield.CodeEntryFieldState

/**
 * Everything [TwoFactorVerifyScreen] needs to render.
 */
data class TwoFactorVerifyState(
  val step: Step = Step.Loading,
  val codeEntry: CodeEntryFieldState = CodeEntryFieldState(),
  /** Whether a code is being checked with the service, during which the field is locked. */
  val submitting: Boolean = false,
  /** Why the last code didn't work, shown under the field and cleared as soon as the user types. */
  val codeError: CodeError = CodeError.None,
  val dialog: Dialog = Dialog.None
) {

  sealed interface Step {
    /** Asking the service which methods the account has. */
    data object Loading : Step

    /**
     * Letting the user pick a method. A passkey is always on offer here, since an account with only authenticator
     * apps skips straight to [EnterCode].
     */
    data class ChooseMethod(val hasAuthenticatorApp: Boolean) : Step

    /** Collecting a code from the user's authenticator app. */
    data class EnterCode(
      /** Whether the user got here from [ChooseMethod], and so has somewhere to go back to. */
      val canReturnToMethodChoice: Boolean
    ) : Step
  }

  sealed interface CodeError {
    data object None : CodeError

    /** The service rejected the code, and we have no reason to think it was anything but a wrong code. */
    data object IncorrectCode : CodeError

    data object NetworkFailure : CodeError
  }

  sealed interface Dialog {
    data object None : Dialog

    /** A passkey ceremony is running, or its result is being checked with the service. */
    data object PasskeyInProgress : Dialog

    /** The passkey ceremony produced nothing the service would accept. */
    data object PasskeyFailed : Dialog

    /** The service couldn't be reached to find out which methods the account has, so there's nothing to show. */
    data object NetworkFailure : Dialog
  }
}
