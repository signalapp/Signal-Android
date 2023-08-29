/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.v2.data.ConversationMessageElement
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.changeConstraints

/**
 * Represents a media-backed conversation item.
 */
class V2ConversationItemMediaViewHolder<Model : MappingModel<Model>>(
  private val binding: V2ConversationItemMediaBindingBridge,
  private val conversationContext: V2ConversationContext
) : V2ConversationItemTextOnlyViewHolder<Model>(
  binding.textBridge,
  conversationContext,
  V2FooterPositionDelegate(binding)
) {

  private var thumbnailSlide: Slide? = null
  private var placeholderTarget: PlaceholderTarget? = null
  private val thumbnailSize = intArrayOf(0, 0)

  init {
    binding.textBridge.conversationItemBodyWrapper.clipToOutline = true
  }

  override fun bind(model: Model) {
    conversationMessage = (model as ConversationMessageElement).conversationMessage
    presentThumbnail()
    super.bind(model)
  }

  private fun presentThumbnail() {
    val slideDeck = requireMediaMessage().slideDeck
    if (slideDeck.thumbnailSlides.isEmpty() || slideDeck.thumbnailSlides.size > 1) {
      binding.thumbnailStub.visibility = View.GONE
      thumbnailSize[0] = -1
      thumbnailSize[1] = -1

      binding.textBridge.root.changeConstraints {
        this.constrainMaxWidth(binding.textBridge.conversationItemBodyWrapper.id, 0)
      }
      return
    }

    binding.thumbnailStub.visibility = View.VISIBLE

    val thumbnail = slideDeck.thumbnailSlides.first()

    // TODO [alex] -- Is this correct?
    if (thumbnail == thumbnailSlide) {
      return
    }

    thumbnailSlide = thumbnail

    conversationContext.glideRequests.clear(binding.thumbnailStub.get())

    if (placeholderTarget != null) {
      conversationContext.glideRequests.clear(placeholderTarget)
    }
    // endif

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
      maxHeight
    )

    binding.thumbnailStub.get().updateLayoutParams {
      width = thumbnailSize[0]
      height = thumbnailSize[1]
    }

    binding.textBridge.root.changeConstraints {
      this.constrainMaxWidth(binding.textBridge.conversationItemBodyWrapper.id, thumbnailSize[0])
    }

    if (thumbnailBlur != null) {
      val placeholderTarget = PlaceholderTarget(binding.thumbnailStub.get())
      conversationContext
        .glideRequests
        .load(thumbnailBlur)
        .centerInside()
        .dontAnimate()
        .override(thumbnailSize[0], thumbnailSize[1])
        .into(placeholderTarget)

      this.placeholderTarget = placeholderTarget
    }

    if (thumbnailUri != null) {
      conversationContext
        .glideRequests
        .load(DecryptableStreamUriLoader.DecryptableUri(thumbnailUri))
        .centerInside()
        .dontAnimate()
        .override(thumbnailSize[0], thumbnailSize[1])
        .into(binding.thumbnailStub.get())
    }
  }

  private fun setThumbnailSize(
    thumbnailWidth: Int,
    thumbnailHeight: Int,
    maxWidth: Int,
    maxHeight: Int
  ) {
    if (thumbnailWidth == 0 || thumbnailHeight == 0) {
      thumbnailSize[0] = maxWidth
      thumbnailSize[1] = maxHeight
      return
    }

    if (thumbnailWidth <= maxWidth && thumbnailHeight <= maxHeight) {
      thumbnailSize[0] = thumbnailWidth
      thumbnailSize[1] = thumbnailHeight
      return
    }

    if (thumbnailWidth > maxWidth) {
      val thumbnailScale = 1 - ((thumbnailWidth - maxWidth) / thumbnailWidth.toFloat())

      thumbnailSize[0] = (thumbnailWidth * thumbnailScale).toInt()
      thumbnailSize[1] = (thumbnailHeight * thumbnailScale).toInt()
    }

    if (isThumbnailMetricsSatisfied(maxWidth, maxHeight)) {
      return
    }

    if (thumbnailHeight > maxHeight) {
      val thumbnailScale = 1 - ((thumbnailHeight - maxHeight) / thumbnailHeight.toFloat())

      thumbnailSize[0] = (thumbnailWidth * thumbnailScale).toInt()
      thumbnailSize[1] = (thumbnailHeight * thumbnailScale).toInt()
    }

    if (isThumbnailMetricsSatisfied(maxWidth, maxHeight)) {
      return
    }

    setThumbnailSize(
      thumbnailSize[0],
      thumbnailSize[1],
      maxWidth,
      maxHeight
    )
  }

  private fun isThumbnailMetricsSatisfied(maxWidth: Int, maxHeight: Int): Boolean {
    return thumbnailSize[0] in 1..maxWidth && thumbnailSize[1] in 1..maxHeight
  }

  private fun requireMediaMessage(): MediaMmsMessageRecord {
    return conversationMessage.messageRecord as MediaMmsMessageRecord
  }

  private inner class PlaceholderTarget(view: ImageView) : CustomViewTarget<ImageView, Drawable>(view) {
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
