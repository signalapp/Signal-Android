/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.twofactorverify

import org.signal.passwordmanager.PasskeyAssertionResult
import org.signal.uicomponents.codeentryfield.CodeEntryFieldAction
import org.signal.uicomponents.codeentryfield.CodeEntryFieldEvents
import org.signal.uicomponents.codeentryfield.CodeEntryFieldState

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface TwoFactorVerifyEvent {

  /** The user tapped the passkey card. */
  data object PasskeySelected : TwoFactorVerifyEvent

  /** The user tapped the authenticator app card. */
  data object AuthenticatorAppSelected : TwoFactorVerifyEvent

  /** Received an event from the code field that we want to forward. */
  data class CodeEntryEvent(val event: CodeEntryFieldEvents) : TwoFactorVerifyEvent

  /** The code field's presenter emitted new state for us to mirror. */
  data class CodeEntryStateChanged(val codeEntryState: CodeEntryFieldState) : TwoFactorVerifyEvent

  /** The code field's presenter emitted an action for us to carry out. */
  data class CodeEntryAction(val action: CodeEntryFieldAction) : TwoFactorVerifyEvent

  /** The host ran the passkey ceremony and this is how it went. */
  data class PasskeyCeremonyCompleted(val result: PasskeyAssertionResult) : TwoFactorVerifyEvent

  /** The user tapped the cancel button. */
  data object CancelClicked : TwoFactorVerifyEvent

  /** The user pressed back. */
  data object BackClicked : TwoFactorVerifyEvent

  /** The user dismissed whatever dialog was showing. */
  data object DialogDismissed : TwoFactorVerifyEvent
}
