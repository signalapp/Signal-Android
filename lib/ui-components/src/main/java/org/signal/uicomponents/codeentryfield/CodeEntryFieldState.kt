/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.codeentryfield

/**
 * State of a [CodeEntryField]. Owned by a [CodeEntryFieldPresenter] and expected to be mirrored into the state of
 * whatever screen the field sits in.
 *
 * Reminder that this is logged, so don't put the code itself in the toString.
 */
data class CodeEntryFieldState(
  val digits: List<String> = emptyDigits(),
  val focusedDigitIndex: Int = 0
) {

  override fun toString(): String = "CodeEntryFieldState(digitsEntered=${digits.count { it.isNotEmpty() }}, focusedDigitIndex=$focusedDigitIndex)"

  /**
   * The full code as currently entered. Only meaningful when [isComplete] is true.
   */
  val code: String get() = digits.joinToString("")

  val isComplete: Boolean get() = digits.size == CODE_LENGTH && digits.all { it.isNotEmpty() }

  companion object {
    const val CODE_LENGTH = 6

    fun emptyDigits(): List<String> = List(CODE_LENGTH) { "" }
  }
}
