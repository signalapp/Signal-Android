/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.preupload

import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.core.models.media.Media
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.mediasend.MediaRecipientId
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
class PreUploadManager(
  context: Context,
  private val callback: Callback
) {

  private val context: Context = context.applicationContext
  private val uploadResults: LinkedHashMap<Media, PreUploadResult> = LinkedHashMap()
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
        cancelUploadInternal(media)
        Log.d(TAG, "Re-uploading media with recipient.")
        uploadMediaInternal(media, recipientId)
      }
    }
  }

  /**
   * Given a map of old->new, cancel medias that were changed and upload their replacements. Will
   * also upload any media in the map that wasn't yet uploaded.
   *
   * @param oldToNew A mapping of prior media objects to their updated equivalents.
   * @param recipientId Optional recipient identifier. Used by the callback to apply recipient-specific behavior.
   */
  fun applyMediaUpdates(oldToNew: Map<Media, Media>, recipientId: MediaRecipientId?) {
    executor.execute {
      for ((oldMedia, newMedia) in oldToNew) {
        val same = oldMedia == newMedia && hasSameTransformProperties(oldMedia, newMedia)

        if (!same || !uploadResults.containsKey(newMedia)) {
          Log.d(TAG, "Canceling existing preuploads.")
          cancelUploadInternal(oldMedia)
          Log.d(TAG, "Applying media updates.")
          uploadMediaInternal(newMedia, recipientId)
        }
      }
    }
  }

  private fun hasSameTransformProperties(oldMedia: Media, newMedia: Media): Boolean {
    val oldProperties = oldMedia.transformProperties
    val newProperties = newMedia.transformProperties

    if (oldProperties == null || newProperties == null) {
      return oldProperties == newProperties
    }

    // Matches legacy behavior: if the new media is "video edited", we treat it as different.
    // Otherwise, we treat it as the same if only the sent quality matches.
    return !newProperties.videoEdited && oldProperties.sentMediaQuality == newProperties.sentMediaQuality
  }

  /**
   * Cancels the pre-upload (if present) for [media] and deletes any associated attachment state.
   *
   * @param media The media item to cancel.
   */
  fun cancelUpload(media: Media) {
    Log.d(TAG, "User canceling media upload.")
    executor.execute { cancelUploadInternal(media) }
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
        cancelUploadInternal(media)
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
      for (media in keysSnapshot) {
        cancelUploadInternal(media)
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
      uploadResults[media] = result
    } else {
      Log.w(TAG, "Failed to upload media with URI: ${media.uri}")
    }
  }

  private fun cancelUploadInternal(media: Media) {
    val result = uploadResults[media] ?: return

    Log.d(TAG, "Canceling attachment upload jobs for ${result.attachmentId}")
    callback.cancelJobs(context, result.jobIds)
    uploadResults.remove(media)
    callback.deleteAttachment(context, result.attachmentId)
  }

  @WorkerThread
  private fun updateCaptionsInternal(updatedMedia: List<Media>) {
    for (updated in updatedMedia) {
      val result = uploadResults[updated]

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
    val orderedUploadResults: LinkedHashMap<Media, PreUploadResult> = LinkedHashMap()

    for ((index, media) in mediaInOrder.withIndex()) {
      val result = uploadResults[media]

      if (result != null) {
        orderMap[result.attachmentId] = index
        orderedUploadResults[media] = result
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

  /**
   * Callbacks that perform the real side-effects (DB ops, job scheduling/cancelation, etc).
   *
   * This keeps `feature/media-send` free of direct dependencies on app-specific systems.
   *
   * Threading: all callback methods are invoked on this manager's serialized background executor
   * thread (i.e., not the main thread).
   */
  interface Callback {
    /**
     * Performs pre-upload side-effects (e.g., create attachment state + enqueue jobs).
     *
     * @param context Application context.
     * @param media The media item being pre-uploaded.
     * @param recipientId Optional recipient identifier, if known.
     * @return A [PreUploadResult] if enqueued, or `null` if it failed or should not pre-upload.
     */
    @WorkerThread
    fun preUpload(context: Context, media: Media, recipientId: MediaRecipientId?): PreUploadResult?

    /**
     * Cancels any scheduled/running work for the provided job ids.
     *
     * @param context Application context.
     * @param jobIds Job identifiers to cancel.
     */
    @WorkerThread
    fun cancelJobs(context: Context, jobIds: List<String>)

    /**
     * Deletes any persisted attachment state for [attachmentId].
     *
     * @param context Application context.
     * @param attachmentId Attachment identifier to delete.
     */
    @WorkerThread
    fun deleteAttachment(context: Context, attachmentId: Long)

    /**
     * Updates the caption for [attachmentId].
     *
     * @param context Application context.
     * @param attachmentId Attachment identifier.
     * @param caption New caption (or `null` to clear).
     */
    @WorkerThread
    fun updateAttachmentCaption(context: Context, attachmentId: Long, caption: String?)

    /**
     * Updates display order for attachments.
     *
     * @param context Application context.
     * @param orderMap Map of attachment id -> display order index.
     */
    @WorkerThread
    fun updateDisplayOrder(context: Context, orderMap: Map<Long, Int>)

    /**
     * Deletes any pre-uploaded attachments that are no longer referenced.
     *
     * @param context Application context.
     * @return The number of attachments deleted.
     */
    @WorkerThread
    fun deleteAbandonedPreuploadedAttachments(context: Context): Int
  }

  private companion object {
    private val TAG = Log.tag(PreUploadManager::class.java)
  }
}
