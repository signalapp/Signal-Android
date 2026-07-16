package org.thoughtcrime.securesms.mediasend.v2.capture

import android.content.Context
import android.net.Uri
import org.signal.core.models.media.Media
import org.signal.core.util.ContentTypeUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.contentproviders.BlobProvider
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException

class MediaCaptureRepository(context: Context) {

  private val context: Context = context.applicationContext

  fun renderImageToMedia(data: ByteArray, width: Int, height: Int, onMediaRendered: (Media) -> Unit, onFailedToRender: () -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val media: Media? = renderCaptureToMedia(
        dataSupplier = { data },
        getLength = { data.size.toLong() },
        createBlobBuilder = { blobProvider, bytes, _ -> blobProvider.forData(bytes) },
        mimeType = ContentTypeUtil.IMAGE_JPEG,
        width = width,
        height = height
      )

      if (media != null) {
        onMediaRendered(media)
      } else {
        onFailedToRender()
      }
    }
  }

  fun renderVideoToMedia(fileDescriptor: FileDescriptor, onMediaRendered: (Media) -> Unit, onFailedToRender: () -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val media: Media? = renderCaptureToMedia(
        dataSupplier = { FileInputStream(fileDescriptor) },
        getLength = { it.channel.size() },
        createBlobBuilder = BlobProvider::forData,
        mimeType = VideoConstants.RECORDED_VIDEO_CONTENT_TYPE,
        width = 0,
        height = 0
      )

      if (media != null) {
        onMediaRendered(media)
      } else {
        onFailedToRender()
      }
    }
  }

  private fun <T> renderCaptureToMedia(
    dataSupplier: () -> T,
    getLength: (T) -> Long,
    createBlobBuilder: (BlobProvider, T, Long) -> BlobProvider.BlobBuilder,
    mimeType: String,
    width: Int,
    height: Int
  ): Media? {
    return try {
      val data: T = dataSupplier()
      val length: Long = getLength(data)
      val uri: Uri = createBlobBuilder(AppDependencies.blobs, data, length)
        .withMimeType(mimeType)
        .createForSingleSessionOnDisk(context)

      Media(
        uri = uri,
        contentType = mimeType,
        date = System.currentTimeMillis(),
        width = width,
        height = height,
        size = length,
        duration = 0,
        isBorderless = false,
        isVideoGif = false,
        bucketId = Media.ALL_MEDIA_BUCKET_ID,
        caption = null,
        transformProperties = null,
        fileName = null
      )
    } catch (e: IOException) {
      return null
    }
  }
}
