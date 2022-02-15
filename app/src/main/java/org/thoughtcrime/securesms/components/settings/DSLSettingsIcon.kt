package org.thoughtcrime.securesms.components.settings

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R

const val NO_TINT = -1

sealed class DSLSettingsIcon {

  private data class FromResource(
    @DrawableRes private val iconId: Int,
    @ColorRes private val iconTintId: Int
  ) : DSLSettingsIcon() {
    override fun resolve(context: Context) = requireNotNull(ContextCompat.getDrawable(context, iconId)).apply {
      if (iconTintId != NO_TINT) {
        colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, iconTintId), PorterDuff.Mode.SRC_IN)
      }
    }
  }

  private data class FromDrawable(
    private val drawable: Drawable
  ) : DSLSettingsIcon() {
    override fun resolve(context: Context): Drawable = drawable
  }

  abstract fun resolve(context: Context): Drawable

  companion object {
    @JvmStatic
    fun from(@DrawableRes iconId: Int, @ColorRes iconTintId: Int = R.color.signal_icon_tint_primary): DSLSettingsIcon = FromResource(iconId, iconTintId)

    @JvmStatic
    fun from(drawable: Drawable): DSLSettingsIcon = FromDrawable(drawable)
  }
}
