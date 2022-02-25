package org.thoughtcrime.securesms.stories.landing

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.view.AvatarView
import org.thoughtcrime.securesms.components.ThumbnailView
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.dialogs.StoryContextMenu
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible
import java.util.Locale

/**
 * Items displaying a preview and metadata for a story from a user, allowing them to launch into the story viewer.
 */
object StoriesLandingItem {
  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.stories_landing_item))
  }

  class Model(
    val data: StoriesLandingItemData,
    val onRowClick: (Model) -> Unit,
    val onHideStory: (Model) -> Unit,
    val onForwardStory: (Model) -> Unit,
    val onShareStory: (Model) -> Unit,
    val onGoToChat: (Model) -> Unit,
    val onSave: (Model) -> Unit,
    val onDeleteStory: (Model) -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return data.storyRecipient.id == newItem.data.storyRecipient.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return data.storyRecipient.hasSameContent(newItem.data.storyRecipient) &&
        data == newItem.data &&
        super.areContentsTheSame(newItem)
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val avatarView: AvatarView = itemView.findViewById(R.id.avatar)
    private val storyPreview: ThumbnailView = itemView.findViewById<ThumbnailView>(R.id.story).apply {
      isClickable = false
    }
    private val storyMulti: ThumbnailView = itemView.findViewById<ThumbnailView>(R.id.story_multi).apply {
      isClickable = false
    }
    private val sender: TextView = itemView.findViewById(R.id.sender)
    private val date: TextView = itemView.findViewById(R.id.date)
    private val icon: ImageView = itemView.findViewById(R.id.icon)

    override fun bind(model: Model) {
      itemView.setOnClickListener { model.onRowClick(model) }

      if (model.data.storyRecipient.isMyStory) {
        itemView.setOnLongClickListener(null)
        avatarView.displayProfileAvatar(Recipient.self())
      } else {
        itemView.setOnLongClickListener {
          displayContext(model)
          true
        }

        avatarView.displayProfileAvatar(model.data.storyRecipient)
      }

      val record = model.data.primaryStory.messageRecord as MediaMmsMessageRecord

      avatarView.setStoryRingFromState(model.data.storyViewState)
      storyPreview.setImageResource(GlideApp.with(storyPreview), record.slideDeck.thumbnailSlide!!, false, true)

      if (model.data.secondaryStory != null) {
        val secondaryRecord = model.data.secondaryStory.messageRecord as MediaMmsMessageRecord
        storyMulti.setImageResource(GlideApp.with(storyPreview), secondaryRecord.slideDeck.thumbnailSlide!!, false, true)
        storyMulti.visible = true
      } else {
        storyMulti.visible = false
      }

      sender.text = when {
        model.data.storyRecipient.isMyStory -> context.getText(R.string.StoriesLandingFragment__my_stories)
        model.data.storyRecipient.isGroup -> getGroupPresentation(model)
        else -> model.data.storyRecipient.getDisplayName(context)
      }

      date.text = DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), model.data.dateInMilliseconds)
      icon.visible = model.data.hasReplies || model.data.hasRepliesFromSelf
      // TODO [stories] -- Set actual image resource
      icon.setImageDrawable(ColorDrawable(Color.RED))

      listOf(avatarView, storyPreview, storyMulti, sender, date, icon).forEach {
        it.alpha = if (model.data.isHidden) 0.5f else 1f
      }
    }

    private fun getGroupPresentation(model: Model): String {
      return context.getString(
        R.string.StoryViewerPageFragment__s_to_s,
        getIndividualPresentation(model),
        model.data.storyRecipient.getDisplayName(context)
      )
    }

    private fun getIndividualPresentation(model: Model): String {
      return if (model.data.primaryStory.messageRecord.isOutgoing) {
        context.getString(R.string.Recipient_you)
      } else {
        model.data.individualRecipient.getDisplayName(context)
      }
    }

    private fun displayContext(model: Model) {
      itemView.isSelected = true
      StoryContextMenu.show(context, itemView, model) { itemView.isSelected = false }
    }
  }
}
