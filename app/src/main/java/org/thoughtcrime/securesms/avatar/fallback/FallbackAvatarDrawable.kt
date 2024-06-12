/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.fallback

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.airbnb.lottie.SimpleColorFilter
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.RoundedCornerTreatment
import com.google.android.material.shape.ShapeAppearanceModel
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.Avatars
import org.thoughtcrime.securesms.avatar.TextAvatarDrawable
import org.thoughtcrime.securesms.conversation.colors.AvatarColorPair

class FallbackAvatarDrawable(
  private val context: Context,
  private val fallbackAvatar: FallbackAvatar
) : MaterialShapeDrawable() {

  private val avatarColorPair: AvatarColorPair = AvatarColorPair.create(context, fallbackAvatar.color)
  private var avatarSize: FallbackAvatar.Size = FallbackAvatar.Size.SMALL
  private var icon: Drawable? = null

  init {
    fillColor = ColorStateList.valueOf(avatarColorPair.backgroundColor)
  }

  fun circleCrop(): FallbackAvatarDrawable {
    shapeAppearanceModel = ShapeAppearanceModel.builder()
      .setAllCorners(RoundedCornerTreatment())
      .setAllCornerSizes(RelativeCornerSize(0.5f))
      .build()
    return this
  }

  override fun onBoundsChange(bounds: Rect) {
    super.onBoundsChange(bounds)

    avatarSize = FallbackAvatar.getSizeByPx(bounds.width())
    icon = when (fallbackAvatar) {
      is FallbackAvatar.Resource -> {
        val resourceIcon = ContextCompat.getDrawable(context, fallbackAvatar.getIconBySize(avatarSize))!!

        val iconBounds = Rect(bounds)
        iconBounds.inset(
          ((bounds.width() - (bounds.width() * FallbackAvatar.ICON_TO_BACKGROUND_SCALE)) / 2f).toInt(),
          ((bounds.height() - (bounds.height() * FallbackAvatar.ICON_TO_BACKGROUND_SCALE)) / 2f).toInt()
        )

        resourceIcon.bounds = iconBounds
        resourceIcon
      }

      is FallbackAvatar.Text -> TextAvatarDrawable(
        context = context,
        avatar = Avatar.Text(
          fallbackAvatar.content,
          Avatars.ColorPair(avatarColorPair.backgroundColor, avatarColorPair.foregroundColor, ""),
          Avatar.DatabaseId.DoNotPersist
        ),
        size = bounds.width()
      )

      FallbackAvatar.Transparent -> null
    }

    icon?.alpha = alpha
    icon?.colorFilter = SimpleColorFilter(avatarColorPair.foregroundColor)
  }

  override fun draw(canvas: Canvas) {
    if (icon == null) return

    super.draw(canvas)
    icon?.draw(canvas)
  }

  override fun setAlpha(alpha: Int) {
    super.setAlpha(alpha)
    icon?.alpha = alpha
    invalidateSelf()
  }
}
