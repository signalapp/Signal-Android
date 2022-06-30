package org.thoughtcrime.securesms.components.quotes

import android.content.Context
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R

enum class QuoteViewColorTheme(
  private val backgroundColorRes: Int,
  private val barColorRes: Int,
  private val foregroundColorRes: Int
) {

  INCOMING_WALLPAPER(
    R.color.quote_view_background_incoming_wallpaper,
    R.color.quote_view_bar_incoming_wallpaper,
    R.color.quote_view_foreground_incoming_wallpaper
  ),
  INCOMING_NORMAL(
    R.color.quote_view_background_incoming_normal,
    R.color.quote_view_bar_incoming_normal,
    R.color.quote_view_foreground_incoming_normal
  ),
  OUTGOING_WALLPAPER(
    R.color.quote_view_background_outgoing_wallpaper,
    R.color.quote_view_bar_outgoing_wallpaper,
    R.color.quote_view_foreground_outgoing_wallpaper
  ),
  OUTGOING_NORMAL(
    R.color.quote_view_background_outgoing_normal,
    R.color.quote_view_bar_outgoing_normal,
    R.color.quote_view_foreground_outgoing_normal
  );

  fun getBackgroundColor(context: Context) = ContextCompat.getColor(context, backgroundColorRes)
  fun getBarColor(context: Context) = ContextCompat.getColor(context, barColorRes)
  fun getForegroundColor(context: Context) = ContextCompat.getColor(context, foregroundColorRes)

  companion object {
    @JvmStatic
    fun resolveTheme(isOutgoing: Boolean, isPreview: Boolean, hasWallpaper: Boolean): QuoteViewColorTheme {
      return when {
        isPreview && hasWallpaper -> INCOMING_WALLPAPER
        isPreview && !hasWallpaper -> INCOMING_NORMAL
        isOutgoing && hasWallpaper -> OUTGOING_WALLPAPER
        !isOutgoing && hasWallpaper -> INCOMING_WALLPAPER
        isOutgoing && !hasWallpaper -> OUTGOING_NORMAL
        else -> INCOMING_NORMAL
      }
    }
  }
}
