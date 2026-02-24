/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.preupload

import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.core.models.media.Media
import org.signal.mediasend.MediaRecipientId

/**
 * Callbacks that perform the real side-effects (DB ops, job scheduling/cancelation, etc).
 *
 * This keeps `feature/media-send` free of direct dependencies on app-specific systems.
 *
 * Threading: all callback methods are invoked on this manager's serialized background executor
 * thread (i.e., not the main thread).
 */
interface PreUploadRepository {
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
