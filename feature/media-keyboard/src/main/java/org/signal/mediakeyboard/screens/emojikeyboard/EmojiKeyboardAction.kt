/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emojikeyboard

/**
 * What an [EmojiKeyboard] needs its host to carry out.
 */
sealed interface EmojiKeyboardAction {

  /** @param emoji The emoji to insert, already resolved to the user's preferred variation. */
  data class EmojiSelected(val emoji: String) : EmojiKeyboardAction

  /** Delete backwards from wherever the host's own field has its cursor. */
  data object Backspace : EmojiKeyboardAction

  /** Search is taking the keyboard's place, and wants the system keyboard alongside it. */
  data object SearchOpened : EmojiKeyboardAction

  /** Search is giving the keyboard back. */
  data object SearchClosed : EmojiKeyboardAction
}
