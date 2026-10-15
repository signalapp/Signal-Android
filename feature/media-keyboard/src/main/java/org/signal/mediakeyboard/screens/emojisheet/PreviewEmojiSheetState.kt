/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emojisheet

import org.signal.mediakeyboard.data.EmojiCategoryPage
import org.signal.mediakeyboard.data.EmojiKeyboardCategory
import org.signal.mediakeyboard.data.KeyboardEmoji
import org.signal.mediakeyboard.screens.emoji.EmojiPageState

/**
 * An [EmojiSheetState] with a "This message" section, recents and two categories, for previews and tests.
 */
fun previewEmojiSheetState(showCustomizeReactions: Boolean = true): EmojiSheetState {
  return EmojiSheetState(
    page = EmojiPageState(
      pages = listOf(
        EmojiCategoryPage(EmojiKeyboardCategory.THIS_MESSAGE, listOf("👍", "❤️").map { KeyboardEmoji(it) }),
        EmojiCategoryPage(EmojiKeyboardCategory.RECENTS, listOf("😂", "🎉", "🔥").map { KeyboardEmoji(it) }),
        EmojiCategoryPage(
          category = EmojiKeyboardCategory.PEOPLE,
          emoji = listOf("😀", "😂", "🥰", "😎", "🤔", "😢", "😡", "🥳").map { KeyboardEmoji(it) } +
            KeyboardEmoji("👍", listOf("👍", "👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"))
        ),
        EmojiCategoryPage(EmojiKeyboardCategory.NATURE, listOf("🐶", "🐱", "🦊", "🌸", "🌈").map { KeyboardEmoji(it) })
      ),
      selectedCategory = EmojiKeyboardCategory.THIS_MESSAGE
    ),
    showCustomizeReactions = showCustomizeReactions
  )
}
