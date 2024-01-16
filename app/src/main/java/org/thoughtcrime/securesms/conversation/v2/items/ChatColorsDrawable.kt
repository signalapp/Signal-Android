/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.core.graphics.toRectF
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.v2.items.ChatColorsDrawable.ChatColorsItemDecoration
import org.thoughtcrime.securesms.util.Projection
import org.thoughtcrime.securesms.util.Projection.Corners

/**
 * Drawable that renders the given chat colors at a specified coordinate offset.
 * This is meant to be used in conjunction with [ChatColorsItemDecoration]
 */
class ChatColorsDrawable(
  private val dataProvider: () -> ChatColorsData
) : Drawable() {

  /**
   * Object allowing you to inject global color / masking.
   */
  data class ChatColorsData(
    var chatColors: ChatColors?,
    var mask: Drawable?
  )

  /**
   * Translation coordinates so that the mask is drawn at the right location
   * on the screen.
   */
  private val maskOffset = PointF()

  /**
   * Clipping path that includes the dimensions and corners for this view.
   */
  private val path = Path()
  private val rect = RectF()

  private var corners: FloatArray = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

  private var localChatColors: ChatColors? = null
  private var localMask: Drawable? = null

  override fun draw(canvas: Canvas) {
    val chatColors = getChatColors() ?: return

    val mask = getMask()
    if (chatColors.isGradient() && mask != null) {
      canvas.withTranslation(-maskOffset.x, -maskOffset.y) {
        canvas.withClip(path) {
          mask.draw(canvas)
        }
      }
    } else {
      path.reset()
      rect.set(bounds)
      path.addRoundRect(rect, corners, Path.Direction.CW)
      canvas.withClip(path) {
        canvas.drawColor(chatColors.asSingleColor())
      }
    }
  }

  override fun setAlpha(alpha: Int) = Unit

  override fun setColorFilter(colorFilter: ColorFilter?) = Unit

  override fun getOpacity(): Int {
    return PixelFormat.TRANSLUCENT
  }

  /**
   * Note: APIs had the wrong name for setPath here, so we have to use the deprecated method.
   */
  @Suppress("DEPRECATION")
  override fun getOutline(outline: Outline) {
    val path = Path()
    path.addRoundRect(
      bounds.toRectF(),
      corners,
      Path.Direction.CW
    )

    outline.setConvexPath(path)
  }

  /**
   * Applies the given [Projection] as the clipping path for the canvas on subsequent draws.
   * Also applies the given [Projection]'s (x,y) (Top, Left) coordinates as the mask offset,
   * which is used as a canvas translation before drawing.
   *
   * This is done separately from setting the corners and color, because it needs to happen
   * on every frame we would normally perform a decorator onDraw, whereas setting the corners
   * and color only needs to happen on bind.
   */
  fun applyMaskProjection(projection: Projection) {
    path.reset()
    projection.applyToPath(path)

    maskOffset.set(
      projection.x,
      projection.y
    )

    invalidateSelf()
  }

  fun isSolidColor(): Boolean {
    return getChatColors()?.isGradient() == false
  }

  fun setCorners(corners: FloatArray) {
    if (!this.corners.contentEquals(corners)) {
      this.corners = corners
      invalidateSelf()
    }
  }

  /**
   * Sets the shape as specified. If the colors are a gradient,
   * we will use masking to draw, and we will draw every time we're told to by
   * the decorator.
   */
  fun setCorners(
    corners: Corners
  ) {
    setCorners(corners.toRadii())
  }

  fun setLocalChatColors(
    chatColors: ChatColors
  ) {
    localChatColors = chatColors

    localMask = if (chatColors.isGradient()) {
      chatColors.chatBubbleMask
    } else {
      null
    }

    invalidateSelf()
  }

  fun clearLocalChatColors() {
    localChatColors = null
    localMask = null

    invalidateSelf()
  }

  private fun getChatColors(): ChatColors? = localChatColors ?: dataProvider().chatColors

  private fun getMask(): Drawable? = localMask ?: dataProvider().mask

  object ChatColorsItemDecoration : RecyclerView.ItemDecoration() {
    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
      parent.children.map { parent.getChildViewHolder(it) }.filterIsInstance<ChatColorsDrawableInvalidator>().forEach { element ->
        element.invalidateChatColorsDrawable(parent)
      }
    }
  }

  interface ChatColorsDrawableInvalidator {
    fun invalidateChatColorsDrawable(coordinateRoot: ViewGroup)
  }
}
