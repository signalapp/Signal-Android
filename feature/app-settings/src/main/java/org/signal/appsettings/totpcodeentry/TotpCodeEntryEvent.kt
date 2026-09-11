/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.totpcodeentry

import org.signal.uicomponents.codeentryfield.CodeEntryFieldEvents
import org.signal.uicomponents.codeentryfield.CodeEntryFieldState

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface TotpCodeEntryEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : TotpCodeEntryEvent

  /** Received an event from the code field that we want to forward. */
  data class CodeEntryEvent(val event: CodeEntryFieldEvents) : TotpCodeEntryEvent

  /** The code field's presenter emitted new state for us to mirror. */
  data class CodeEntryStateChanged(val codeEntryState: CodeEntryFieldState) : TotpCodeEntryEvent

  /** The user submitted the code they entered. */
  data object NextClicked : TotpCodeEntryEvent
}
