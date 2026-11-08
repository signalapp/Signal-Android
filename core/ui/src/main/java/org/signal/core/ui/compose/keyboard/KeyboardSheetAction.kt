/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.keyboard

/**
 * Side effects emitted by a [KeyboardSheetScaffold] that need to be handled by the user of the
 * component: which of its keyboards is up, and what the system keyboard is doing.
 */
sealed interface KeyboardSheetAction {
  data class KeyboardShown(val key: KeyboardSheetKey) : KeyboardSheetAction

  /** None of ours is showing any more. */
  data object KeyboardHidden : KeyboardSheetAction

  /** One of ours was dismissed by a back gesture rather than by a request. */
  data object DismissedByBack : KeyboardSheetAction

  /** @param visible Whether the system keyboard is up, on the target state rather than the animated one. */
  data class SystemKeyboardVisibilityChanged(val visible: Boolean) : KeyboardSheetAction

  /** The system keyboard finished animating, in either direction. */
  data object SystemKeyboardAnimationEnded : KeyboardSheetAction

  /**
   * A trustworthy system keyboard height was observed. The scaffold does not persist it.
   *
   * @param heightPx The measured height.
   */
  data class SystemKeyboardHeightMeasured(val heightPx: Int) : KeyboardSheetAction
}
