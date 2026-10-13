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
  val code: String = "",
  /** Set when the code was replaced from outside the field. The field applies it, then reports [CodeEntryFieldEvents.OverwriteApplied]. */
  val pendingOverwrite: String? = null
) {

  override fun toString(): String = "CodeEntryFieldState(digitsEntered=${code.length}, hasPendingOverwrite=${pendingOverwrite != null})"

  val isComplete: Boolean get() = code.length == CODE_LENGTH

  companion object {
    const val CODE_LENGTH = 6
  }
}
