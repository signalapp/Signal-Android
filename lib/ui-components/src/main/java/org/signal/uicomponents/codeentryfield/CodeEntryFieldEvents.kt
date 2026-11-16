/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.codeentryfield

import org.signal.core.util.censor

/**
 * Everything a [CodeEntryFieldPresenter] can be told, whether it came from the field itself or from the screen hosting
 * it.
 *
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface CodeEntryFieldEvents {

  /** The contents of the field changed to [code]. */
  data class CodeChanged(val code: String) : CodeEntryFieldEvents {
    override fun toString(): String = "CodeChanged(code=${code.censor()})"
  }

  /** The field has applied [CodeEntryFieldState.pendingOverwrite]. */
  data object OverwriteApplied : CodeEntryFieldEvents

  /** Replaces the code in the field with [code], such as one auto-filled from an SMS. */
  data class SetCode(val code: String) : CodeEntryFieldEvents {
    override fun toString(): String = "SetCode(code=${code.censor()})"
  }

  /** Clears the field, such as after the entered code was rejected. */
  data object Clear : CodeEntryFieldEvents
}
