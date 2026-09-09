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
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

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

  /** Whether the system keyboard is up, as reported by the target of the IME inset animation. */
  var isSystemKeyboardVisible: Boolean by mutableStateOf(false)
    internal set

  /**
   * True while the system keyboard is on its way in or out. [isSystemKeyboardVisible] reads the
   * target of that animation, so it goes false the moment a hide is asked for, well before the
   * space is handed back.
   */
  var isSystemKeyboardAnimating: Boolean by mutableStateOf(false)
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

  /** True when no keyboard is up, on its way out, or being held space for. */
  val isSettled: Boolean get() = current == null && !isSystemKeyboardVisible && !isSystemKeyboardAnimating && !awaitingSystemKeyboard

  /**
   * Suspends until [isSettled], so a caller can act on a content area that has been handed all of
   * its space back. Reads the same snapshot state the scaffold writes, so there is no settle to miss
   * for a keyboard that goes away without animating.
   */
  suspend fun awaitSettled() {
    snapshotFlow { isSettled }.first { it }
  }

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
