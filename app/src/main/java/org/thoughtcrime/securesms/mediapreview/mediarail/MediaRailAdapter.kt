package org.thoughtcrime.securesms.mediapreview.mediarail

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ThumbnailView
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.adapter.StableIdGenerator
import org.thoughtcrime.securesms.util.visible
import java.util.concurrent.atomic.AtomicInteger

/**
 * This is the RecyclerView.Adapter for the row of thumbnails present in the media viewer screen.
 */
class MediaRailAdapter(private val glideRequests: GlideRequests, listener: RailItemListener, imageLoadingListener: ImageLoadingListener) : ListAdapter<Media, MediaRailAdapter.MediaRailViewHolder>(MediaDiffer()) {
  val imageLoadingListener: ImageLoadingListener

  var currentItemPosition: Int = -1

  private val listener: RailItemListener
  private val stableIdGenerator: StableIdGenerator<Media>

  init {
    this.listener = listener
    stableIdGenerator = StableIdGenerator()
    this.imageLoadingListener = imageLoadingListener
    setHasStableIds(true)
  }

  override fun onCreateViewHolder(viewGroup: ViewGroup, type: Int): MediaRailViewHolder {
    return MediaRailViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.mediarail_media_item, viewGroup, false))
  }

  override fun onBindViewHolder(viewHolder: MediaRailViewHolder, i: Int) {
    viewHolder.bind(getItem(i), i == currentItemPosition, glideRequests, listener, imageLoadingListener)
  }

  override fun onViewRecycled(holder: MediaRailViewHolder) {
    holder.recycle()
  }

  override fun getItemId(position: Int): Long {
    return stableIdGenerator.getId(getItem(position))
  }

  class MediaRailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val image: ThumbnailView
    private val outline: View
    private val captionIndicator: View

    init {
      image = itemView.findViewById(R.id.rail_item_image)
      outline = itemView.findViewById(R.id.rail_item_outline)
      captionIndicator = itemView.findViewById(R.id.rail_item_caption)
    }

    fun bind(
      media: Media,
      isCurrentlySelected: Boolean,
      glideRequests: GlideRequests,
      railItemListener: RailItemListener,
      listener: ImageLoadingListener
    ) {
      listener.onRequest()
      image.setImageResource(glideRequests, media.uri, 0, 0, false, listener)
      image.setOnClickListener { railItemListener.onRailItemClicked(media) }
      captionIndicator.visibility = if (media.caption.isPresent) View.VISIBLE else View.GONE
      setSelectedItem(isCurrentlySelected)
    }

    fun recycle() {
      image.setOnClickListener(null)
    }

    fun setSelectedItem(isActive: Boolean) {
      outline.visible = isActive
    }
  }

  fun interface RailItemListener {
    fun onRailItemClicked(media: Media)
  }

  abstract class ImageLoadingListener : RequestListener<Drawable?> {
    private val activeJobs = AtomicInteger()
    fun onRequest() {
      activeJobs.incrementAndGet()
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

  class MediaDiffer : DiffUtil.ItemCallback<Media>() {
    override fun areItemsTheSame(oldItem: Media, newItem: Media): Boolean {
      return oldItem.uri == newItem.uri
    }

    override fun areContentsTheSame(oldItem: Media, newItem: Media): Boolean {
      return oldItem == newItem
    }
  }
}
