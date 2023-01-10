package org.thoughtcrime.securesms.mediapreview.mediarail

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.target.Target
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ThumbnailView
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible
import java.util.concurrent.atomic.AtomicInteger

/**
 * This is the RecyclerView.Adapter for the row of thumbnails present in the media viewer screen.
 */
class MediaRailAdapter(
  private val glideRequests: GlideRequests,
  private val onRailItemSelected: (Media) -> Unit,
  private val imageLoadingListener: ImageLoadingListener
) : MappingAdapter() {

  init {
    registerFactory(MediaRailItem::class.java, ::MediaRailViewHolder, R.layout.mediarail_media_item)
  }

  override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
    super.onAttachedToRecyclerView(recyclerView)
    recyclerView.itemAnimator = null
  }

  override fun submitList(list: List<MappingModel<*>>?) {
    super.submitList(list)
    if (list?.isEmpty() == true) {
      imageLoadingListener.reset()
    }
  }

  override fun submitList(list: List<MappingModel<*>>?, commitCallback: Runnable?) {
    super.submitList(list, commitCallback)
    if (list?.isEmpty() == true) {
      imageLoadingListener.reset()
    }
  }

  fun findSelectedItemPosition(): Int {
    return indexOfFirst(MediaRailItem::class.java) { it.isSelected }.coerceAtLeast(0)
  }

  data class MediaRailItem(val media: Media, val isSelected: Boolean) : MappingModel<MediaRailItem> {
    override fun areItemsTheSame(newItem: MediaRailItem): Boolean {
      return media.uri == newItem.media.uri
    }

    override fun areContentsTheSame(newItem: MediaRailItem): Boolean {
      return this == newItem
    }
  }

  private inner class MediaRailViewHolder(itemView: View) : MappingViewHolder<MediaRailItem>(itemView) {
    private val image: ThumbnailView
    private val outline: View
    private val captionIndicator: View
    private val overlay: ImageView

    init {
      image = itemView.findViewById(R.id.rail_item_image)
      outline = itemView.findViewById(R.id.rail_item_outline)
      captionIndicator = itemView.findViewById(R.id.rail_item_caption)
      overlay = itemView.findViewById(R.id.rail_item_overlay)
    }

    override fun bind(model: MediaRailItem) {
      image.setImageResource(glideRequests, model.media.uri, 0, 0, false, imageLoadingListener)
      image.setOnClickListener { onRailItemSelected(model.media) }
      captionIndicator.visibility = if (model.media.caption.isPresent) View.VISIBLE else View.GONE

      outline.visible = model.isSelected
      overlay.setImageResource(if (model.isSelected) R.drawable.mediapreview_rail_item_overlay_selected else R.drawable.mediapreview_rail_item_overlay_unselected)
    }
  }

  abstract class ImageLoadingListener : ThumbnailView.ThumbnailRequestListener {
    private val activeJobs = AtomicInteger()
    final override fun onLoadScheduled() {
      activeJobs.incrementAndGet()
    }

    final override fun onLoadCanceled() {
      activeJobs.decrementAndGet()
    }

    final override fun onLoadFailed(e: GlideException?, model: Any, target: Target<Drawable?>, isFirstResource: Boolean): Boolean {
      val count = activeJobs.decrementAndGet()
      if (count == 0) {
        onAllRequestsFinished()
      }
      return false
    }

    final override fun onResourceReady(resource: Drawable?, model: Any, target: Target<Drawable?>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
      val count = activeJobs.decrementAndGet()
      if (count == 0) {
        onAllRequestsFinished()
      }
      return false
    }

    fun reset() {
      activeJobs.set(0)
    }

    abstract fun onAllRequestsFinished()
  }
}
