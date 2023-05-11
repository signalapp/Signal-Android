package org.thoughtcrime.securesms.conversation.colors

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.Avatars

class AvatarColorPair private constructor(
  @ColorInt val foregroundColor: Int,
  @ColorInt val backgroundColor: Int
) {
  companion object {
    @JvmStatic
    fun create(context: Context, avatarColor: AvatarColor): AvatarColorPair {
      return when (avatarColor) {
        AvatarColor.UNKNOWN -> AvatarColorPair(
          foregroundColor = ContextCompat.getColor(context, R.color.signal_colorOnSurface),
          backgroundColor = ContextCompat.getColor(context, R.color.signal_colorSurfaceVariant)
        )
        AvatarColor.ON_SURFACE_VARIANT -> AvatarColorPair(
          foregroundColor = ContextCompat.getColor(context, R.color.signal_colorOnSurfaceVariant),
          backgroundColor = ContextCompat.getColor(context, R.color.signal_colorSurfaceVariant)
        )
        else -> AvatarColorPair(
          foregroundColor = Avatars.getForegroundColor(avatarColor).colorInt,
          backgroundColor = avatarColor.colorInt()
        )
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AvatarColorPair

    if (foregroundColor != other.foregroundColor) return false
    if (backgroundColor != other.backgroundColor) return false

    return true
  }

  override fun hashCode(): Int {
    var result = foregroundColor
    result = 31 * result + backgroundColor
    return result
  }
}
