/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.data

import android.graphics.drawable.Drawable
import androidx.annotation.StringRes
import org.signal.mediakeyboard.R

/**
 * Data source for the emoji page of the media keyboard.
 */
interface EmojiKeyboardRepository {

  /**
   * All emoji, grouped into display categories. Should not include [EmojiKeyboardCategory.RECENTS],
   * which is fetched separately via [getRecentEmoji].
   */
  suspend fun getEmojiPages(): List<EmojiCategoryPage>

  /**
   * Recently-used emoji, most recent first. May be empty.
   */
  suspend fun getRecentEmoji(): List<KeyboardEmoji>

  /**
   * Emoji matching the given search query.
   */
  suspend fun search(query: String): List<KeyboardEmoji>

  /**
   * The user's preferred (e.g. skin tone) variation for each canonical emoji.
   */
  suspend fun getPreferredVariations(): Map<String, String>

  fun setPreferredVariation(canonical: String, variation: String)

  /**
   * Called whenever the user picks an emoji, so that recents can be tracked.
   */
  suspend fun onEmojiUsed(emoji: String)

  /**
   * A custom drawable to render the given emoji with, or null to render it as plain text with
   * the system emoji font.
   */
  fun getEmojiDrawable(emoji: String): Drawable? = null
}

/**
 * A single selectable emoji.
 *
 * @param canonical The default form of the emoji.
 * @param variations All selectable variations (e.g. skin tones), including the canonical form.
 *   Empty if the emoji has no variations.
 */
data class KeyboardEmoji(
  val canonical: String,
  val variations: List<String> = emptyList()
) {
  val hasVariations: Boolean = variations.size > 1
}

data class EmojiCategoryPage(
  val category: EmojiKeyboardCategory,
  val emoji: List<KeyboardEmoji>
)

enum class EmojiKeyboardCategory(val key: String, @field:StringRes val label: Int) {
  RECENTS("Recents", R.string.MediaKeyboard__recently_used),
  PEOPLE("People", R.string.MediaKeyboard__smileys_and_people),
  NATURE("Nature", R.string.MediaKeyboard__nature),
  FOODS("Foods", R.string.MediaKeyboard__food),
  ACTIVITY("Activity", R.string.MediaKeyboard__activities),
  PLACES("Places", R.string.MediaKeyboard__places),
  OBJECTS("Objects", R.string.MediaKeyboard__objects),
  SYMBOLS("Symbols", R.string.MediaKeyboard__symbols),
  FLAGS("Flags", R.string.MediaKeyboard__flags),
  EMOTICONS("Emoticons", R.string.MediaKeyboard__emoticons)
}
