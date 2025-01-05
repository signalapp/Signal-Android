package org.thoughtcrime.securesms.emoji

import androidx.annotation.AttrRes
import androidx.annotation.StringRes
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

  @StringRes
  fun getCategoryLabel(): Int {
    return getCategoryLabel(icon)
  }

  companion object {
    @JvmStatic
    fun forKey(key: String) = entries.first { it.key == key }

    @JvmStatic
    @StringRes
    fun getCategoryLabel(@AttrRes iconAttr: Int): Int {
      return when (iconAttr) {
        R.attr.emoji_category_people -> R.string.ReactWithAnyEmojiBottomSheetDialogFragment__smileys_and_people
        R.attr.emoji_category_nature -> R.string.ReactWithAnyEmojiBottomSheetDialogFragment__nature
        R.attr.emoji_category_foods -> R.string.ReactWithAnyEmojiBottomSheetDialogFragment__food
        R.attr.emoji_category_activity -> R.string.ReactWithAnyEmojiBottomSheetDialogFragment__activities
        R.attr.emoji_category_places -> R.string.ReactWithAnyEmojiBottomSheetDialogFragment__places
        R.attr.emoji_category_objects -> R.string.ReactWithAnyEmojiBottomSheetDialogFragment__objects
        R.attr.emoji_category_symbols -> R.string.ReactWithAnyEmojiBottomSheetDialogFragment__symbols
        R.attr.emoji_category_flags -> R.string.ReactWithAnyEmojiBottomSheetDialogFragment__flags
        R.attr.emoji_category_emoticons -> R.string.ReactWithAnyEmojiBottomSheetDialogFragment__emoticons
        else -> throw AssertionError()
      }
    }
  }
}
