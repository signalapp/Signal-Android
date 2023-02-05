package org.thoughtcrime.securesms.notifications.v2

import android.content.Context
import android.net.Uri
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.util.BitmapDecodingException
import org.thoughtcrime.securesms.util.ImageCompressionUtil
import org.thoughtcrime.securesms.util.kb
import org.thoughtcrime.securesms.util.mb

/**
 * Creates and caches attachment thumbnails solely for use by Notifications.
 *
 * Handles LRU cache on it's own due to needing to cleanup BlobProvider when oldest element is evicted.
 *
 * Previously the PartProvider was used and it would provide the entire, full-resolution image assets causing
 * some OEMs to ANR during file reading.
 */
object NotificationThumbnails {
  private val TAG = Log.tag(NotificationThumbnails::class.java)

  private const val MAX_CACHE_SIZE = 16
  private val TARGET_SIZE = 128.kb
  private val SUPPORTED_SIZE_THRESHOLD = 1.mb

  private val executor = SignalExecutors.BOUNDED_IO

  private val thumbnailCache = LinkedHashMap<MessageId, CachedThumbnail>(MAX_CACHE_SIZE)

  fun get(context: Context, notificationItem: NotificationItem): NotificationItem.ThumbnailInfo {
    val thumbnailSlide: Slide? = notificationItem.slideDeck?.thumbnailSlide

    if (thumbnailSlide == null || thumbnailSlide.uri == null) {
      return NotificationItem.ThumbnailInfo.NONE
    }

    if (thumbnailSlide.fileSize > SUPPORTED_SIZE_THRESHOLD) {
      Log.i(TAG, "Source attachment too large for notification")
      return NotificationItem.ThumbnailInfo.NONE
    }

    if (thumbnailSlide.fileSize < TARGET_SIZE) {
      return NotificationItem.ThumbnailInfo(thumbnailSlide.publicUri, thumbnailSlide.contentType)
    }

    val messageId = MessageId(notificationItem.id)
    val thumbnail: CachedThumbnail? = synchronized(thumbnailCache) { thumbnailCache[messageId] }

    if (thumbnail != null) {
      return if (thumbnail != CachedThumbnail.PENDING) {
        NotificationItem.ThumbnailInfo(thumbnail.uri, thumbnail.contentType)
      } else {
        NotificationItem.ThumbnailInfo.NONE
      }
    }

    synchronized(thumbnailCache) {
      thumbnailCache[messageId] = CachedThumbnail.PENDING
    }

    executor.execute {
      val uri = thumbnailSlide.uri

      if (uri != null) {
        val result: ImageCompressionUtil.Result? = try {
          ImageCompressionUtil.compressWithinConstraints(
            context,
            thumbnailSlide.contentType,
            DecryptableStreamUriLoader.DecryptableUri(uri),
            1024,
            TARGET_SIZE,
            60
          )
        } catch (e: BitmapDecodingException) {
          Log.i(TAG, "Unable to decode bitmap", e)
          null
        }

        if (result != null) {
          val thumbnailUri = BlobProvider
            .getInstance()
            .forData(result.data)
            .withMimeType(result.mimeType)
            .withFileName(result.hashCode().toString())
            .createForSingleSessionInMemory()

          synchronized(thumbnailCache) {
            if (thumbnailCache.size >= MAX_CACHE_SIZE) {
              thumbnailCache.remove(thumbnailCache.keys.first())?.uri?.let {
                BlobProvider.getInstance().delete(context, it)
              }
            }
            thumbnailCache[messageId] = CachedThumbnail(thumbnailUri, result.mimeType)
          }

          ApplicationDependencies.getMessageNotifier().updateNotification(context, notificationItem.thread)
        } else {
          Log.i(TAG, "Unable to compress attachment thumbnail for $messageId")
        }
      }
    }

    return NotificationItem.ThumbnailInfo.NONE
  }

  fun removeAllExcept(notificationItems: List<NotificationItem>) {
    val currentMessages = notificationItems.map { MessageId(it.id) }
    synchronized(thumbnailCache) {
      thumbnailCache.keys.removeIf { !currentMessages.contains(it) }
    }
  }

  private data class CachedThumbnail(val uri: Uri?, val contentType: String?) {
    companion object {
      val PENDING = CachedThumbnail(null, null)
    }
  }
}
