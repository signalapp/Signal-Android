package org.thoughtcrime.securesms.stories.my

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.stories.StoryTextPostModel
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.DebouncedOnClickListener
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
    val distributionStory: MyStoriesState.DistributionStory,
    val onClick: (Model, View) -> Unit,
    val onSaveClick: (Model) -> Unit,
    val onDeleteClick: (Model) -> Unit,
    val onForwardClick: (Model) -> Unit,
    val onShareClick: (Model) -> Unit,
    val onInfoClick: (Model, View) -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return distributionStory.messageRecord.id == newItem.distributionStory.messageRecord.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return distributionStory == newItem.distributionStory &&
        !hasStatusChange(newItem) &&
        distributionStory.messageRecord.isViewed == newItem.distributionStory.messageRecord.isViewed &&
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

      val oldRecordHasIdentityMismatch = distributionStory.messageRecord.identityKeyMismatches.isNotEmpty()
      val newRecordHasIdentityMismatch = newItem.distributionStory.messageRecord.identityKeyMismatches.isNotEmpty()
      val oldRecordHasNetworkFailures = distributionStory.messageRecord.hasNetworkFailures()
      val newRecordHasNetworkFailures = newItem.distributionStory.messageRecord.hasNetworkFailures()

      return oldRecord.isOutgoing &&
        newRecord.isOutgoing &&
        (
          oldRecord.isPending != newRecord.isPending ||
            oldRecord.isSent != newRecord.isSent ||
            oldRecord.isFailed != newRecord.isFailed ||
            oldRecordHasIdentityMismatch != newRecordHasIdentityMismatch ||
            oldRecordHasNetworkFailures != newRecordHasNetworkFailures
          )
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val downloadTarget: View = itemView.findViewById(R.id.download_touch)
    private val moreTarget: View = itemView.findViewById(R.id.more_touch)
    private val storyPreview: ImageView = itemView.findViewById<ImageView>(R.id.story).apply {
      isClickable = false
    }
    private val storyBlur: ImageView = itemView.findViewById<ImageView>(R.id.story_blur).apply {
      isClickable = false
    }
    private val viewCount: TextView = itemView.findViewById(R.id.view_count)
    private val date: TextView = itemView.findViewById(R.id.date)
    private val errorIndicator: View = itemView.findViewById(R.id.error_indicator)

    override fun bind(model: Model) {
      storyPreview.isClickable = false
      itemView.setOnClickListener(
        DebouncedOnClickListener {
          model.onClick(model, storyPreview)
        }
      )
      downloadTarget.setOnClickListener { model.onSaveClick(model) }
      moreTarget.setOnClickListener { showContextMenu(model) }
      presentDateOrStatus(model)

      if (model.distributionStory.messageRecord.isSent) {
        if (SignalStore.story.viewedReceiptsEnabled) {
          viewCount.text = context.resources.getQuantityString(
            R.plurals.MyStories__d_views,
            model.distributionStory.views,
            model.distributionStory.views
          )
        } else {
          viewCount.setText(R.string.StoryViewerPageFragment__views_off)
        }
      }

      if (STATUS_CHANGE in payload) {
        return
      }

      val record: MmsMessageRecord = model.distributionStory.messageRecord as MmsMessageRecord
      val thumbnail = record.slideDeck.thumbnailSlide?.uri
      val blur = record.slideDeck.thumbnailSlide?.placeholderBlur

      clearGlide()
      storyBlur.visible = blur != null
      if (blur != null) {
        Glide.with(storyBlur).load(blur).into(storyBlur)
      }

      @Suppress("CascadeIf")
      if (record.storyType.isTextStory) {
        storyBlur.visible = false
        val storyTextPostModel = StoryTextPostModel.parseFrom(record)
        Glide.with(storyPreview)
          .load(storyTextPostModel)
          .placeholder(storyTextPostModel.getPlaceholder())
          .centerCrop()
          .dontAnimate()
          .into(storyPreview)
      } else if (thumbnail != null) {
        storyBlur.visible = blur != null
        Glide.with(storyPreview)
          .load(DecryptableStreamUriLoader.DecryptableUri(thumbnail))
          .addListener(HideBlurAfterLoadListener())
          .centerCrop()
          .dontAnimate()
          .into(storyPreview)
      }
    }

    private fun presentDateOrStatus(model: Model) {
      if (model.distributionStory.messageRecord.isPending || model.distributionStory.messageRecord.isMediaPending) {
        errorIndicator.visible = false
        date.visible = false
        viewCount.setText(R.string.StoriesLandingItem__sending)
      } else if (model.distributionStory.messageRecord.isFailed) {
        errorIndicator.visible = true
        date.visible = true
        viewCount.setText(R.string.StoriesLandingItem__send_failed)
        date.setText(R.string.StoriesLandingItem__tap_to_retry)
      } else if (model.distributionStory.messageRecord.isIdentityMismatchFailure) {
        errorIndicator.visible = true
        date.visible = true
        viewCount.setText(R.string.StoriesLandingItem__partially_sent)
        date.setText(R.string.StoriesLandingItem__tap_to_retry)
      } else {
        errorIndicator.visible = false
        date.visible = true
        date.text = DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), model.distributionStory.messageRecord.dateSent)
      }
    }

    private fun showContextMenu(model: Model) {
      SignalContextMenu.Builder(itemView, itemView.rootView as ViewGroup)
        .preferredHorizontalPosition(SignalContextMenu.HorizontalPosition.END)
        .offsetX(DimensionUnit.DP.toPixels(16f).toInt())
        .offsetY(DimensionUnit.DP.toPixels(12f).toInt())
        .show(
          listOf(
            ActionItem(R.drawable.symbol_trash_24, context.getString(R.string.delete)) { model.onDeleteClick(model) },
            ActionItem(R.drawable.symbol_forward_24, context.getString(R.string.MyStories_forward)) { model.onForwardClick(model) },
            ActionItem(R.drawable.symbol_share_android_24, context.getString(R.string.StoriesLandingItem__share)) { model.onShareClick(model) },
            ActionItem(R.drawable.symbol_info_24, context.getString(R.string.StoriesLandingItem__info)) { model.onInfoClick(model, storyPreview) }
          )
        )
    }

    private inner class HideBlurAfterLoadListener : RequestListener<Drawable> {
      override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean = false

      override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
        storyBlur.visible = false
        return false
      }
    }

    private fun clearGlide() {
      Glide.with(storyPreview).clear(storyPreview)
      Glide.with(storyBlur).clear(storyBlur)
    }
  }
}
