/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import androidx.annotation.WorkerThread
import org.signal.core.models.media.Media
import org.signal.core.util.ContentTypeUtil
import org.signal.core.util.Util

object MediaValidator {

  /**
   * Drops anything in [media] that cannot be sent, and reports why.
   *
   * @param canSendToStory Whether a given item is sendable as a story. Only consulted when [isStory] is true, but
   *   required either way: a default would let a story send silently skip story validation.
   */
  @WorkerThread
  fun filterMedia(
    media: List<Media>,
    mediaConstraints: MediaConstraints,
    maxSelection: Int,
    isStory: Boolean,
    canSendToStory: (Media) -> Boolean
  ): FilterResult {
    val filteredMedia = filterForValidMedia(media, mediaConstraints, isStory, canSendToStory)
    val isAllMediaValid = filteredMedia.size == media.size

    var error: FilterError? = null
    if (!isAllMediaValid) {
      val keptUris = filteredMedia.map { it.uri }.toSet()
      val rejected = media.firstOrNull { it.uri !in keptUris }

      error = if (media.all { ContentTypeUtil.isImageOrVideoType(it.contentType) || ContentTypeUtil.isDocumentType(it.contentType) }) {
        FilterError.ItemTooLarge(rejected)
      } else {
        FilterError.ItemInvalidType(rejected)
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
  private fun filterForValidMedia(
    media: List<Media>,
    mediaConstraints: MediaConstraints,
    isStory: Boolean,
    canSendToStory: (Media) -> Boolean
  ): List<Media> {
    return media
      .filter { m -> isSupportedMediaType(m.contentType!!) }
      .filter { m ->
        ContentTypeUtil.isImageAndNotGif(m.contentType!!) || isValidGif(m, mediaConstraints) || isValidVideo(m, mediaConstraints) || isValidDocument(m, mediaConstraints)
      }
      .filter { m ->
        !isStory || canSendToStory(m)
      }
  }

  private fun isValidGif(media: Media, mediaConstraints: MediaConstraints): Boolean {
    return ContentTypeUtil.isGif(media.contentType) && media.size < mediaConstraints.getGifMaxSize()
  }

  private fun isValidVideo(media: Media, mediaConstraints: MediaConstraints): Boolean {
    return ContentTypeUtil.isVideoType(media.contentType) && media.size < mediaConstraints.getUncompressedVideoMaxSize()
  }

  private fun isValidDocument(media: Media, mediaConstraints: MediaConstraints): Boolean {
    return ContentTypeUtil.isDocumentType(media.contentType) && media.size < mediaConstraints.getDocumentMaxSize()
  }

  private fun isSupportedMediaType(mimeType: String): Boolean {
    return ContentTypeUtil.isGif(mimeType) || ContentTypeUtil.isImageType(mimeType) || ContentTypeUtil.isVideoType(mimeType) || ContentTypeUtil.isDocumentType(mimeType)
  }

  data class FilterResult(val filteredMedia: List<Media>, val filterError: FilterError?, val bucketId: String?)

  /**
   * @param media The first item that was rejected, when one can be pinned down. Only the filtering here knows which
   *   items it dropped and why, so it reports the offender rather than leaving callers to re-derive it.
   */
  sealed class FilterError {
    data class ItemTooLarge(val media: Media?) : FilterError()
    data class ItemInvalidType(val media: Media?) : FilterError()
    object TooManyItems : FilterError()
    class NoItems(val cause: FilterError? = null) : FilterError() {
      init {
        require(cause !is NoItems)
      }
    }
    object None : FilterError()
  }
}
