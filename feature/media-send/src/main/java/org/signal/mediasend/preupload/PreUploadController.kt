/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.preupload

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import org.signal.core.models.media.Media
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.mediasend.MediaRecipientId
import org.signal.mediasend.MediaSendDependencies
import java.util.LinkedHashMap
import java.util.concurrent.Executor

/**
 * Manages proactive upload of media during the selection process.
 *
 * Upload/cancel operations are serialized, because they're asynchronous operations that depend on
 * ordered completion.
 *
 * For example, if we begin upload of a [Media] but then immediately cancel it (before it was fully
 * enqueued), we need to wait until we have the job ids to cancel. This class manages everything by
 * using a single-thread executor.
 *
 * This class is stateful.
 */
class PreUploadController {

  private val callback: PreUploadRepository = MediaSendDependencies.preUploadRepository
  private val context: Context = MediaSendDependencies.application

  /**
   * Keyed by URI rather than by [Media] itself: there is one pre-uploaded attachment per item on disk, and everything
   * else about a [Media] changes as it moves through the flow. Editing a caption, trimming a video or filling in a
   * video's dimensions all produce a new, non-equal [Media] for the same attachment.
   */
  private val uploadResults: LinkedHashMap<Uri, PreUploadResult> = LinkedHashMap()
  private val executor: Executor =
    SignalExecutors.newCachedSingleThreadExecutor("signal-PreUpload", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD)

  /**
   * Starts a pre-upload for [media].
   *
   * @param media The media item to pre-upload.
   * @param recipientId Optional recipient identifier. Used by the callback to apply recipient-specific behavior.
   */
  fun startUpload(media: Media, recipientId: MediaRecipientId?) {
    executor.execute { uploadMediaInternal(media, recipientId) }
  }

  /**
   * Starts (or restarts) pre-uploads for [mediaItems].
   *
   * This cancels any existing pre-upload for each item before starting a new one.
   *
   * @param mediaItems The media items to pre-upload.
   * @param recipientId Optional recipient identifier. Used by the callback to apply recipient-specific behavior.
   */
  fun startUpload(mediaItems: Collection<Media>, recipientId: MediaRecipientId?) {
    executor.execute {
      for (media in mediaItems) {
        Log.d(TAG, "Canceling existing preuploads.")
        cancelUploadInternal(media.uri)
        Log.d(TAG, "Re-uploading media with recipient.")
        uploadMediaInternal(media, recipientId)
      }
    }
  }

  /**
   * Cancels the pre-upload (if present) for [media] and deletes any associated attachment state.
   *
   * @param media The media item to cancel.
   */
  fun cancelUpload(media: Media) {
    Log.d(TAG, "User canceling media upload.")
    executor.execute { cancelUploadInternal(media.uri) }
  }

  /**
   * Cancels pre-uploads (if present) for all [mediaItems].
   *
   * @param mediaItems Media items to cancel.
   */
  fun cancelUpload(mediaItems: Collection<Media>) {
    Log.d(TAG, "Canceling uploads.")
    executor.execute {
      for (media in mediaItems) {
        cancelUploadInternal(media.uri)
      }
    }
  }

  /**
   * Cancels all current pre-uploads and clears internal state.
   */
  fun cancelAllUploads() {
    Log.d(TAG, "Canceling all uploads.")
    executor.execute {
      val keysSnapshot = uploadResults.keys.toList()
      for (uri in keysSnapshot) {
        cancelUploadInternal(uri)
      }
    }
  }

  /**
   * Returns the current pre-upload results snapshot.
   *
   * @param callback Invoked with the current set of results (in display/order insertion order).
   */
  fun getPreUploadResults(callback: (Collection<PreUploadResult>) -> Unit) {
    executor.execute { callback(uploadResults.values) }
  }

  /**
   * Updates captions for any pre-uploaded items in [updatedMedia].
   *
   * @param updatedMedia Media items containing the latest caption values.
   */
  fun updateCaptions(updatedMedia: List<Media>) {
    executor.execute { updateCaptionsInternal(updatedMedia) }
  }

  /**
   * Updates display order for pre-uploaded items, using [mediaInOrder] list order.
   *
   * @param mediaInOrder Media items in the desired display order.
   */
  fun updateDisplayOrder(mediaInOrder: List<Media>) {
    executor.execute { updateDisplayOrderInternal(mediaInOrder) }
  }

  /**
   * Deletes abandoned pre-upload attachments via the callback.
   *
   * @return Nothing. The callback controls deletion and returns a count for logging.
   */
  fun deleteAbandonedAttachments() {
    executor.execute {
      val deleted = this.callback.deleteAbandonedPreuploadedAttachments(context)
      Log.i(TAG, "Deleted $deleted abandoned attachments.")
    }
  }

  @WorkerThread
  private fun uploadMediaInternal(media: Media, recipientId: MediaRecipientId?) {
    val result = callback.preUpload(context, media, recipientId)

    if (result != null) {
      uploadResults[media.uri] = result
    } else {
      Log.w(TAG, "Failed to upload media with URI: ${media.uri}")
    }
  }

  private fun cancelUploadInternal(uri: Uri) {
    val result = uploadResults[uri] ?: return

    Log.d(TAG, "Canceling attachment upload jobs for ${result.attachmentId}")
    callback.cancelJobs(context, result.jobIds)
    uploadResults.remove(uri)
    callback.deleteAttachment(context, result.attachmentId)
  }

  @WorkerThread
  private fun updateCaptionsInternal(updatedMedia: List<Media>) {
    for (updated in updatedMedia) {
      val result = uploadResults[updated.uri]

      if (result != null) {
        callback.updateAttachmentCaption(context, result.attachmentId, updated.caption)
      } else {
        Log.w(TAG, "When updating captions, no pre-upload result could be found for media with URI: ${updated.uri}")
      }
    }
  }

  @WorkerThread
  private fun updateDisplayOrderInternal(mediaInOrder: List<Media>) {
    val orderMap: MutableMap<Long, Int> = LinkedHashMap()
    val orderedUploadResults: LinkedHashMap<Uri, PreUploadResult> = LinkedHashMap()

    for ((index, media) in mediaInOrder.withIndex()) {
      val result = uploadResults[media.uri]

      if (result != null) {
        orderMap[result.attachmentId] = index
        orderedUploadResults[media.uri] = result
      } else {
        Log.w(TAG, "When updating display order, no pre-upload result could be found for media with URI: ${media.uri}")
      }
    }

    callback.updateDisplayOrder(context, orderMap)

    if (orderedUploadResults.size == uploadResults.size) {
      uploadResults.clear()
      uploadResults.putAll(orderedUploadResults)
    }
  }

  private companion object {
    private val TAG = Log.tag(PreUploadController::class.java)
  }
}
