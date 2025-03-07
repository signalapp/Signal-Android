package org.thoughtcrime.securesms.mediasend.v2.gallery

import android.animation.ValueAnimator
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.setPadding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.imageview.ShapeableImageView
import org.signal.core.util.DimensionUnit
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaFolder
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible
import java.util.concurrent.TimeUnit

typealias OnMediaFolderClicked = (MediaFolder) -> Unit
typealias OnMediaClicked = (Media, Boolean) -> Unit

private val FILE_VIEW_HOLDER_TAG = Log.tag(MediaGallerySelectableItem.FileViewHolder::class.java)
private const val PAYLOAD_CHECK_CHANGED = 0
private const val PAYLOAD_INDEX_CHANGED = 1

object MediaGallerySelectableItem {

  fun registerAdapter(
    mappingAdapter: MappingAdapter,
    onMediaFolderClicked: OnMediaFolderClicked,
    onMediaClicked: OnMediaClicked,
    isMultiselectEnabled: Boolean
  ) {
    mappingAdapter.registerFactory(FolderModel::class.java, LayoutFactory({ FolderViewHolder(it, onMediaFolderClicked) }, R.layout.v2_media_gallery_folder_item))
    mappingAdapter.registerFactory(FileModel::class.java, LayoutFactory({ FileViewHolder(it, onMediaClicked) }, if (isMultiselectEnabled) R.layout.v2_media_gallery_item else R.layout.v2_media_gallery_item_no_check))
    mappingAdapter.registerFactory(PlaceholderModel::class.java, LayoutFactory({ PlaceholderViewHolder(it) }, R.layout.v2_media_gallery_placeholder_item))
  }

  class PlaceholderViewHolder(itemView: View) : BaseViewHolder<PlaceholderModel>(itemView) {
    override fun bind(model: PlaceholderModel) = Unit
  }

  class PlaceholderModel : MappingModel<PlaceholderModel> {
    override fun areItemsTheSame(newItem: PlaceholderModel): Boolean = true
    override fun areContentsTheSame(newItem: PlaceholderModel): Boolean = true
  }

  class FolderModel(val mediaFolder: MediaFolder) : MappingModel<FolderModel> {
    override fun areItemsTheSame(newItem: FolderModel): Boolean {
      return mediaFolder.bucketId == newItem.mediaFolder.bucketId
    }

    override fun areContentsTheSame(newItem: FolderModel): Boolean {
      return mediaFolder.bucketId == newItem.mediaFolder.bucketId &&
        mediaFolder.thumbnailUri == newItem.mediaFolder.thumbnailUri
    }
  }

  abstract class BaseViewHolder<T : MappingModel<T>>(itemView: View) : MappingViewHolder<T>(itemView) {
    protected val imageView: ShapeableImageView = itemView.findViewById(R.id.media_gallery_image)
    protected val playOverlay: ImageView? = itemView.findViewById(R.id.media_gallery_play_overlay)
    protected val checkView: TextView? = itemView.findViewById(R.id.media_gallery_check)
    protected val title: TextView? = itemView.findViewById(R.id.media_gallery_title)
  }

  class FolderViewHolder(itemView: View, private val onMediaFolderClicked: OnMediaFolderClicked) : BaseViewHolder<FolderModel>(itemView) {
    override fun bind(model: FolderModel) {
      Glide.with(imageView)
        .load(DecryptableStreamUriLoader.DecryptableUri(model.mediaFolder.thumbnailUri))
        .into(imageView)

      playOverlay?.visible = false
      itemView.setOnClickListener { onMediaFolderClicked(model.mediaFolder) }
      title?.text = model.mediaFolder.title
      title?.visible = true
    }
  }

  data class FileModel(val media: Media, val isSelected: Boolean, val selectionOneBasedIndex: Int) : MappingModel<FileModel> {
    override fun areItemsTheSame(newItem: FileModel): Boolean {
      return newItem.media == media
    }

    override fun areContentsTheSame(newItem: FileModel): Boolean {
      return newItem.media == media && isSelected == newItem.isSelected && selectionOneBasedIndex == newItem.selectionOneBasedIndex
    }

    override fun getChangePayload(newItem: FileModel): Any? {
      return when {
        newItem.media != media -> null
        newItem.isSelected != isSelected -> PAYLOAD_CHECK_CHANGED
        newItem.selectionOneBasedIndex != selectionOneBasedIndex -> PAYLOAD_INDEX_CHANGED
        else -> null
      }
    }
  }

  class FileViewHolder(itemView: View, private val onMediaClicked: OnMediaClicked) : BaseViewHolder<FileModel>(itemView) {

    private val selectedPadding = DimensionUnit.DP.toPixels(12f)
    private val selectedRadius = DimensionUnit.DP.toPixels(12f)
    private var animator: ValueAnimator? = null

    override fun bind(model: FileModel) {
      checkView?.visible = model.isSelected
      checkView?.text = "${model.selectionOneBasedIndex}"
      itemView.setOnClickListener { onMediaClicked(model.media, model.isSelected) }
      playOverlay?.visible = MediaUtil.isVideo(model.media.contentType) && !model.media.isVideoGif
      title?.visible = false

      if (PAYLOAD_INDEX_CHANGED in payload) {
        return
      }

      if (PAYLOAD_CHECK_CHANGED in payload) {
        animateCheckState(model.isSelected)
        return
      } else {
        animator?.cancel()
        updateImageView(if (model.isSelected) 1f else 0f)
      }

      Glide.with(imageView)
        .load(DecryptableStreamUriLoader.DecryptableUri(model.media.uri))
        .addListener(ErrorLoggingRequestListener(FILE_VIEW_HOLDER_TAG))
        .into(imageView)
    }

    private fun animateCheckState(isSelected: Boolean) {
      animator?.cancel()

      val start = if (isSelected) 0f else 1f
      val end = if (isSelected) 1f else 0f

      animator = ValueAnimator.ofFloat(start, end).apply {
        duration = TimeUnit.MILLISECONDS.toMillis(100L)
        addUpdateListener { animator ->
          val fraction = animator.animatedValue as Float
          updateImageView(fraction)
        }
        start()
      }
    }

    override fun onDetachedFromWindow() {
      animator?.cancel()
    }

    private fun updateImageView(fraction: Float) {
      val padding = selectedPadding * fraction
      imageView.setPadding(padding.toInt())

      val corners = selectedRadius * fraction
      imageView.shapeAppearanceModel = imageView.shapeAppearanceModel.withCornerSize(corners)
    }
  }

  private class ErrorLoggingRequestListener(private val tag: String) : RequestListener<Drawable> {
    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
      Log.w(tag, "Failed to load media.", e)
      return false
    }

    override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean = false
  }
}
