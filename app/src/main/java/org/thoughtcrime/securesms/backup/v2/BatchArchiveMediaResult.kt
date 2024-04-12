/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import org.thoughtcrime.securesms.attachments.AttachmentId
import org.whispersystems.signalservice.api.archive.BatchArchiveMediaResponse

/**
 * Result of attempting to batch copy multiple attachments at once with helpers for
 * processing the collection of mini-responses.
 */
data class BatchArchiveMediaResult(
  private val response: BatchArchiveMediaResponse,
  private val mediaIdToAttachmentId: Map<String, AttachmentId>,
  private val attachmentIdToMediaName: Map<AttachmentId, String>
) {
  val successfulResponses: Sequence<BatchArchiveMediaResponse.BatchArchiveMediaItemResponse>
    get() = response
      .responses
      .asSequence()
      .filter { it.status == 200 }

  val sourceNotFoundResponses: Sequence<BatchArchiveMediaResponse.BatchArchiveMediaItemResponse>
    get() = response
      .responses
      .asSequence()
      .filter { it.status == 410 }

  fun mediaIdToAttachmentId(mediaId: String): AttachmentId {
    return mediaIdToAttachmentId[mediaId]!!
  }

  fun attachmentIdToMediaName(attachmentId: AttachmentId): String {
    return attachmentIdToMediaName[attachmentId]!!
  }
}
