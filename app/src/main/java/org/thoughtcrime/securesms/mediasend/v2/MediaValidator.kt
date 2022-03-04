package org.thoughtcrime.securesms.mediasend.v2

import android.content.Context
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mms.MediaConstraints
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.Util

object MediaValidator {

  fun filterMedia(context: Context, media: List<Media>, mediaConstraints: MediaConstraints, maxSelection: Int): FilterResult {
    val filteredMedia = filterForValidMedia(context, media, mediaConstraints)
    val isAllMediaValid = filteredMedia.size == media.size

    var error: FilterError? = null
    if (!isAllMediaValid) {
      error = if (media.all { MediaUtil.isImageOrVideoType(it.mimeType) }) {
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
      truncatedMedia.drop(1).fold(truncatedMedia.first().bucketId.or(Media.ALL_MEDIA_BUCKET_ID)) { acc, m ->
        if (Util.equals(acc, m.bucketId.or(Media.ALL_MEDIA_BUCKET_ID))) {
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

  private fun filterForValidMedia(context: Context, media: List<Media>, mediaConstraints: MediaConstraints): List<Media> {
    return media
      .filter { m -> isSupportedMediaType(m.mimeType) }
      .filter { m ->
        MediaUtil.isImageAndNotGif(m.mimeType) || isValidGif(context, m, mediaConstraints) || isValidVideo(context, m, mediaConstraints)
      }
  }

  private fun isValidGif(context: Context, media: Media, mediaConstraints: MediaConstraints): Boolean {
    return MediaUtil.isGif(media.mimeType) && media.size < mediaConstraints.getGifMaxSize(context)
  }

  private fun isValidVideo(context: Context, media: Media, mediaConstraints: MediaConstraints): Boolean {
    return MediaUtil.isVideoType(media.mimeType) && media.size < mediaConstraints.getUncompressedVideoMaxSize(context)
  }

  private fun isSupportedMediaType(mimeType: String): Boolean {
    return MediaUtil.isGif(mimeType) || MediaUtil.isImageType(mimeType) || MediaUtil.isVideoType(mimeType)
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
  }
}
