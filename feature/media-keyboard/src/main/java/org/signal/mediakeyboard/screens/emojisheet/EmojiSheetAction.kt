/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emojisheet

/**
 * What an [EmojiSheet] needs its host to carry out.
 */
sealed interface EmojiSheetAction {

  /** @param emoji The emoji the user picked, already resolved to their preferred variation. */
  data class EmojiSelected(val emoji: String) : EmojiSheetAction

  /** Take the user to wherever their quick reactions are changed. */
  data object CustomizeReactionsClicked : EmojiSheetAction
}
