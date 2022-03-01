package org.thoughtcrime.securesms.stories.my

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ThumbnailView
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import java.util.Locale

object MyStoriesItem {

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.stories_my_stories_item))
  }

  class Model(
    val distributionStory: ConversationMessage,
    val onClick: (Model) -> Unit,
    val onSaveClick: (Model) -> Unit,
    val onDeleteClick: (Model) -> Unit,
    val onForwardClick: (Model) -> Unit,
    val onShareClick: (Model) -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return distributionStory.messageRecord.id == newItem.distributionStory.messageRecord.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return distributionStory == newItem.distributionStory && super.areContentsTheSame(newItem)
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val downloadTarget: View = itemView.findViewById(R.id.download_touch)
    private val moreTarget: View = itemView.findViewById(R.id.more_touch)
    private val storyPreview: ThumbnailView = itemView.findViewById(R.id.story)
    private val viewCount: TextView = itemView.findViewById(R.id.view_count)
    private val date: TextView = itemView.findViewById(R.id.date)

    override fun bind(model: Model) {
      itemView.setOnClickListener { model.onClick(model) }
      downloadTarget.setOnClickListener { model.onSaveClick(model) }
      moreTarget.setOnClickListener { showContextMenu(model) }
      viewCount.text = context.resources.getQuantityString(R.plurals.MyStories__d_views, model.distributionStory.messageRecord.readReceiptCount, model.distributionStory.messageRecord.readReceiptCount)
      date.text = DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), model.distributionStory.messageRecord.dateSent)

      val thumbnail = (model.distributionStory.messageRecord as MmsMessageRecord).slideDeck.thumbnailSlide
      if (thumbnail != null) {
        storyPreview.setImageResource(GlideApp.with(itemView), thumbnail, false, true)
      } else {
        storyPreview.clear(GlideApp.with(itemView))
      }
    }

    private fun showContextMenu(model: Model) {
      SignalContextMenu.Builder(itemView, itemView.rootView as ViewGroup)
        .preferredHorizontalPosition(SignalContextMenu.HorizontalPosition.END)
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
