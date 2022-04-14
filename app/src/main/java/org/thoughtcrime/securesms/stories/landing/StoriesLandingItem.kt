package org.thoughtcrime.securesms.stories.landing

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.view.AvatarView
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.StoryTextPostModel
import org.thoughtcrime.securesms.stories.dialogs.StoryContextMenu
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible
import java.util.Locale

/**
 * Items displaying a preview and metadata for a story from a user, allowing them to launch into the story viewer.
 */
object StoriesLandingItem {

  private const val STATUS_CHANGE = 0

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.stories_landing_item))
  }

  class Model(
    val data: StoriesLandingItemData,
    val onRowClick: (Model, View) -> Unit,
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
        !hasStatusChange(newItem) &&
        (data.sendingCount == newItem.data.sendingCount && data.failureCount == newItem.data.failureCount) &&
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
      return data.primaryStory.messageRecord.id == newItem.data.primaryStory.messageRecord.id
    }

    private fun hasStatusChange(newItem: Model): Boolean {
      val oldRecord = data.primaryStory.messageRecord
      val newRecord = newItem.data.primaryStory.messageRecord

      return oldRecord.isOutgoing &&
        newRecord.isOutgoing &&
        (oldRecord.isPending != newRecord.isPending || oldRecord.isSent != newRecord.isSent || oldRecord.isFailed != newRecord.isFailed)
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val avatarView: AvatarView = itemView.findViewById(R.id.avatar)
    private val badgeView: BadgeImageView = itemView.findViewById(R.id.badge)
    private val storyPreview: ImageView = itemView.findViewById<ImageView>(R.id.story).apply {
      isClickable = false
    }
    private val storyBlur: ImageView = itemView.findViewById<ImageView>(R.id.story_blur).apply {
      isClickable = false
    }
    private val storyOutline: ImageView = itemView.findViewById(R.id.story_outline)
    private val storyMulti: ImageView = itemView.findViewById<ImageView>(R.id.story_multi).apply {
      isClickable = false
    }
    private val sender: TextView = itemView.findViewById(R.id.sender)
    private val date: TextView = itemView.findViewById(R.id.date)
    private val icon: ImageView = itemView.findViewById(R.id.icon)
    private val errorIndicator: View = itemView.findViewById(R.id.error_indicator)

    override fun bind(model: Model) {

      presentDateOrStatus(model)
      setUpClickListeners(model)

      if (payload.contains(STATUS_CHANGE)) {
        return
      }

      if (model.data.storyRecipient.isMyStory) {
        avatarView.displayProfileAvatar(Recipient.self())
        badgeView.setBadgeFromRecipient(Recipient.self())
      } else {
        avatarView.displayProfileAvatar(model.data.storyRecipient)
        badgeView.setBadgeFromRecipient(model.data.storyRecipient)
      }

      val record = model.data.primaryStory.messageRecord as MediaMmsMessageRecord

      avatarView.setStoryRingFromState(model.data.storyViewState)

      val thumbnail = record.slideDeck.thumbnailSlide?.uri
      val blur = record.slideDeck.thumbnailSlide?.placeholderBlur

      clearGlide()
      storyBlur.visible = blur != null
      if (blur != null) {
        GlideApp.with(storyBlur).load(blur).into(storyBlur)
      }

      @Suppress("CascadeIf")
      if (record.storyType.isTextStory) {
        storyBlur.visible = false
        val storyTextPostModel = StoryTextPostModel.parseFrom(record)
        GlideApp.with(storyPreview)
          .load(storyTextPostModel)
          .placeholder(storyTextPostModel.getPlaceholder())
          .centerCrop()
          .dontAnimate()
          .into(storyPreview)
      } else if (thumbnail != null) {
        storyBlur.visible = blur != null
        GlideApp.with(storyPreview)
          .load(DecryptableStreamUriLoader.DecryptableUri(thumbnail))
          .addListener(HideBlurAfterLoadListener())
          .centerCrop()
          .dontAnimate()
          .into(storyPreview)
      }

      if (model.data.secondaryStory != null) {
        val secondaryRecord = model.data.secondaryStory.messageRecord as MediaMmsMessageRecord
        val secondaryThumb = secondaryRecord.slideDeck.thumbnailSlide?.uri
        storyOutline.setBackgroundColor(ContextCompat.getColor(context, R.color.signal_background_primary))

        @Suppress("CascadeIf")
        if (secondaryRecord.storyType.isTextStory) {
          val storyTextPostModel = StoryTextPostModel.parseFrom(secondaryRecord)
          GlideApp.with(storyMulti)
            .load(storyTextPostModel)
            .placeholder(storyTextPostModel.getPlaceholder())
            .centerCrop()
            .dontAnimate()
            .into(storyMulti)
          storyMulti.visible = true
        } else if (secondaryThumb != null) {
          GlideApp.with(storyMulti)
            .load(DecryptableStreamUriLoader.DecryptableUri(secondaryThumb))
            .centerCrop()
            .dontAnimate()
            .into(storyMulti)
          storyMulti.visible = true
        } else {
          storyOutline.setBackgroundColor(Color.TRANSPARENT)
          GlideApp.with(storyMulti).clear(storyMulti)
          storyMulti.visible = false
        }
      } else {
        storyOutline.setBackgroundColor(Color.TRANSPARENT)
        GlideApp.with(storyMulti).clear(storyMulti)
        storyMulti.visible = false
      }

      sender.text = when {
        model.data.storyRecipient.isMyStory -> context.getText(R.string.StoriesLandingFragment__my_stories)
        model.data.storyRecipient.isGroup -> getGroupPresentation(model)
        else -> model.data.storyRecipient.getDisplayName(context)
      }

      icon.visible = model.data.hasReplies || model.data.hasRepliesFromSelf
      icon.setImageResource(
        when {
          model.data.hasReplies -> R.drawable.ic_messages_solid_20
          else -> R.drawable.ic_reply_24_solid_tinted
        }
      )

      listOf(avatarView, storyPreview, storyMulti, sender, date, icon).forEach {
        it.alpha = if (model.data.isHidden) 0.5f else 1f
      }
    }

    private fun presentDateOrStatus(model: Model) {
      if (model.data.sendingCount > 0 || (model.data.primaryStory.messageRecord.isOutgoing && (model.data.primaryStory.messageRecord.isPending || model.data.primaryStory.messageRecord.isMediaPending))) {
        errorIndicator.visible = model.data.failureCount > 0L
        if (model.data.sendingCount > 1) {
          date.text = context.getString(R.string.StoriesLandingItem__sending_d, model.data.sendingCount)
        } else {
          date.setText(R.string.StoriesLandingItem__sending)
        }
      } else if (model.data.failureCount > 0 || (model.data.primaryStory.messageRecord.isOutgoing && model.data.primaryStory.messageRecord.isFailed)) {
        errorIndicator.visible = true
        date.text = SpanUtil.color(ContextCompat.getColor(context, R.color.signal_alert_primary), context.getString(R.string.StoriesLandingItem__send_failed))
      } else {
        errorIndicator.visible = false
        date.text = DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), model.data.dateInMilliseconds)
      }
    }

    private fun setUpClickListeners(model: Model) {
      itemView.setOnClickListener {
        model.onRowClick(model, storyPreview)
      }

      if (model.data.storyRecipient.isMyStory) {
        itemView.setOnLongClickListener(null)
      } else {
        itemView.setOnLongClickListener {
          displayContext(model)
          true
        }
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

    private fun clearGlide() {
      GlideApp.with(storyPreview).clear(storyPreview)
      GlideApp.with(storyBlur).clear(storyBlur)
    }

    private inner class HideBlurAfterLoadListener : RequestListener<Drawable> {
      override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean = false

      override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
        storyBlur.visible = false
        return false
      }
    }
  }
}
