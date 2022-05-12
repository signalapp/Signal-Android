package org.thoughtcrime.securesms.stories.viewer.reply.group

import android.content.Context
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.FromTextView
import org.thoughtcrime.securesms.components.emoji.EmojiImageView
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.AvatarUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible
import java.util.Locale

object StoryGroupReplyItem {

  private const val NAME_COLOR_CHANGED = 1

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(TextModel::class.java, LayoutFactory(::TextViewHolder, R.layout.stories_group_text_reply_item))
    mappingAdapter.registerFactory(ReactionModel::class.java, LayoutFactory(::ReactionViewHolder, R.layout.stories_group_reaction_reply_item))
    mappingAdapter.registerFactory(RemoteDeleteModel::class.java, LayoutFactory(::RemoteDeleteViewHolder, R.layout.stories_group_remote_delete_item))
  }

  class TextModel(
    override val storyGroupReplyItemData: StoryGroupReplyItemData,
    val text: StoryGroupReplyItemData.ReplyBody.Text,
    @ColorInt val nameColor: Int,
    val onCopyClick: (TextModel) -> Unit,
    val onDeleteClick: (TextModel) -> Unit,
    val onMentionClick: (RecipientId) -> Unit
  ) : PreferenceModel<TextModel>(), DataWrapper {
    override fun areItemsTheSame(newItem: TextModel): Boolean {
      return storyGroupReplyItemData.sender == newItem.storyGroupReplyItemData.sender &&
        storyGroupReplyItemData.sentAtMillis == newItem.storyGroupReplyItemData.sentAtMillis
    }

    override fun areContentsTheSame(newItem: TextModel): Boolean {
      return storyGroupReplyItemData == newItem.storyGroupReplyItemData &&
        storyGroupReplyItemData.sender.hasSameContent(newItem.storyGroupReplyItemData.sender) &&
        nameColor == newItem.nameColor &&
        super.areContentsTheSame(newItem)
    }

    override fun getChangePayload(newItem: TextModel): Any? {
      return if (nameColor != newItem.nameColor &&
        storyGroupReplyItemData == newItem.storyGroupReplyItemData &&
        storyGroupReplyItemData.sender.hasSameContent(newItem.storyGroupReplyItemData.sender) &&
        super.areContentsTheSame(newItem)
      ) {
        NAME_COLOR_CHANGED
      } else {
        null
      }
    }
  }

  class RemoteDeleteModel(
    override val storyGroupReplyItemData: StoryGroupReplyItemData,
    val remoteDelete: StoryGroupReplyItemData.ReplyBody.RemoteDelete,
    val onDeleteClick: (RemoteDeleteModel) -> Unit,
    @ColorInt val nameColor: Int
  ) : MappingModel<RemoteDeleteModel>, DataWrapper {
    override fun areItemsTheSame(newItem: RemoteDeleteModel): Boolean {
      return storyGroupReplyItemData.sender == newItem.storyGroupReplyItemData.sender &&
        storyGroupReplyItemData.sentAtMillis == newItem.storyGroupReplyItemData.sentAtMillis
    }

    override fun areContentsTheSame(newItem: RemoteDeleteModel): Boolean {
      return storyGroupReplyItemData == newItem.storyGroupReplyItemData &&
        storyGroupReplyItemData.sender.hasSameContent(newItem.storyGroupReplyItemData.sender) &&
        nameColor == newItem.nameColor
    }

    override fun getChangePayload(newItem: RemoteDeleteModel): Any? {
      return if (nameColor != newItem.nameColor &&
        storyGroupReplyItemData == newItem.storyGroupReplyItemData &&
        storyGroupReplyItemData.sender.hasSameContent(newItem.storyGroupReplyItemData.sender)
      ) {
        NAME_COLOR_CHANGED
      } else {
        null
      }
    }
  }

  class ReactionModel(
    override val storyGroupReplyItemData: StoryGroupReplyItemData,
    val reaction: StoryGroupReplyItemData.ReplyBody.Reaction,
    @ColorInt val nameColor: Int
  ) : PreferenceModel<ReactionModel>(), DataWrapper {
    override fun areItemsTheSame(newItem: ReactionModel): Boolean {
      return storyGroupReplyItemData.sender == newItem.storyGroupReplyItemData.sender &&
        storyGroupReplyItemData.sentAtMillis == newItem.storyGroupReplyItemData.sentAtMillis
    }

    override fun areContentsTheSame(newItem: ReactionModel): Boolean {
      return storyGroupReplyItemData == newItem.storyGroupReplyItemData &&
        storyGroupReplyItemData.sender.hasSameContent(newItem.storyGroupReplyItemData.sender) &&
        nameColor == newItem.nameColor &&
        super.areContentsTheSame(newItem)
    }

    override fun getChangePayload(newItem: ReactionModel): Any? {
      return if (nameColor != newItem.nameColor &&
        storyGroupReplyItemData == newItem.storyGroupReplyItemData &&
        storyGroupReplyItemData.sender.hasSameContent(newItem.storyGroupReplyItemData.sender) &&
        super.areContentsTheSame(newItem)
      ) {
        NAME_COLOR_CHANGED
      } else {
        null
      }
    }
  }

  interface DataWrapper {
    val storyGroupReplyItemData: StoryGroupReplyItemData
  }

  private abstract class BaseViewHolder<T>(itemView: View) : MappingViewHolder<T>(itemView) {
    protected val avatar: AvatarImageView = itemView.findViewById(R.id.avatar)
    protected val name: FromTextView = itemView.findViewById(R.id.name)
    protected val body: EmojiTextView = itemView.findViewById(R.id.body)
    protected val date: TextView = itemView.findViewById(R.id.viewed_at)
    protected val dateBelow: TextView = itemView.findViewById(R.id.viewed_at_below)

    init {
      body.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (body.lastLineWidth + date.measuredWidth > ViewUtil.dpToPx(242)) {
          date.visible = false
          dateBelow.visible = true
        } else {
          dateBelow.visible = false
          date.visible = true
        }
      }
    }
  }

  private class TextViewHolder(itemView: View) : BaseViewHolder<TextModel>(itemView) {

    override fun bind(model: TextModel) {
      itemView.setOnLongClickListener {
        displayContextMenu(model)
        true
      }

      name.setTextColor(model.nameColor)
      if (payload.contains(NAME_COLOR_CHANGED)) {
        return
      }

      AvatarUtil.loadIconIntoImageView(model.storyGroupReplyItemData.sender, avatar, DimensionUnit.DP.toPixels(28f).toInt())
      name.text = resolveName(context, model.storyGroupReplyItemData.sender)

      body.movementMethod = LinkMovementMethod.getInstance()
      body.text = model.text.message.getDisplayBody(context).apply {
        linkifyBody(model, this)
      }

      date.text = DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), model.storyGroupReplyItemData.sentAtMillis)
      dateBelow.text = DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), model.storyGroupReplyItemData.sentAtMillis)
    }

    private fun displayContextMenu(model: TextModel) {
      itemView.isSelected = true
      SignalContextMenu.Builder(itemView, itemView.rootView as ViewGroup)
        .preferredHorizontalPosition(SignalContextMenu.HorizontalPosition.START)
        .onDismiss { itemView.isSelected = false }
        .show(
          listOf(
            ActionItem(R.drawable.ic_copy_24_solid_tinted, context.getString(R.string.StoryGroupReplyItem__copy)) { model.onCopyClick(model) },
            ActionItem(R.drawable.ic_trash_24_solid_tinted, context.getString(R.string.StoryGroupReplyItem__delete)) { model.onDeleteClick(model) }
          )
        )
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

      override fun updateDrawState(ds: TextPaint) {}
    }
  }

  private class RemoteDeleteViewHolder(itemView: View) : BaseViewHolder<RemoteDeleteModel>(itemView) {

    override fun bind(model: RemoteDeleteModel) {
      itemView.setOnLongClickListener {
        displayContextMenu(model)
        true
      }

      name.setTextColor(model.nameColor)
      if (payload.contains(NAME_COLOR_CHANGED)) {
        return
      }

      AvatarUtil.loadIconIntoImageView(model.storyGroupReplyItemData.sender, avatar, DimensionUnit.DP.toPixels(28f).toInt())
      name.text = resolveName(context, model.storyGroupReplyItemData.sender)

      date.text = DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), model.storyGroupReplyItemData.sentAtMillis)
      dateBelow.text = DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), model.storyGroupReplyItemData.sentAtMillis)
    }

    private fun displayContextMenu(model: RemoteDeleteModel) {
      itemView.isSelected = true
      SignalContextMenu.Builder(itemView, itemView.rootView as ViewGroup)
        .preferredHorizontalPosition(SignalContextMenu.HorizontalPosition.START)
        .onDismiss { itemView.isSelected = false }
        .show(
          listOf(
            ActionItem(R.drawable.ic_trash_24_solid_tinted, context.getString(R.string.StoryGroupReplyItem__delete)) { model.onDeleteClick(model) }
          )
        )
    }
  }

  private class ReactionViewHolder(itemView: View) : MappingViewHolder<ReactionModel>(itemView) {
    private val avatar: AvatarImageView = itemView.findViewById(R.id.avatar)
    private val name: FromTextView = itemView.findViewById(R.id.name)
    private val reaction: EmojiImageView = itemView.findViewById(R.id.reaction)
    private val date: TextView = itemView.findViewById(R.id.viewed_at)

    override fun bind(model: ReactionModel) {
      name.setTextColor(model.nameColor)
      if (payload.contains(NAME_COLOR_CHANGED)) {
        return
      }

      AvatarUtil.loadIconIntoImageView(model.storyGroupReplyItemData.sender, avatar, DimensionUnit.DP.toPixels(28f).toInt())
      name.text = resolveName(context, model.storyGroupReplyItemData.sender)
      reaction.setImageEmoji(model.reaction.emoji)
      date.text = DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), model.storyGroupReplyItemData.sentAtMillis)
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
