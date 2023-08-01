/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.signal.core.util.StringUtil
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.conversation.BodyBubbleLayoutTransition
import org.thoughtcrime.securesms.conversation.ConversationItemDisplayMode
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.mutiselect.Multiselect
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.conversation.mutiselect.Multiselectable
import org.thoughtcrime.securesms.conversation.v2.data.ConversationMessageElement
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.InterceptableLongClickCopyLinkSpan
import org.thoughtcrime.securesms.util.LongClickMovementMethod
import org.thoughtcrime.securesms.util.PlaceholderURLSpan
import org.thoughtcrime.securesms.util.Projection
import org.thoughtcrime.securesms.util.ProjectionList
import org.thoughtcrime.securesms.util.SearchUtil
import org.thoughtcrime.securesms.util.SearchUtil.StyleFactory
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import org.thoughtcrime.securesms.util.ThemeUtil
import org.thoughtcrime.securesms.util.VibrateUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.hasExtraText
import org.thoughtcrime.securesms.util.hasNoBubble
import org.thoughtcrime.securesms.util.isScheduled
import org.thoughtcrime.securesms.util.visible
import java.util.Locale

/**
 * Base ViewHolder to share some common properties shared among conversation items.
 */
abstract class V2BaseViewHolder<Model : MappingModel<Model>>(
  root: V2ConversationItemLayout,
  appearanceInfoProvider: V2ConversationContext
) : MappingViewHolder<Model>(root) {
  protected val shapeDelegate = V2ConversationItemShape(appearanceInfoProvider)
  protected val themeDelegate = V2ConversationItemTheme(context, appearanceInfoProvider)
}

/**
 * Represents a text-only conversation item.
 */
class V2TextOnlyViewHolder<Model : MappingModel<Model>>(
  private val binding: V2ConversationItemTextOnlyBindingBridge,
  private val conversationContext: V2ConversationContext
) : V2BaseViewHolder<Model>(binding.root, conversationContext), Multiselectable, InteractiveConversationElement {

  companion object {
    private val STYLE_FACTORY = StyleFactory { arrayOf<CharacterStyle>(BackgroundColorSpan(Color.YELLOW), ForegroundColorSpan(Color.BLACK)) }
    private const val CONDENSED_MODE_MAX_LINES = 3
  }

  private var messageId: Long = Long.MAX_VALUE

  private val projections = ProjectionList()
  private val footerDelegate = V2FooterPositionDelegate(binding)

  private val conversationItemFooterBackgroundCorners = Projection.Corners(18f.dp)
  private val conversationItemFooterBackground = MaterialShapeDrawable(
    ShapeAppearanceModel.Builder()
      .setAllCornerSizes(18f.dp)
      .build()
  )

  override lateinit var conversationMessage: ConversationMessage

  override val root: ViewGroup = binding.root
  override val bubbleView: View = binding.conversationItemBodyWrapper

  override val bubbleViews: List<View> = listOfNotNull(
    binding.conversationItemBodyWrapper,
    binding.conversationItemFooterDate,
    binding.conversationItemFooterExpiry,
    binding.conversationItemDeliveryStatus,
    binding.conversationItemFooterBackground
  )

  override val reactionsView: View = binding.conversationItemReactions
  override val quotedIndicatorView: View? = null
  override val replyView: View = binding.conversationItemReply
  override val contactPhotoHolderView: View? = binding.senderPhoto
  override val badgeImageView: View? = binding.senderBadge

  private var reactionMeasureListener: ReactionMeasureListener = ReactionMeasureListener()

  init {
    binding.root.addOnMeasureListener(footerDelegate)

    binding.conversationItemReactions.setOnClickListener {
      conversationContext.clickListener
        .onReactionClicked(
          Multiselect.getParts(conversationMessage).asSingle().singlePart,
          conversationMessage.messageRecord.id,
          conversationMessage.messageRecord.isMms
        )
    }

    binding.root.setOnClickListener {
      conversationContext.clickListener.onItemClick(getMultiselectPartForLatestTouch())
    }

    binding.root.setOnLongClickListener {
      conversationContext.clickListener.onItemLongClick(binding.root, getMultiselectPartForLatestTouch())

      true
    }

    binding.conversationItemBody.isClickable = false
    binding.conversationItemBody.isFocusable = false
    binding.conversationItemBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, SignalStore.settings().messageFontSize.toFloat())
    binding.conversationItemBody.movementMethod = LongClickMovementMethod.getInstance(context)

    if (binding.isIncoming) {
      binding.conversationItemBody.setMentionBackgroundTint(ContextCompat.getColor(context, if (ThemeUtil.isDarkTheme(context)) R.color.core_grey_60 else R.color.core_grey_20))
    } else {
      binding.conversationItemBody.setMentionBackgroundTint(ContextCompat.getColor(context, R.color.transparent_black_25))
    }

    binding.conversationItemBodyWrapper.layoutTransition = BodyBubbleLayoutTransition()
  }

  override fun bind(model: Model) {
    check(model is ConversationMessageElement)
    conversationMessage = model.conversationMessage

    val shape = shapeDelegate.setMessageShape(
      isLtr = itemView.layoutDirection == View.LAYOUT_DIRECTION_LTR,
      currentMessage = conversationMessage.messageRecord,
      isGroupThread = conversationMessage.threadRecipient.isGroup,
      adapterPosition = bindingAdapterPosition
    )

    shapeDelegate.bodyBubble.fillColor = themeDelegate.getBodyBubbleColor(conversationMessage)

    binding.conversationItemBodyWrapper.background = shapeDelegate.bodyBubble
    binding.conversationItemReply.setBackgroundColor(themeDelegate.getReplyIconBackgroundColor())

    presentBody()
    presentDate(shape)
    presentDeliveryStatus(shape)
    presentFooterBackground(shape)
    presentFooterExpiry(shape)
    presentAlert()
    presentSender()
    presentReactions()

    itemView.updateLayoutParams<MarginLayoutParams> {
      topMargin = shape.topPadding.toInt()
      bottomMargin = shape.bottomPadding.toInt()
    }
  }

  override fun getAdapterPosition(recyclerView: RecyclerView): Int = bindingAdapterPosition

  override fun getSnapshotProjections(coordinateRoot: ViewGroup, clipOutMedia: Boolean): ProjectionList {
    return getSnapshotProjections(coordinateRoot, clipOutMedia, true)
  }

  override fun getSnapshotProjections(coordinateRoot: ViewGroup, clipOutMedia: Boolean, outgoingOnly: Boolean): ProjectionList {
    projections.clear()

    if (outgoingOnly && binding.isIncoming) {
      return projections
    }

    projections.add(
      Projection.relativeToParent(
        coordinateRoot,
        binding.conversationItemBodyWrapper,
        Projection.Corners.NONE
      ).translateX(binding.conversationItemBodyWrapper.translationX).translateY(root.translationY)
    )

    return projections
  }

  override fun getColorizerProjections(coordinateRoot: ViewGroup): ProjectionList {
    projections.clear()

    if (conversationMessage.messageRecord.isOutgoing) {
      if (!conversationMessage.messageRecord.hasNoBubble(context)) {
        projections.add(
          Projection.relativeToParent(
            coordinateRoot,
            binding.conversationItemBodyWrapper,
            shapeDelegate.corners
          ).translateX(binding.conversationItemBodyWrapper.translationX).translateY(root.translationY)
        )
      } else if (conversationContext.hasWallpaper()) {
        projections.add(
          Projection.relativeToParent(
            coordinateRoot,
            binding.conversationItemFooterBackground,
            conversationItemFooterBackgroundCorners
          ).translateX(binding.conversationItemFooterBackground.translationX).translateY(root.translationY)
        )
      }
    }

    return projections
  }

  override fun getTopBoundaryOfMultiselectPart(multiselectPart: MultiselectPart): Int {
    return root.top
  }

  override fun getBottomBoundaryOfMultiselectPart(multiselectPart: MultiselectPart): Int {
    return root.bottom
  }

  override fun getMultiselectPartForLatestTouch(): MultiselectPart {
    return conversationMessage.multiselectCollection.asSingle().singlePart
  }

  override fun getHorizontalTranslationTarget(): View? {
    return if (conversationMessage.messageRecord.isOutgoing) {
      null
    } else if (conversationMessage.threadRecipient.isGroup) {
      binding.senderPhoto
    } else {
      binding.conversationItemBodyWrapper
    }
  }

  override fun hasNonSelectableMedia(): Boolean = false
  override fun showProjectionArea() = Unit

  override fun hideProjectionArea() = Unit

  override fun getGiphyMp4PlayableProjection(coordinateRoot: ViewGroup): Projection {
    return Projection
      .relativeToParent(
        coordinateRoot,
        binding.conversationItemBodyWrapper,
        shapeDelegate.corners
      )
      .translateY(root.translationY)
      .translateX(binding.conversationItemBodyWrapper.translationX)
      .translateX(root.translationX)
  }

  override fun canPlayContent(): Boolean = false

  override fun shouldProjectContent(): Boolean = false

  private fun MessageRecord.buildMessageId(): Long {
    return if (isMms) -id else id
  }

  private fun presentBody() {
    binding.conversationItemBody.setTextColor(themeDelegate.getBodyTextColor(conversationMessage))
    binding.conversationItemBody.setLinkTextColor(themeDelegate.getBodyTextColor(conversationMessage))

    val record = conversationMessage.messageRecord
    var styledText: Spannable = conversationMessage.getDisplayBody(context)
    if (conversationContext.isMessageRequestAccepted) {
      linkifyMessageBody(styledText)
    }

    styledText = SearchUtil.getHighlightedSpan(Locale.getDefault(), STYLE_FACTORY, styledText, conversationContext.searchQuery, SearchUtil.STRICT)
    if (record.hasExtraText()) {
      binding.conversationItemBody.setOverflowText(getLongMessageSpan())
    } else {
      binding.conversationItemBody.setOverflowText(null)
    }

    if (isContentCondensed()) {
      binding.conversationItemBody.maxLines = CONDENSED_MODE_MAX_LINES
    } else {
      binding.conversationItemBody.maxLines = Integer.MAX_VALUE
    }

    binding.conversationItemBody.text = StringUtil.trim(styledText)
  }

  private fun linkifyMessageBody(messageBody: Spannable) {
    V2ConversationBodyUtil.linkifyUrlLinks(messageBody, conversationContext.selectedItems.isEmpty(), conversationContext.clickListener::onUrlClicked)

    if (conversationMessage.hasStyleLinks()) {
      messageBody.getSpans(0, messageBody.length, PlaceholderURLSpan::class.java).forEach { placeholder ->
        val start = messageBody.getSpanStart(placeholder)
        val end = messageBody.getSpanEnd(placeholder)
        val span: URLSpan = InterceptableLongClickCopyLinkSpan(
          placeholder.value,
          conversationContext.clickListener::onUrlClicked,
          ContextCompat.getColor(getContext(), R.color.signal_accent_primary),
          false
        )

        messageBody.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
    }

    MentionAnnotation.getMentionAnnotations(messageBody).forEach { annotation ->
      messageBody.setSpan(
        MentionClickableSpan(RecipientId.from(annotation.value)),
        messageBody.getSpanStart(annotation),
        messageBody.getSpanEnd(annotation),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
      )
    }
  }

  private fun getLongMessageSpan(): CharSequence {
    val message = context.getString(R.string.ConversationItem_read_more)
    val span = SpannableStringBuilder(message)

    span.setSpan(ReadMoreSpan(), 0, span.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
    return span
  }

  private inner class MentionClickableSpan(
    private val recipientId: RecipientId
  ) : ClickableSpan() {
    override fun onClick(widget: View) {
      VibrateUtil.vibrateTick(context)
      conversationContext.clickListener.onGroupMemberClicked(recipientId, conversationMessage.threadRecipient.requireGroupId())
    }

    override fun updateDrawState(ds: TextPaint) = Unit
  }

  private inner class ReadMoreSpan : ClickableSpan() {
    override fun onClick(widget: View) {
      if (conversationContext.selectedItems.isEmpty()) {
        conversationContext.clickListener.onMoreTextClicked(
          conversationMessage.threadRecipient.id,
          conversationMessage.messageRecord.id,
          conversationMessage.messageRecord.isMms
        )
      }
    }

    override fun updateDrawState(ds: TextPaint) {
      ds.typeface = Typeface.DEFAULT_BOLD
    }
  }

  private fun isContentCondensed(): Boolean {
    return conversationContext.displayMode == ConversationItemDisplayMode.CONDENSED && conversationContext.getPreviousMessage(bindingAdapterPosition) == null
  }

  private fun presentFooterExpiry(shape: V2ConversationItemShape.MessageShape) {
    if (shape == V2ConversationItemShape.MessageShape.MIDDLE || shape == V2ConversationItemShape.MessageShape.START) {
      binding.conversationItemFooterExpiry.stopAnimation()
      binding.conversationItemFooterExpiry.visible = false
      return
    }

    binding.conversationItemFooterExpiry.setColorFilter(themeDelegate.getFooterIconColor(conversationMessage))

    val timer = binding.conversationItemFooterExpiry
    val record = conversationMessage.messageRecord
    if (record.expiresIn > 0 && !record.isPending) {
      binding.conversationItemFooterExpiry.visible = true
      binding.conversationItemFooterExpiry.setPercentComplete(0f)

      if (record.expireStarted > 0) {
        timer.setExpirationTime(record.expireStarted, record.expiresIn)
        timer.startAnimation()

        if (record.expireStarted + record.expiresIn <= System.currentTimeMillis()) {
          ApplicationDependencies.getExpiringMessageManager().checkSchedule()
        }
      } else if (!record.isOutgoing && !record.isMediaPending) {
        conversationContext.onStartExpirationTimeout(record)
      }
    } else {
      timer.visible = false
    }
  }

  private fun presentSender() {
    if (binding.senderName == null || binding.senderPhoto == null || binding.senderBadge == null) {
      return
    }

    if (conversationMessage.threadRecipient.isGroup) {
      val sender = conversationMessage.messageRecord.fromRecipient
      binding.senderName.visible = true
      binding.senderPhoto.visible = true
      binding.senderBadge.visible = true

      binding.senderName.text = sender.getDisplayName(context)
      binding.senderName.setTextColor(conversationContext.getColorizer().getIncomingGroupSenderColor(context, sender))
      binding.senderPhoto.setAvatar(sender)
      binding.senderBadge.setBadgeFromRecipient(sender)
    } else {
      binding.senderName.visible = false
      binding.senderPhoto.visible = false
      binding.senderBadge.visible = false
    }
  }

  private fun presentAlert() {
    val record = conversationMessage.messageRecord
    binding.conversationItemBody.setCompoundDrawablesWithIntrinsicBounds(
      0,
      0,
      if (record.isKeyExchange) R.drawable.ic_menu_login else 0,
      0
    )

    val alert = binding.conversationItemAlert ?: return

    when {
      record.isFailed -> alert.setFailed()
      record.isPendingInsecureSmsFallback -> alert.setPendingApproval()
      record.isRateLimited -> alert.setRateLimited()
      else -> alert.setNone()
    }

    if (conversationContext.hasWallpaper()) {
      alert.setBackgroundResource(R.drawable.wallpaper_message_decoration_background)
    } else {
      alert.background = null
    }
  }

  private fun presentReactions() {
    if (conversationMessage.messageRecord.reactions.isEmpty()) {
      binding.conversationItemReactions.clear()
      binding.root.removeOnMeasureListener(reactionMeasureListener)
    } else {
      reactionMeasureListener.onPostMeasure()
      binding.root.addOnMeasureListener(reactionMeasureListener)
    }
  }

  private fun presentFooterBackground(shape: V2ConversationItemShape.MessageShape) {
    if (!binding.conversationItemBody.isJumbomoji ||
      !conversationContext.hasWallpaper() ||
      shape == V2ConversationItemShape.MessageShape.MIDDLE ||
      shape == V2ConversationItemShape.MessageShape.START
    ) {
      binding.conversationItemFooterBackground.visible = false
      return
    }

    binding.conversationItemFooterBackground.visible = true
    binding.conversationItemFooterBackground.background = conversationItemFooterBackground
    conversationItemFooterBackground.fillColor = themeDelegate.getFooterBubbleColor(conversationMessage)
  }

  private fun presentDate(shape: V2ConversationItemShape.MessageShape) {
    if (shape == V2ConversationItemShape.MessageShape.MIDDLE || shape == V2ConversationItemShape.MessageShape.START) {
      binding.conversationItemFooterDate.visible = false
      return
    }

    binding.conversationItemFooterDate.visible = true
    binding.conversationItemFooterDate.setTextColor(themeDelegate.getFooterTextColor(conversationMessage))

    val record = conversationMessage.messageRecord
    if (record.isFailed) {
      val errorMessage = when {
        record.hasFailedWithNetworkFailures() -> R.string.ConversationItem_error_network_not_delivered
        record.toRecipient.isPushGroup && record.isIdentityMismatchFailure -> R.string.ConversationItem_error_partially_not_delivered
        else -> R.string.ConversationItem_error_not_sent_tap_for_details
      }

      binding.conversationItemFooterDate.setText(errorMessage)
    } else if (record.isPendingInsecureSmsFallback) {
      binding.conversationItemFooterDate.setText(R.string.ConversationItem_click_to_approve_unencrypted)
    } else if (record.isRateLimited) {
      binding.conversationItemFooterDate.setText(R.string.ConversationItem_send_paused)
    } else if (record.isScheduled()) {
      binding.conversationItemFooterDate.text = (DateUtils.getOnlyTimeString(getContext(), Locale.getDefault(), (record as MediaMmsMessageRecord).scheduledDate))
    } else {
      var date = DateUtils.getSimpleRelativeTimeSpanString(context, Locale.getDefault(), record.timestamp)
      if (conversationContext.displayMode != ConversationItemDisplayMode.DETAILED && record is MediaMmsMessageRecord && record.isEditMessage()) {
        date = getContext().getString(R.string.ConversationItem_edited_timestamp_footer, date)
      }

      binding.conversationItemFooterDate.text = date
    }
  }

  private fun presentDeliveryStatus(shape: V2ConversationItemShape.MessageShape) {
    val deliveryStatus = binding.conversationItemDeliveryStatus ?: return

    if (shape == V2ConversationItemShape.MessageShape.MIDDLE || shape == V2ConversationItemShape.MessageShape.START) {
      deliveryStatus.setNone()
      return
    }

    val record = conversationMessage.messageRecord
    val newMessageId = record.buildMessageId()

    if (messageId != newMessageId && deliveryStatus.isPending && !record.isPending) {
      if (record.toRecipient.isGroup) {
        SignalLocalMetrics.GroupMessageSend.onUiUpdated(record.id)
      } else {
        SignalLocalMetrics.IndividualMessageSend.onUiUpdated(record.id)
      }
    }

    messageId = newMessageId

    if (!record.isOutgoing || record.isFailed || record.isPendingInsecureSmsFallback || record.isScheduled()) {
      deliveryStatus.setNone()
      return
    }

    val onlyShowSendingStatus = when {
      record.isOutgoing && !record.isRemoteDelete -> false
      record.isRemoteDelete -> true
      else -> false
    }

    if (onlyShowSendingStatus) {
      if (record.isPending) {
        deliveryStatus.setPending()
      } else {
        deliveryStatus.setNone()
      }

      return
    }

    when {
      record.isPending -> deliveryStatus.setPending()
      record.isRemoteRead -> deliveryStatus.setRead()
      record.isDelivered -> deliveryStatus.setDelivered()
      else -> deliveryStatus.setSent()
    }
  }

  override fun disallowSwipe(latestDownX: Float, latestDownY: Float): Boolean {
    return false
  }

  private inner class ReactionMeasureListener : V2ConversationItemLayout.OnMeasureListener {
    override fun onPreMeasure() = Unit

    override fun onPostMeasure(): Boolean {
      return binding.conversationItemReactions.setReactions(conversationMessage.messageRecord.reactions, binding.conversationItemBodyWrapper.width)
    }
  }
}
