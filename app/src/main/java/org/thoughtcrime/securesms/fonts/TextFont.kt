package org.thoughtcrime.securesms.fonts

import android.graphics.Typeface
import androidx.annotation.DrawableRes
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost

/**
 * Describes which font the user wishes to render content in.
 */
enum class TextFont(@DrawableRes val icon: Int, val fallbackFamily: String, val fallbackStyle: Int, val isAllCaps: Boolean) {
  REGULAR(R.drawable.ic_font_regular, "sans-serif", Typeface.NORMAL, false),
  BOLD(R.drawable.ic_font_bold, "sans-serif", Typeface.BOLD, false),
  SERIF(R.drawable.ic_font_serif, "serif", Typeface.NORMAL, false),
  SCRIPT(R.drawable.ic_font_script, "serif", Typeface.BOLD, false),
  CONDENSED(R.drawable.ic_font_condensed, "sans-serif", Typeface.BOLD, true);

  companion object {
    fun fromStyle(style: StoryTextPost.Style): TextFont {
      return when (style) {
        StoryTextPost.Style.DEFAULT -> REGULAR
        StoryTextPost.Style.REGULAR -> REGULAR
        StoryTextPost.Style.BOLD -> BOLD
        StoryTextPost.Style.SERIF -> SERIF
        StoryTextPost.Style.SCRIPT -> SCRIPT
        StoryTextPost.Style.CONDENSED -> CONDENSED
      }
    }
  }
}
