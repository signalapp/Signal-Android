package org.thoughtcrime.securesms.mediasend.v2.gallery

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaFolder
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible

typealias OnMediaFolderClicked = (MediaFolder) -> Unit
typealias OnMediaClicked = (Media, Boolean) -> Unit

private val FILE_VIEW_HOLDER_TAG = Log.tag(MediaGallerySelectableItem.FileViewHolder::class.java)

object MediaGallerySelectableItem {

  fun registerAdapter(
    mappingAdapter: MappingAdapter,
    onMediaFolderClicked: OnMediaFolderClicked,
    onMediaClicked: OnMediaClicked,
    isMultiselectEnabled: Boolean
  ) {
    mappingAdapter.registerFactory(FolderModel::class.java, LayoutFactory({ FolderViewHolder(it, onMediaFolderClicked) }, R.layout.v2_media_gallery_folder_item))
    mappingAdapter.registerFactory(FileModel::class.java, LayoutFactory({ FileViewHolder(it, onMediaClicked) }, if (isMultiselectEnabled) R.layout.v2_media_gallery_item else R.layout.v2_media_gallery_item_no_check))
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
    protected val imageView: ImageView = itemView.findViewById(R.id.media_gallery_image)
    protected val playOverlay: ImageView = itemView.findViewById(R.id.media_gallery_play_overlay)
    protected val checkView: ImageView? = itemView.findViewById(R.id.media_gallery_check)
    protected val title: TextView? = itemView.findViewById(R.id.media_gallery_title)

    init {
      (itemView as AspectRatioFrameLayout).setAspectRatio(1f)
    }
  }

  class FolderViewHolder(itemView: View, private val onMediaFolderClicked: OnMediaFolderClicked) : BaseViewHolder<FolderModel>(itemView) {
    override fun bind(model: FolderModel) {
      GlideApp.with(imageView)
        .load(DecryptableStreamUriLoader.DecryptableUri(model.mediaFolder.thumbnailUri))
        .into(imageView)

      playOverlay.visible = false
      itemView.setOnClickListener { onMediaFolderClicked(model.mediaFolder) }
      title?.text = model.mediaFolder.title
      title?.visible = true
    }
  }

  data class FileModel(val media: Media, val isSelected: Boolean) : MappingModel<FileModel> {
    override fun areItemsTheSame(newItem: FileModel): Boolean {
      return newItem.media == media
    }

    override fun areContentsTheSame(newItem: FileModel): Boolean {
      return newItem.media == media && isSelected == newItem.isSelected
    }
  }

  class FileViewHolder(itemView: View, private val onMediaClicked: OnMediaClicked) : BaseViewHolder<FileModel>(itemView) {
    override fun bind(model: FileModel) {
      GlideApp.with(imageView)
        .load(DecryptableStreamUriLoader.DecryptableUri(model.media.uri))
        .addListener(ErrorLoggingRequestListener(FILE_VIEW_HOLDER_TAG))
        .into(imageView)

      checkView?.isSelected = model.isSelected
      playOverlay.visible = MediaUtil.isVideo(model.media.mimeType) && !model.media.isVideoGif
      itemView.setOnClickListener { onMediaClicked(model.media, model.isSelected) }
      title?.visible = false
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
