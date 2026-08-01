/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import org.signal.core.util.logging.Log
import org.signal.network.NetworkResult
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.io.InputStream

/**
 * What's left of the original archive API after [ArchiveApiV2] took over the endpoints.
 *
 * Backup file upload still lives here because it isn't a chat-connection request at all -- it's a long-running, resumable CDN transfer driven by
 * [PushServiceSocket], with progress reporting and crash-recovery semantics that [org.signal.libsignal.net.RequestResult] doesn't model. Everything else has
 * moved; prefer [ArchiveApiV2] (or [org.signal.network.service.ArchiveService]) for new work.
 */
class ArchiveApi(private val pushServiceSocket: PushServiceSocket) {

  companion object {
    private val TAG = Log.tag(ArchiveApi::class)
  }

  /**
   * Uploads a pre-encrypted backup file, automatically choosing the best upload strategy based on CDN version.
   * For CDN3, uses TUS "Creation With Upload" (single POST). For other CDNs, falls back to the legacy
   * resumable upload flow.
   *
   * If [existingResumeUrl] is provided, the upload resumes using the existing URL (HEAD+PATCH).
   * Otherwise, a new upload is initiated and [onResumeUrlCreated] is called with the resumable URL
   * before the upload begins, allowing callers to persist it for crash recovery.
   */
  fun uploadBackupFile(
    uploadForm: AttachmentUploadForm,
    data: InputStream,
    dataLength: Long,
    checksumSha256: String? = null,
    progressListener: SignalServiceAttachment.ProgressListener? = null,
    existingResumeUrl: String? = null,
    onResumeUrlCreated: ((String) -> Unit)? = null
  ): NetworkResult<Unit> {
    return NetworkResult.fromFetch {
      if (existingResumeUrl != null) {
        Log.i(TAG, "Resuming backup upload via HEAD+PATCH")
        pushServiceSocket.uploadBackupFile(uploadForm, existingResumeUrl, data, dataLength, progressListener)
      } else if (uploadForm.cdn == 3) {
        Log.i(TAG, "Fresh backup upload via creation-with-upload (CDN3)")
        val resumeUrl = uploadForm.signedUploadLocation + "/" + uploadForm.key
        onResumeUrlCreated?.invoke(resumeUrl)
        pushServiceSocket.uploadBackupFile(uploadForm, checksumSha256, data, dataLength, progressListener, null)
      } else {
        Log.i(TAG, "Fresh backup upload via legacy flow (CDN${uploadForm.cdn})")
        val resumeUrl = pushServiceSocket.getResumableUploadUrl(uploadForm, checksumSha256)
        onResumeUrlCreated?.invoke(resumeUrl)
        pushServiceSocket.uploadBackupFile(uploadForm, resumeUrl, data, dataLength, progressListener)
      }
    }
  }
}
