package org.thoughtcrime.securesms.stories.viewer.reply.group

import android.content.Context
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.updateLayoutParams
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AlertView
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.DeliveryStatusView
import org.thoughtcrime.securesms.components.FromTextView
import org.thoughtcrime.securesms.components.emoji.EmojiImageView
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.AvatarUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.LongClickMovementMethod
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.changeConstraints
import org.thoughtcrime.securesms.util.padding
import org.thoughtcrime.securesms.util.visible
import java.util.Locale

typealias OnCopyClick = (CharSequence) -> Unit
typealias OnDeleteClick = (MessageRecord) -> Unit
typealias OnTapForDetailsClick = (MessageRecord) -> Unit

object StoryGroupReplyItem {

  private const val NAME_COLOR_CHANGED = 1

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(TextModel::class.java, LayoutFactory(::TextViewHolder, R.layout.stories_group_text_reply_item))
    mappingAdapter.registerFactory(ReactionModel::class.java, LayoutFactory(::ReactionViewHolder, R.layout.stories_group_text_reply_item))
    mappingAdapter.registerFactory(RemoteDeleteModel::class.java, LayoutFactory(::RemoteDeleteViewHolder, R.layout.stories_group_text_reply_item))
  }

  sealed class Model<T : Any>(
    val replyBody: ReplyBody,
    @ColorInt val nameColor: Int,
    val onCopyClick: OnCopyClick?,
    val onDeleteClick: OnDeleteClick,
    val onTapForDetailsClick: OnTapForDetailsClick
  ) : MappingModel<T> {

    val messageRecord: MessageRecord = replyBody.messageRecord
    val isPending: Boolean = messageRecord.isPending
    val isFailure: Boolean = messageRecord.isFailed
    val sentAtMillis: Long = replyBody.sentAtMillis

    override fun areItemsTheSame(newItem: T): Boolean {
      val other = newItem as Model<*>
      return replyBody.sender == other.replyBody.sender &&
        replyBody.sentAtMillis == other.replyBody.sentAtMillis
    }

    override fun areContentsTheSame(newItem: T): Boolean {
      val other = newItem as Model<*>
      return areNonPayloadPropertiesTheSame(other) &&
        nameColor == other.nameColor
    }

    override fun getChangePayload(newItem: T): Any? {
      val other = newItem as Model<*>
      return if (nameColor != other.nameColor && areNonPayloadPropertiesTheSame(other)) {
        NAME_COLOR_CHANGED
      } else {
        null
      }
    }

    private fun areNonPayloadPropertiesTheSame(newItem: Model<*>): Boolean {
      return replyBody.hasSameContent(newItem.replyBody) &&
        isPending == newItem.isPending &&
        isFailure == newItem.isFailure &&
        sentAtMillis == newItem.sentAtMillis
    }
  }

  class TextModel(
    val text: ReplyBody.Text,
    val onMentionClick: (RecipientId) -> Unit,
    @ColorInt nameColor: Int,
    onCopyClick: OnCopyClick,
    onDeleteClick: OnDeleteClick,
    onTapForDetailsClick: OnTapForDetailsClick
  ) : Model<TextModel>(
    replyBody = text,
    nameColor = nameColor,
    onCopyClick = onCopyClick,
    onDeleteClick = onDeleteClick,
    onTapForDetailsClick = onTapForDetailsClick
  )

  class ReactionModel(
    val reaction: ReplyBody.Reaction,
    @ColorInt nameColor: Int,
    onCopyClick: OnCopyClick,
    onDeleteClick: OnDeleteClick,
    onTapForDetailsClick: OnTapForDetailsClick
  ) : Model<ReactionModel>(
    replyBody = reaction,
    nameColor = nameColor,
    onCopyClick = onCopyClick,
    onDeleteClick = onDeleteClick,
    onTapForDetailsClick = onTapForDetailsClick
  )

  class RemoteDeleteModel(
    val remoteDelete: ReplyBody.RemoteDelete,
    @ColorInt nameColor: Int,
    onDeleteClick: OnDeleteClick,
    onTapForDetailsClick: OnTapForDetailsClick
  ) : Model<RemoteDeleteModel>(
    replyBody = remoteDelete,
    nameColor = nameColor,
    onCopyClick = null,
    onDeleteClick = onDeleteClick,
    onTapForDetailsClick = onTapForDetailsClick
  )

  private abstract class BaseViewHolder<T : Model<T>>(itemView: View) : MappingViewHolder<T>(itemView) {
    protected val bubble: View = findViewById(R.id.bubble)
    protected val avatar: AvatarImageView = findViewById(R.id.avatar)
    protected val name: FromTextView = findViewById(R.id.name)
    protected val body: EmojiTextView = findViewById(R.id.body)
    protected val date: TextView = findViewById(R.id.viewed_at)
    protected val dateBelow: TextView = findViewById(R.id.viewed_at_below)
    protected val status: DeliveryStatusView = findViewById(R.id.delivery_status)
    protected val alertView: AlertView = findViewById(R.id.alert_view)
    protected val reaction: EmojiImageView = itemView.findViewById(R.id.reaction)

    @CallSuper
    override fun bind(model: T) {
      itemView.setOnLongClickListener {
        displayContextMenu(model)
        true
      }

      name.setTextColor(model.nameColor)
      if (payload.contains(NAME_COLOR_CHANGED)) {
        return
      }

      AvatarUtil.loadIconIntoImageView(model.replyBody.sender, avatar, DimensionUnit.DP.toPixels(28f).toInt())
      name.text = resolveName(context, model.replyBody.sender)

      if (model.isPending) {
        status.setPending()
      } else {
        status.setNone()
      }

      if (model.isFailure) {
        alertView.setFailed()
        itemView.setOnClickListener {
          model.onTapForDetailsClick(model.messageRecord)
        }

        date.setText(R.string.ConversationItem_error_not_sent_tap_for_details)
        dateBelow.setText(R.string.ConversationItem_error_not_sent_tap_for_details)
      } else {
        alertView.setNone()
        itemView.setOnClickListener(null)

        val dateText = DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), model.sentAtMillis)
        date.text = dateText
        dateBelow.text = dateText
      }

      itemView.post {
        if (alertView.visible || body.lastLineWidth + date.measuredWidth > ViewUtil.dpToPx(242)) {
          date.visible = false
          dateBelow.visible = true
        } else {
          dateBelow.visible = false
          date.visible = true
        }
      }
    }

    private fun displayContextMenu(model: Model<*>) {
      itemView.isSelected = true

      val actions = mutableListOf<ActionItem>()
      if (model.onCopyClick != null) {
        actions += ActionItem(R.drawable.symbol_copy_android_24, context.getString(R.string.StoryGroupReplyItem__copy)) {
          val toCopy: CharSequence = when (model) {
            is TextModel -> model.text.message.getDisplayBody(context)
            else -> model.messageRecord.getDisplayBody(context)
          }
          model.onCopyClick.invoke(toCopy)
        }
      }
      actions += ActionItem(R.drawable.symbol_trash_24, context.getString(R.string.StoryGroupReplyItem__delete)) { model.onDeleteClick(model.messageRecord) }

      SignalContextMenu.Builder(itemView, itemView.rootView as ViewGroup)
        .preferredHorizontalPosition(SignalContextMenu.HorizontalPosition.START)
        .onDismiss { itemView.isSelected = false }
        .show(actions)
    }
  }

  private class TextViewHolder(itemView: View) : BaseViewHolder<TextModel>(itemView) {

    override fun bind(model: TextModel) {
      super.bind(model)

      body.movementMethod = LongClickMovementMethod.getInstance()
      body.text = model.text.message.getDisplayBody(context).apply {
        linkifyBody(model, this)
      }
    }

    private fun linkifyBody(model: TextModel, body: Spannable) {
      val mentionAnnotations = MentionAnnotation.getMentionAnnotations(body)
      for (annotation in mentionAnnotations) {
        body.setSpan(MentionClickableSpan(model, RecipientId.from(annotation.value)), body.getSpanStart(annotation), body.getSpanEnd(annotation), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
    }

    private class MentionClickableSpan(
      private val model: TextModel,
      private val mentionedRecipientId: RecipientId
    ) : ClickableSpan() {
      override fun onClick(widget: View) {
        model.onMentionClick(mentionedRecipientId)
      }

      override fun updateDrawState(ds: TextPaint) = Unit
    }
  }

  private class ReactionViewHolder(itemView: View) : BaseViewHolder<ReactionModel>(itemView) {

    init {
      reaction.visible = true
      bubble.visibility = View.INVISIBLE
      itemView.padding(bottom = 0)
      body.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        marginEnd = 0
      }

      (itemView as ConstraintLayout).changeConstraints {
        connect(avatar.id, ConstraintSet.BOTTOM, body.id, ConstraintSet.BOTTOM)
      }
    }

    override fun bind(model: ReactionModel) {
      super.bind(model)
      reaction.setImageEmoji(model.reaction.emoji)
      body.setText(if (model.replyBody.sender.isSelf) R.string.StoryGroupReactionReplyItem__you_reacted_to_the_story else R.string.StoryGroupReactionReplyItem__someone_reacted_to_the_story)
    }
  }

  private class RemoteDeleteViewHolder(itemView: View) : BaseViewHolder<RemoteDeleteModel>(itemView) {
    init {
      bubble.setBackgroundResource(R.drawable.rounded_outline_secondary_18)
      body.setText(R.string.ThreadRecord_this_message_was_deleted)
    }
  }

  private fun resolveName(context: Context, recipient: Recipient): String {
    return if (recipient.isSelf) {
      context.getString(R.string.StoryViewerPageFragment__you)
    } else {
      recipient.getDisplayName(context)
    }
  }
}
