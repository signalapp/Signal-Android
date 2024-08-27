package org.thoughtcrime.securesms.mediasend.v2.capture

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import org.signal.core.util.CursorUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaRepository
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.StorageUtil
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.util.LinkedList
import java.util.Optional

private val TAG = Log.tag(MediaCaptureRepository::class.java)

class MediaCaptureRepository(context: Context) {

  private val context: Context = context.applicationContext

  fun getMostRecentItem(callback: (Media?) -> Unit) {
    if (!StorageUtil.canReadAnyFromMediaStore()) {
      Log.w(TAG, "Cannot read from storage.")
      callback(null)
      return
    }

    SignalExecutors.BOUNDED.execute {
      val media: List<Media> = getMediaInBucket(context, Media.ALL_MEDIA_BUCKET_ID, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
      callback(media.firstOrNull())
    }
  }

  fun renderImageToMedia(data: ByteArray, width: Int, height: Int, onMediaRendered: (Media) -> Unit, onFailedToRender: () -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val media: Media? = renderCaptureToMedia(
        dataSupplier = { data },
        getLength = { data.size.toLong() },
        createBlobBuilder = { blobProvider, bytes, _ -> blobProvider.forData(bytes) },
        mimeType = MediaUtil.IMAGE_JPEG,
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
      val uri: Uri = createBlobBuilder(BlobProvider.getInstance(), data, length)
        .withMimeType(mimeType)
        .createForSingleSessionOnDisk(context)

      Media(
        uri,
        mimeType,
        System.currentTimeMillis(),
        width,
        height,
        length,
        0,
        false,
        false,
        Optional.of(Media.ALL_MEDIA_BUCKET_ID),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
      )
    } catch (e: IOException) {
      return null
    }
  }

  companion object {

    @SuppressLint("VisibleForTests")
    @WorkerThread
    private fun getMediaInBucket(context: Context, bucketId: String, contentUri: Uri, isImage: Boolean): List<Media> {
      val media: MutableList<Media> = LinkedList()
      var selection: String? = MediaStore.Images.Media.BUCKET_ID + " = ? AND " + isNotPending()
      var selectionArgs: Array<String>? = arrayOf(bucketId)
      val sortBy = MediaStore.Images.Media.DATE_MODIFIED + " DESC"

      val projection: Array<String> = if (isImage) {
        arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.ORIENTATION, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT, MediaStore.Images.Media.SIZE)
      } else {
        arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT, MediaStore.Images.Media.SIZE, MediaStore.Video.Media.DURATION)
      }

      if (Media.ALL_MEDIA_BUCKET_ID == bucketId) {
        selection = isNotPending()
        selectionArgs = null
      }

      context.contentResolver.query(contentUri, projection, selection, selectionArgs, sortBy).use { cursor ->
        while (cursor != null && cursor.moveToNext()) {
          val rowId = CursorUtil.requireLong(cursor, projection[0])
          val uri = ContentUris.withAppendedId(contentUri, rowId)
          val mimetype = CursorUtil.requireString(cursor, MediaStore.Images.Media.MIME_TYPE)
          val date = CursorUtil.requireLong(cursor, MediaStore.Images.Media.DATE_MODIFIED)
          val orientation = if (isImage) CursorUtil.requireInt(cursor, MediaStore.Images.Media.ORIENTATION) else 0
          val width = CursorUtil.requireInt(cursor, getWidthColumn(orientation))
          val height = CursorUtil.requireInt(cursor, getHeightColumn(orientation))
          val size = CursorUtil.requireLong(cursor, MediaStore.Images.Media.SIZE)
          val duration = if (!isImage) CursorUtil.requireInt(cursor, MediaStore.Video.Media.DURATION).toLong() else 0.toLong()
          media.add(
            MediaRepository.fixMimeType(
              context,
              Media(
                uri,
                mimetype,
                date,
                width,
                height,
                size,
                duration,
                false,
                false,
                Optional.of(bucketId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
              )
            )
          )
        }
      }

      return media
    }

    private fun getWidthColumn(orientation: Int): String {
      return if (orientation == 0 || orientation == 180) MediaStore.Images.Media.WIDTH else MediaStore.Images.Media.HEIGHT
    }

    private fun getHeightColumn(orientation: Int): String {
      return if (orientation == 0 || orientation == 180) MediaStore.Images.Media.HEIGHT else MediaStore.Images.Media.WIDTH
    }

    @Suppress("DEPRECATION")
    private fun isNotPending(): String {
      return if (Build.VERSION.SDK_INT <= 28) MediaStore.Images.Media.DATA + " NOT NULL" else MediaStore.MediaColumns.IS_PENDING + " != 1"
    }
  }
}
