/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.QuoteView
import org.thoughtcrime.securesms.conversation.v2.data.ConversationMessageElement
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.changeConstraints
import org.thoughtcrime.securesms.util.isStoryReaction
import org.thoughtcrime.securesms.util.visible

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
    presentQuote()
    updateMediaConstraints()
  }

  private fun updateMediaConstraints() {
    binding.bodyContentSpacer.visible = (hasGroupSenderName() && hasThumbnail()) || hasQuote()

    binding.textBridge.root.changeConstraints {
      val maxBodyWidth = if (hasThumbnail()) {
        thumbnailSize[0]
      } else {
        0
      }

      this.constrainMaxWidth(binding.textBridge.conversationItemBodyWrapper.id, maxBodyWidth)
    }
  }

  private fun presentQuote() {
    val record = requireMediaMessage()
    val quote = record.quote

    if (quote == null) {
      binding.quoteStub.visibility = View.GONE
      return
    }

    val quoteView = binding.quoteStub.get()
    quoteView.setOnClickListener {
      conversationContext.clickListener.onQuoteClicked(record)
    }

    binding.quoteStub.visibility = View.VISIBLE
    quoteView.setQuote(
      conversationContext.glideRequests,
      quote.id,
      Recipient.live(quote.author).get(),
      quote.displayText,
      quote.isOriginalMissing,
      quote.attachment,
      if (conversationMessage.messageRecord.isStoryReaction()) conversationMessage.messageRecord.body else null,
      quote.quoteType
    )

    quoteView.setMessageType(
      when {
        conversationMessage.messageRecord.isStoryReaction() && binding.textBridge.isIncoming -> QuoteView.MessageType.STORY_REPLY_INCOMING
        conversationMessage.messageRecord.isStoryReaction() -> QuoteView.MessageType.STORY_REPLY_OUTGOING
        binding.textBridge.isIncoming -> QuoteView.MessageType.INCOMING
        else -> QuoteView.MessageType.OUTGOING
      }
    )

    quoteView.setWallpaperEnabled(conversationContext.hasWallpaper())
    quoteView.setTextSize(TypedValue.COMPLEX_UNIT_SP, SignalStore.settings().getMessageQuoteFontSize(context).toFloat())

    val isOutgoing = conversationMessage.messageRecord.isOutgoing
    when (shape) {
      V2ConversationItemShape.MessageShape.SINGLE, V2ConversationItemShape.MessageShape.START -> {
        val isGroupThread = conversationMessage.threadRecipient.isGroup
        quoteView.setTopCornerSizes(
          isOutgoing || !isGroupThread,
          isOutgoing || !isGroupThread
        )
      }

      V2ConversationItemShape.MessageShape.MIDDLE, V2ConversationItemShape.MessageShape.END -> {
        quoteView.setTopCornerSizes(isOutgoing, !isOutgoing)
      }
    }
  }

  private fun presentThumbnail() {
    val slideDeck = requireMediaMessage().slideDeck
    if (slideDeck.thumbnailSlides.isEmpty() || slideDeck.thumbnailSlides.size > 1) {
      binding.thumbnailStub.visibility = View.GONE
      thumbnailSize[0] = -1
      thumbnailSize[1] = -1

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

  private fun hasGroupSenderName(): Boolean {
    return binding.textBridge.senderName?.visible == true
  }

  private fun hasThumbnail(): Boolean {
    return binding.thumbnailStub.isVisible
  }

  private fun hasQuote(): Boolean {
    return binding.quoteStub.isVisible
  }

  private fun hasMedia(): Boolean {
    return hasThumbnail() || hasQuote()
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
