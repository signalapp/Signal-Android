/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emojisheet

import org.signal.mediakeyboard.screens.emoji.EmojiPageScreenEvents

sealed interface EmojiSheetEvents {
  /** Something happened in the emoji grid or its category rail. */
  data class EmojiPageScreenEvent(val event: EmojiPageScreenEvents) : EmojiSheetEvents

  /** The user tapped the button for changing their quick reactions. */
  data object CustomizeReactionsClicked : EmojiSheetEvents
}
