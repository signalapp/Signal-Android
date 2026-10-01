/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emoji

import org.signal.mediakeyboard.data.EmojiCategoryPage
import org.signal.mediakeyboard.data.EmojiKeyboardCategory
import org.signal.mediakeyboard.data.KeyboardEmoji

data class EmojiPageState(
  val pages: List<EmojiCategoryPage> = emptyList(),
  val selectedCategory: EmojiKeyboardCategory? = null,
  val preferredVariations: Map<String, String> = emptyMap(),
  val variationSelector: VariationSelectorTarget? = null,
  val scrollTarget: EmojiKeyboardCategory? = null,
  val searchResults: List<KeyboardEmoji>? = null,
  val searchQuery: String = ""
) {

  /**
   * Identifies the exact grid cell (via its lazy-grid key) that should display the
   * skin tone variation popup.
   */
  data class VariationSelectorTarget(
    val cellKey: String,
    val emoji: KeyboardEmoji
  )

  fun displayEmoji(emoji: KeyboardEmoji): String {
    return preferredVariations[emoji.canonical] ?: emoji.canonical
  }
}
