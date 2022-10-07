package org.thoughtcrime.securesms.color

import android.content.Context
import android.os.Parcelable
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.R

/**
 * Represents a set of colors to be applied to the foreground and background of a view.
 *
 * Supports mixing color ints and color resource ids.
 */
@Parcelize
data class ViewColorSet(
  val foreground: ViewColor,
  val background: ViewColor
) : Parcelable {
  companion object {
    val PRIMARY = ViewColorSet(
      foreground = ViewColor.ColorResource(R.color.signal_colorOnPrimary),
      background = ViewColor.ColorResource(R.color.signal_colorPrimary)
    )

    fun forCustomColor(@ColorInt customColor: Int): ViewColorSet {
      return ViewColorSet(
        foreground = ViewColor.ColorResource(R.color.signal_colorOnCustom),
        background = ViewColor.ColorValue(customColor)
      )
    }
  }

  @Parcelize
  sealed class ViewColor : Parcelable {

    @ColorInt
    abstract fun resolve(context: Context): Int

    @Parcelize
    data class ColorValue(@ColorInt val colorInt: Int) : ViewColor() {
      override fun resolve(context: Context): Int {
        return colorInt
      }
    }

    @Parcelize
    data class ColorResource(@ColorRes val colorRes: Int) : ViewColor() {
      override fun resolve(context: Context): Int {
        return ContextCompat.getColor(context, colorRes)
      }
    }
  }
}
