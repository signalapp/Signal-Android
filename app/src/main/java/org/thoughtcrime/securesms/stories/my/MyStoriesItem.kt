package org.thoughtcrime.securesms.stories.my

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ThumbnailView
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.stories.StoryTextPostModel
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible
import java.util.Locale

object MyStoriesItem {

  private const val STATUS_CHANGE = 0

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.stories_my_stories_item))
  }

  class Model(
    val distributionStory: ConversationMessage,
    val onClick: (Model, View) -> Unit,
    val onLongClick: (Model) -> Boolean,
    val onSaveClick: (Model) -> Unit,
    val onDeleteClick: (Model) -> Unit,
    val onForwardClick: (Model) -> Unit,
    val onShareClick: (Model) -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return distributionStory.messageRecord.id == newItem.distributionStory.messageRecord.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return distributionStory == newItem.distributionStory &&
        !hasStatusChange(newItem) &&
        distributionStory.messageRecord.viewedReceiptCount == newItem.distributionStory.messageRecord.viewedReceiptCount &&
        super.areContentsTheSame(newItem)
    }

    override fun getChangePayload(newItem: Model): Any? {
      return if (isSameRecord(newItem) && hasStatusChange(newItem)) {
        STATUS_CHANGE
      } else {
        null
      }
    }

    private fun isSameRecord(newItem: Model): Boolean {
      return distributionStory.messageRecord.id == newItem.distributionStory.messageRecord.id
    }

    private fun hasStatusChange(newItem: Model): Boolean {
      val oldRecord = distributionStory.messageRecord
      val newRecord = newItem.distributionStory.messageRecord

      return oldRecord.isOutgoing &&
        newRecord.isOutgoing &&
        (oldRecord.isPending != newRecord.isPending || oldRecord.isSent != newRecord.isSent || oldRecord.isFailed != newRecord.isFailed)
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val downloadTarget: View = itemView.findViewById(R.id.download_touch)
    private val moreTarget: View = itemView.findViewById(R.id.more_touch)
    private val storyPreview: ThumbnailView = itemView.findViewById(R.id.story)
    private val viewCount: TextView = itemView.findViewById(R.id.view_count)
    private val date: TextView = itemView.findViewById(R.id.date)
    private val errorIndicator: View = itemView.findViewById(R.id.error_indicator)

    override fun bind(model: Model) {
      storyPreview.isClickable = false
      itemView.setOnClickListener { model.onClick(model, storyPreview) }
      itemView.setOnLongClickListener { model.onLongClick(model) }
      downloadTarget.setOnClickListener { model.onSaveClick(model) }
      moreTarget.setOnClickListener { showContextMenu(model) }
      presentDateOrStatus(model)

      viewCount.text = context.resources.getQuantityString(
        R.plurals.MyStories__d_views,
        model.distributionStory.messageRecord.viewedReceiptCount,
        model.distributionStory.messageRecord.viewedReceiptCount
      )

      if (STATUS_CHANGE in payload) {
        return
      }

      val record: MmsMessageRecord = model.distributionStory.messageRecord as MmsMessageRecord
      val thumbnail: Slide? = record.slideDeck.thumbnailSlide

      @Suppress("CascadeIf")
      if (record.storyType.isTextStory) {
        storyPreview.setImageResource(GlideApp.with(storyPreview), StoryTextPostModel.parseFrom(record), 0, 0)
      } else if (thumbnail != null) {
        storyPreview.setImageResource(GlideApp.with(storyPreview), thumbnail, false, true)
      } else {
        storyPreview.clear(GlideApp.with(storyPreview))
      }
    }

    private fun presentDateOrStatus(model: Model) {
      if (model.distributionStory.messageRecord.isPending || model.distributionStory.messageRecord.isMediaPending) {
        errorIndicator.visible = false
        date.setText(R.string.StoriesLandingItem__sending)
      } else if (model.distributionStory.messageRecord.isFailed) {
        errorIndicator.visible = true
        date.text = SpanUtil.color(ContextCompat.getColor(context, R.color.signal_alert_primary), context.getString(R.string.StoriesLandingItem__couldnt_send))
      } else {
        errorIndicator.visible = false
        date.text = DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), model.distributionStory.messageRecord.dateSent)
      }
    }

    private fun showContextMenu(model: Model) {
      SignalContextMenu.Builder(itemView, itemView.rootView as ViewGroup)
        .preferredHorizontalPosition(SignalContextMenu.HorizontalPosition.END)
        .offsetX(DimensionUnit.DP.toPixels(16f).toInt())
        .show(
          listOf(
            ActionItem(R.drawable.ic_delete_24_tinted, context.getString(R.string.delete)) { model.onDeleteClick(model) },
            ActionItem(R.drawable.ic_download_24_tinted, context.getString(R.string.save)) { model.onSaveClick(model) },
            ActionItem(R.drawable.ic_forward_24_tinted, context.getString(R.string.MyStories_forward)) { model.onForwardClick(model) },
            ActionItem(R.drawable.ic_share_24_tinted, context.getString(R.string.StoriesLandingItem__share)) { model.onShareClick(model) }
          )
        )
    }
  }
}
