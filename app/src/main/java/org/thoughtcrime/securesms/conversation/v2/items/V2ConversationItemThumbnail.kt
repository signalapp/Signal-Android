/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Size
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.withTranslation
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.v2.items.V2ConversationItemUtils.isThumbnailAtBottomOfBubble
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.mms.Slide

/**
 * ImageView subclass that adds support for a foreground drawable and
 * gradient at the bottom, rendered in onDrawForeground.
 *
 * This is lighter weight then adding a bunch of extra, possibly unnecessary views
 * to the relevant layouts.
 *
 * Also encapsulates the logic around presenting thumbnails to the user.
 */
class V2ConversationItemThumbnail @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

  companion object {
    private val FOREGROUND_SHADE_HEIGHT = 32.dp
    private val UNSET_SIZE = Size(-1, -1)
    private const val MAX_SIZE_RECURSION_DEPTH = 5
  }

  private val gradientPaint = Paint().apply {
    isAntiAlias = true
  }

  private val rect = Rect()
  private var drawForegroundShade: Boolean = true
  private var compatForegroundDrawable: Drawable? = null

  private var thumbnailSlide: Slide? = null
  private var fastPreflightId: String? = null

  private var placeholderTarget: PlaceholderTarget? = null
  private var thumbnailSize = UNSET_SIZE

  val thumbWidth: Int get() = thumbnailSize.width

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    gradientPaint.shader = LinearGradient(
      w / 2f,
      0f,
      w / 2f,
      FOREGROUND_SHADE_HEIGHT.toFloat(),
      intArrayOf(0x00000000, 0x88000000.toInt()),
      floatArrayOf(0f, 1f),
      Shader.TileMode.CLAMP
    )
  }

  override fun onDrawForeground(canvas: Canvas) {
    super.onDrawForeground(canvas)

    canvas.getClipBounds(rect)

    if (drawForegroundShade) {
      canvas.withTranslation(y = rect.height() - FOREGROUND_SHADE_HEIGHT.toFloat()) {
        canvas.drawPaint(gradientPaint)
      }
    }

    // Sizing?
    compatForegroundDrawable?.draw(canvas)
  }

  fun presentThumbnail(
    mediaMessage: MmsMessageRecord,
    conversationContext: V2ConversationContext
  ) {
    val slideDeck = mediaMessage.slideDeck
    if (slideDeck.thumbnailSlides.isEmpty() || slideDeck.thumbnailSlides.size > 1) {
      visibility = View.GONE
      thumbnailSize = UNSET_SIZE

      return
    }

    visibility = View.VISIBLE

    val thumbnail = slideDeck.thumbnailSlides.first()
    val fastPreflightId = slideDeck.thumbnailSlides.first().fastPreflightId

    if (thumbnail == thumbnailSlide && fastPreflightId == this.fastPreflightId) {
      return
    }

    thumbnailSlide = thumbnail
    this.fastPreflightId = fastPreflightId

    conversationContext.requestManager.clear(this)

    if (placeholderTarget != null) {
      conversationContext.requestManager.clear(placeholderTarget)
    }

    val thumbnailUri = thumbnail.uri
    val thumbnailBlur = thumbnail.placeholderBlur

    val thumbnailAttachment = thumbnail.asAttachment()
    val thumbnailWidth = thumbnailAttachment.width
    val thumbnailHeight = thumbnailAttachment.height

    val maxWidth = context.resources.getDimensionPixelSize(R.dimen.media_bubble_max_width)
    val maxHeight = context.resources.getDimensionPixelSize(R.dimen.media_bubble_max_height)

    setThumbnailSize(
      thumbnailWidth,
      thumbnailHeight,
      maxWidth,
      maxHeight,
      0
    )

    updateLayoutParams {
      width = thumbnailSize.width
      height = thumbnailSize.height
    }

    if (thumbnailBlur != null) {
      val placeholderTarget = PlaceholderTarget(this)
      conversationContext
        .requestManager
        .load(thumbnailBlur)
        .centerInside()
        .dontAnimate()
        .override(thumbnailSize.width, thumbnailSize.height)
        .into(placeholderTarget)

      this.placeholderTarget = placeholderTarget
    }

    if (thumbnailUri != null) {
      conversationContext
        .requestManager
        .load(DecryptableStreamUriLoader.DecryptableUri(thumbnailUri))
        .centerInside()
        .dontAnimate()
        .override(thumbnailSize.width, thumbnailSize.height)
        .into(this)
    }

    setDrawForegroundShade(mediaMessage.isThumbnailAtBottomOfBubble(context))
  }

  private fun setDrawForegroundShade(drawForegroundShade: Boolean) {
    this.drawForegroundShade = drawForegroundShade
    invalidate()
  }

  private fun setCompatForegroundDrawable(drawable: Drawable?) {
    this.compatForegroundDrawable = drawable
    invalidate()
  }

  private fun setThumbnailSize(
    thumbnailWidth: Int,
    thumbnailHeight: Int,
    maxWidth: Int,
    maxHeight: Int,
    depth: Int
  ) {
    if (thumbnailWidth == 0 || thumbnailHeight == 0 || depth >= MAX_SIZE_RECURSION_DEPTH) {
      thumbnailSize = Size(maxWidth, maxHeight)
      return
    }

    if (thumbnailWidth <= maxWidth && thumbnailHeight <= maxHeight) {
      thumbnailSize = Size(thumbnailWidth, thumbnailHeight)
      return
    }

    if (thumbnailWidth > maxWidth) {
      val thumbnailScale = 1 - ((thumbnailWidth - maxWidth) / thumbnailWidth.toFloat())

      thumbnailSize = Size(
        (thumbnailWidth * thumbnailScale).toInt(),
        (thumbnailHeight * thumbnailScale).toInt()
      )
    }

    if (isThumbnailMetricsSatisfied(maxWidth, maxHeight)) {
      return
    }

    if (thumbnailHeight > maxHeight) {
      val thumbnailScale = 1 - ((thumbnailHeight - maxHeight) / thumbnailHeight.toFloat())

      thumbnailSize = Size(
        (thumbnailWidth * thumbnailScale).toInt(),
        (thumbnailHeight * thumbnailScale).toInt()
      )
    }

    if (isThumbnailMetricsSatisfied(maxWidth, maxHeight)) {
      return
    }

    setThumbnailSize(
      thumbnailSize.width,
      thumbnailSize.height,
      maxWidth,
      maxHeight,
      depth + 1
    )
  }

  private fun isThumbnailMetricsSatisfied(maxWidth: Int, maxHeight: Int): Boolean {
    return thumbnailSize.width in 1..maxWidth && thumbnailSize.height in 1..maxHeight
  }

  private class PlaceholderTarget(view: ImageView) : CustomViewTarget<ImageView, Drawable>(view) {
    override fun onLoadFailed(errorDrawable: Drawable?) {
      view.background = errorDrawable
    }

    override fun onResourceCleared(placeholder: Drawable?) {
      view.background = placeholder
    }

    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
      view.background = resource
    }
  }
}
