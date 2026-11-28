/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.totpentry

import org.signal.uicomponents.codeentryfield.CodeEntryFieldAction
import org.signal.uicomponents.codeentryfield.CodeEntryFieldEvents
import org.signal.uicomponents.codeentryfield.CodeEntryFieldState

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed class TotpEntryScreenEvents {

  /** Received an event from the code field that we want to forward. */
  data class CodeEntryEvent(val event: CodeEntryFieldEvents) : TotpEntryScreenEvents()

  /** The code field's presenter emitted new state for us to mirror. */
  data class CodeEntryStateChanged(val codeEntryState: CodeEntryFieldState) : TotpEntryScreenEvents()

  /** The code field's presenter decided something needs doing that only this screen can do. */
  data class CodeEntryAction(val action: CodeEntryFieldAction) : TotpEntryScreenEvents()

  /** The user tapped the cancel button. */
  data object CancelClicked : TotpEntryScreenEvents()
}
