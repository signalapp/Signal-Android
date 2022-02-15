package org.thoughtcrime.securesms.conversation.colors

import android.content.Context
import androidx.annotation.ColorInt
import org.thoughtcrime.securesms.util.ThemeUtil

/**
 * Class which stores information for a Recipient's name color in a group.
 */
class NameColor(
  @ColorInt private val lightColor: Int,
  @ColorInt private val darkColor: Int
) {
  @ColorInt
  fun getColor(context: Context): Int {
    return if (ThemeUtil.isDarkTheme(context)) {
      darkColor
    } else {
      lightColor
    }
  }
}
