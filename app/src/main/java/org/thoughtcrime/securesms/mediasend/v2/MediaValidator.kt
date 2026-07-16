package org.thoughtcrime.securesms.mediasend.v2

import androidx.annotation.WorkerThread
import org.signal.core.models.media.Media
import org.signal.core.util.Util
import org.signal.mediasend.MediaConstraints
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.MediaUtil

object MediaValidator {

  @WorkerThread
  fun filterMedia(media: List<Media>, mediaConstraints: MediaConstraints, maxSelection: Int, isStory: Boolean): FilterResult {
    val filteredMedia = filterForValidMedia(media, mediaConstraints, isStory)
    val isAllMediaValid = filteredMedia.size == media.size

    var error: FilterError? = null
    if (!isAllMediaValid) {
      error = if (media.all { MediaUtil.isImageOrVideoType(it.contentType) || MediaUtil.isDocumentType(it.contentType) }) {
        FilterError.ItemTooLarge
      } else {
        FilterError.ItemInvalidType
      }
    }

    if (filteredMedia.size > maxSelection) {
      error = FilterError.TooManyItems
    }

    val truncatedMedia = filteredMedia.take(maxSelection)
    val bucketId = if (truncatedMedia.isNotEmpty()) {
      truncatedMedia.drop(1).fold(truncatedMedia.first().bucketId ?: Media.ALL_MEDIA_BUCKET_ID) { acc, m ->
        if (Util.equals(acc, m.bucketId ?: Media.ALL_MEDIA_BUCKET_ID)) {
          acc
        } else {
          Media.ALL_MEDIA_BUCKET_ID
        }
      }
    } else {
      Media.ALL_MEDIA_BUCKET_ID
    }

    if (truncatedMedia.isEmpty()) {
      error = FilterError.NoItems(error)
    }

    return FilterResult(truncatedMedia, error, bucketId)
  }

  @WorkerThread
  private fun filterForValidMedia(media: List<Media>, mediaConstraints: MediaConstraints, isStory: Boolean): List<Media> {
    return media
      .filter { m -> isSupportedMediaType(m.contentType!!) }
      .filter { m ->
        MediaUtil.isImageAndNotGif(m.contentType!!) || isValidGif(m, mediaConstraints) || isValidVideo(m, mediaConstraints) || isValidDocument(m, mediaConstraints)
      }
      .filter { m ->
        !isStory || Stories.MediaTransform.getSendRequirements(m) != Stories.MediaTransform.SendRequirements.CAN_NOT_SEND
      }
  }

  private fun isValidGif(media: Media, mediaConstraints: MediaConstraints): Boolean {
    return MediaUtil.isGif(media.contentType) && media.size < mediaConstraints.getGifMaxSize()
  }

  private fun isValidVideo(media: Media, mediaConstraints: MediaConstraints): Boolean {
    return MediaUtil.isVideoType(media.contentType) && media.size < mediaConstraints.getUncompressedVideoMaxSize()
  }

  private fun isValidDocument(media: Media, mediaConstraints: MediaConstraints): Boolean {
    return MediaUtil.isDocumentType(media.contentType) && media.size < mediaConstraints.getDocumentMaxSize()
  }

  private fun isSupportedMediaType(mimeType: String): Boolean {
    return MediaUtil.isGif(mimeType) || MediaUtil.isImageType(mimeType) || MediaUtil.isVideoType(mimeType) || MediaUtil.isDocumentType(mimeType)
  }

  data class FilterResult(val filteredMedia: List<Media>, val filterError: FilterError?, val bucketId: String?)

  sealed class FilterError {
    object ItemTooLarge : FilterError()
    object ItemInvalidType : FilterError()
    object TooManyItems : FilterError()
    class NoItems(val cause: FilterError? = null) : FilterError() {
      init {
        require(cause !is NoItems)
      }
    }
    object None : FilterError()
  }
}
