/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emojikeyboard

import org.signal.mediakeyboard.screens.emoji.EmojiPageState

/**
 * Everything [EmojiKeyboardContent] needs to render.
 *
 * @param page The emoji grid, its categories and any search results.
 * @param isSearching Whether search has taken the keyboard's place, leaving the room for the system keyboard.
 */
data class EmojiKeyboardState(
  val page: EmojiPageState = EmojiPageState(),
  val isSearching: Boolean = false
)
