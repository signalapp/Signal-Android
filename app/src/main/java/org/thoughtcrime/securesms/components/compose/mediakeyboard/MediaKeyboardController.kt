/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.compose.mediakeyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Asks a [MediaKeyboardScaffold] for a keyboard.
 *
 * Stable and safe to hold outside composition, so view code can call into it. Everything flowing the
 * other way is a [MediaKeyboardEvents].
 *
 * @param initialKeyboardHeightPx Height to use before one has been measured, typically persisted
 *   from an earlier run.
 */
@Stable
class MediaKeyboardController(initialKeyboardHeightPx: Int = 0) {

  /** The keyboard currently up, or null when none of ours is. */
  var current: MediaKeyboardKey? by mutableStateOf(null)
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

  val isShowing: Boolean get() = current != null

  fun show(key: MediaKeyboardKey) {
    current = key
    awaitingSystemKeyboard = false
  }

  fun hide() {
    current = null
    awaitingSystemKeyboard = false
  }

  /** Closes [key] if it is already up, otherwise swaps to it. */
  fun toggle(key: MediaKeyboardKey) {
    if (current == key) hide() else show(key)
  }

  /**
   * Puts ours away because the system keyboard is being brought up instead. Only holds the space if
   * one of ours was up; with nothing to hand over, content should just follow the keyboard in.
   */
  fun hideForSystemKeyboard() {
    awaitingSystemKeyboard = current != null
    current = null
  }
}

/** @param initialKeyboardHeightPx See [MediaKeyboardController]. */
@Composable
fun rememberMediaKeyboardController(initialKeyboardHeightPx: Int = 0): MediaKeyboardController {
  return remember { MediaKeyboardController(initialKeyboardHeightPx) }
}
