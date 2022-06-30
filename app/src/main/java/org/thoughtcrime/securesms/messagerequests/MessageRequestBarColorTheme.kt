package org.thoughtcrime.securesms.messagerequests

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R

enum class MessageRequestBarColorTheme(
  private val containerBackgroundColor: Int,
  private val buttonBackgroundColor: Int,
  private val buttonForegroundDenyColor: Int,
  private val buttonForegroundAcceptColor: Int
) {
  WALLPAPER(
    R.color.message_request_bar_container_background_wallpaper,
    R.color.message_request_bar_background_wallpaper,
    R.color.message_request_bar_denyForeground_wallpaper,
    R.color.message_request_bar_acceptForeground_wallpaper
  ),
  NORMAL(
    R.color.message_request_bar_container_background_normal,
    R.color.message_request_bar_background_normal,
    R.color.message_request_bar_denyForeground_normal,
    R.color.message_request_bar_acceptForeground_normal
  );

  @ColorInt
  fun getContainerButtonBackgroundColor(context: Context): Int = ContextCompat.getColor(context, containerBackgroundColor)

  @ColorInt
  fun getButtonBackgroundColor(context: Context): Int = ContextCompat.getColor(context, buttonBackgroundColor)

  @ColorInt
  fun getButtonForegroundDenyColor(context: Context): Int = ContextCompat.getColor(context, buttonForegroundDenyColor)

  @ColorInt
  fun getButtonForegroundAcceptColor(context: Context): Int = ContextCompat.getColor(context, buttonForegroundAcceptColor)

  companion object {
    @JvmStatic
    fun resolveTheme(hasWallpaper: Boolean): MessageRequestBarColorTheme {
      return if (hasWallpaper) WALLPAPER else NORMAL
    }
  }
}
