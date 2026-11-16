/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emojisheet

import org.signal.mediakeyboard.screens.emoji.EmojiPageState

/**
 * Everything [EmojiSheetContent] needs to render.
 *
 * @param page The emoji grid, its categories and any search results.
 * @param showCustomizeReactions Whether the category rail offers a way to change the user's quick reactions.
 */
data class EmojiSheetState(
  val page: EmojiPageState = EmojiPageState(),
  val showCustomizeReactions: Boolean = false
)
