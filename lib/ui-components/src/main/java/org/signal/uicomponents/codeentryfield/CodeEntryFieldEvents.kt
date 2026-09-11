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

  /**
   * The raw [value] of the digit field at [index] changed.
   */
  data class DigitChanged(val index: Int, val value: String) : CodeEntryFieldEvents {
    override fun toString(): String = "DigitChanged(index=$index, value=${value.censor()})"
  }
}
