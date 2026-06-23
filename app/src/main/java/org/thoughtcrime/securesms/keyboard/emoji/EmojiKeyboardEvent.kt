/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyboard.emoji

import android.view.KeyEvent

/**
 * Mapping of [EmojiKeyboardCallback] methods into a sealed event class
 */
sealed interface EmojiKeyboardEvent {
  object OpenEmojiSearch : EmojiKeyboardEvent
  object CloseEmojiSearch : EmojiKeyboardEvent
  data class EmojiInsert(val emoji: String?) : EmojiKeyboardEvent
  data class EmojiKeyEvent(val keyEvent: KeyEvent?) : EmojiKeyboardEvent
}
