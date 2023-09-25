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
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.core.graphics.toRectF
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.core.view.children
import androidx.core.view.doOnDetach
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.util.Projection
import org.thoughtcrime.securesms.util.Projection.Corners

/**
 * Drawable that renders the given chat colors at a specified coordinate offset.
 * This is meant to be used in conjunction with [ChatColorsItemDecoration]
 */
class ChatColorsDrawable : Drawable() {

  companion object {
    private var maskDrawable: Drawable? = null
    private var latestBounds: Rect? = null

    /**
     * Binds the ChatColorsDrawable static cache to the lifecycle of the given recycler-view
     */
    fun attach(recyclerView: RecyclerView) {
      recyclerView.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
        applyBounds(Rect(left, top, right, bottom))
      }

      recyclerView.addItemDecoration(ChatColorsItemDecoration)
      recyclerView.doOnDetach {
        maskDrawable = null
      }
    }

    private fun applyBounds(bounds: Rect) {
      latestBounds = bounds
      maskDrawable?.bounds = bounds
    }
  }

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

  private var gradientColors: ChatColors? = null
  private var corners: FloatArray = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
  private var fillColor: Int = 0

  override fun draw(canvas: Canvas) {
    if (gradientColors == null && fillColor == 0) {
      return
    }

    val mask = maskDrawable
    if (gradientColors != null && mask != null) {
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
        canvas.drawColor(fillColor)
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
    return gradientColors == null
  }

  fun setCorners(corners: FloatArray) {
    if (!this.corners.contentEquals(corners)) {
      this.corners = corners
      invalidateSelf()
    }
  }

  /**
   * Sets the chat color and shape as specified. If the colors are a gradient,
   * we will use masking to draw, and we will draw every time we're told to by
   * the decorator.
   *
   * If a solid color is set, we can skip drawing as we move, since we haven't changed.
   */
  fun setChatColors(
    chatColors: ChatColors,
    corners: Corners
  ) {
    this.corners = corners.toRadii()

    if (chatColors.isGradient()) {
      if (maskDrawable == null) {
        maskDrawable = chatColors.chatBubbleMask

        val maskBounds = latestBounds
        if (maskBounds != null) {
          maskDrawable?.bounds = maskBounds
        }
      }

      this.fillColor = 0
      this.gradientColors = chatColors
    } else {
      this.fillColor = chatColors.asSingleColor()
      this.gradientColors = null
    }

    invalidateSelf()
  }

  private object ChatColorsItemDecoration : RecyclerView.ItemDecoration() {
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
