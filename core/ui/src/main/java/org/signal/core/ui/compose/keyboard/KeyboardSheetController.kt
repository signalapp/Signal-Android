/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Asks a [KeyboardSheetScaffold] for a keyboard.
 *
 * Stable and safe to hold outside composition, so view code can call into it. Everything flowing the
 * other way is a [KeyboardSheetAction].
 *
 * @param initialKeyboardHeightPx Height to use before one has been measured, typically persisted
 *   from an earlier run.
 */
@Stable
class KeyboardSheetController(initialKeyboardHeightPx: Int = 0) {

  /** The keyboard currently up, or null when none of ours is. */
  var current: KeyboardSheetKey? by mutableStateOf(null)
    private set

  /** Whether the system keyboard is up, from the target of the IME inset animation. */
  var isSystemKeyboardVisible: Boolean by mutableStateOf(false)
    internal set

  /**
   * True while the system keyboard has been asked for in place of one of ours but has yet to settle.
   * Holds the space across that gap, which the IME service round trip would otherwise leave empty.
   */
  var awaitingSystemKeyboard: Boolean by mutableStateOf(false)
    internal set

  /** How tall a keyboard of ours should be, replaced whenever a system keyboard is measured. */
  var keyboardHeightPx: Int by mutableStateOf(initialKeyboardHeightPx)
    internal set

  /**
   * Whether an expandable keyboard is, or is settling to, full height. Always false for keyboards
   * that were not declared expandable.
   */
  var isExpanded: Boolean by mutableStateOf(false)
    internal set

  /**
   * Whether a window of the host's own -- a dialog with a field of its own, say -- is up in front of
   * the sheet and owns the system keyboard while it is there, so the sheet stays put rather than
   * giving way to it. Driven by [onHostWindowShown] and [onHostWindowHidden].
   */
  var isBehindHostWindow: Boolean by mutableStateOf(false)
    private set

  private val _isEnteringText = MutableStateFlow(false)

  /**
   * Whether the keyboard that is up has taken text entry over, for content with a field of its own
   * such as a search bar. Text entry cannot outlive the keyboard it is for, and only the scaffold is
   * in a position to end it -- a drag, a collapse or a dismissal all take the room away -- so it
   * lives here rather than in the content that asked for it.
   */
  val isEnteringText: StateFlow<Boolean> = _isEnteringText.asStateFlow()

  /** Where [expand] and [collapse] want the sheet; the scaffold drives it there. */
  internal var expansionTarget: Boolean by mutableStateOf(false)

  val isShowing: Boolean get() = current != null

  fun show(key: KeyboardSheetKey) {
    reset(key)
  }

  fun hide() {
    reset(null)
  }

  /** Grows an expandable keyboard to full height. A no-op for any other keyboard. */
  fun expand() {
    expansionTarget = true
  }

  /**
   * Returns an expanded keyboard to keyboard height, which also gives up text entry: a field has
   * nowhere to go once the sheet is no taller than the system keyboard in front of it.
   */
  fun collapse() {
    _isEnteringText.value = false
    expansionTarget = false
  }

  /**
   * Takes text entry over on behalf of the keyboard that is up, growing the sheet to leave room for
   * the system keyboard. Content calls this rather than asking for height, so it cannot end up
   * holding a field with nowhere to put it.
   *
   * Only meaningful for a keyboard declared expandable, and a no-op when none of ours is up.
   */
  fun beginTextEntry() {
    if (current == null) {
      return
    }

    _isEnteringText.value = true
    expansionTarget = true
  }

  /** Gives text entry up, taking the system keyboard with it and returning to keyboard height. */
  fun endTextEntry() {
    collapse()
  }

  /** The host has put a window of its own up in front of the sheet. See [isBehindHostWindow]. */
  fun onHostWindowShown() {
    isBehindHostWindow = true
  }

  /** That window is gone, so the system keyboard is the sheet's business again. */
  fun onHostWindowHidden() {
    isBehindHostWindow = false
  }

  /** Closes [key] if it is already up, otherwise swaps to it. */
  fun toggle(key: KeyboardSheetKey) {
    if (current == key) hide() else show(key)
  }

  /**
   * Puts ours away because the system keyboard is being brought up instead. Only holds the space if
   * one of ours was up; with nothing to hand over, content should just follow the keyboard in.
   *
   * Also the one way out of [isEnteringText] that keeps the system keyboard: the host is taking it
   * for a field of its own, so it is handed over rather than put away and summoned again.
   */
  fun hideForSystemKeyboard() {
    reset(null, awaitingSystem = current != null)
  }

  private fun reset(key: KeyboardSheetKey?, awaitingSystem: Boolean = false) {
    awaitingSystemKeyboard = awaitingSystem
    current = key
    _isEnteringText.value = false
    expansionTarget = false
  }
}

/** @param initialKeyboardHeightPx See [KeyboardSheetController]. */
@Composable
fun rememberKeyboardSheetController(initialKeyboardHeightPx: Int = 0): KeyboardSheetController {
  return remember { KeyboardSheetController(initialKeyboardHeightPx) }
}

/**
 * The controller of the [KeyboardSheetScaffold] a keyboard is being shown by, so its content can
 * drive the scaffold without the host having to thread callbacks down to it: taking text entry over
 * for a field of its own, or reading whether it is at full height.
 *
 * Only present inside a scaffold's keyboard content.
 */
val LocalKeyboardSheetController = staticCompositionLocalOf<KeyboardSheetController> {
  error("Not inside a KeyboardSheetScaffold's keyboard content.")
}
