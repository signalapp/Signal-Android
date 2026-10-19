/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emoji

import org.signal.mediakeyboard.data.EmojiCategoryPage
import org.signal.mediakeyboard.data.EmojiKeyboardCategory
import org.signal.mediakeyboard.data.KeyboardEmoji

/**
 * An [EmojiPageState] with recents and two categories, one of them holding an emoji with skin tone variations, for
 * previews and tests.
 */
fun previewEmojiPageState(): EmojiPageState {
  return EmojiPageState(
    pages = listOf(
      EmojiCategoryPage(EmojiKeyboardCategory.RECENTS, listOf("😂", "🎉", "🔥").map { KeyboardEmoji(it) }),
      EmojiCategoryPage(
        category = EmojiKeyboardCategory.PEOPLE,
        emoji = listOf("😀", "😂", "🥰", "😎", "🤔", "😢", "😡", "🥳").map { KeyboardEmoji(it) } +
          KeyboardEmoji("👍", listOf("👍", "👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"))
      ),
      EmojiCategoryPage(EmojiKeyboardCategory.NATURE, listOf("🐶", "🐱", "🦊", "🌸", "🌈").map { KeyboardEmoji(it) })
    ),
    selectedCategory = EmojiKeyboardCategory.RECENTS
  )
}
