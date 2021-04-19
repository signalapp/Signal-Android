package org.thoughtcrime.securesms.emoji

import androidx.annotation.AttrRes
import org.thoughtcrime.securesms.R

/**
 * All the different Emoji categories the app is aware of in the order we want to display them.
 */
enum class EmojiCategory(val priority: Int, val key: String, @AttrRes val icon: Int) {
  PEOPLE(0, "People", R.attr.emoji_category_people),
  NATURE(1, "Nature", R.attr.emoji_category_nature),
  FOODS(2, "Foods", R.attr.emoji_category_foods),
  ACTIVITY(3, "Activity", R.attr.emoji_category_activity),
  PLACES(4, "Places", R.attr.emoji_category_places),
  OBJECTS(5, "Objects", R.attr.emoji_category_objects),
  SYMBOLS(6, "Symbols", R.attr.emoji_category_symbols),
  FLAGS(7, "Flags", R.attr.emoji_category_flags),
  EMOTICONS(8, "Emoticons", R.attr.emoji_category_emoticons);

  companion object {
    fun forKey(key: String) = values().first { it.key == key }
  }
}
