/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import okio.ByteString.Companion.toByteString
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import org.whispersystems.signalservice.internal.push.AttachmentPointer

/**
 * The result of uploading an attachment. Just the additional metadata related to the upload itself.
 */
class AttachmentUploadResult(
  val remoteId: SignalServiceAttachmentRemoteId,
  val cdnNumber: Int,
  val key: ByteArray,
  val digest: ByteArray,
  val incrementalDigest: ByteArray?,
  val incrementalDigestChunkSize: Int,
  val dataSize: Long,
  val uploadTimestamp: Long,
  val blurHash: String?,
  val audioHash: String?
) {

  /**
   * Builds an [AttachmentPointer] from what the upload knows. Callers add any other attachment metadata (file name, dimensions, flags) themselves.
   */
  fun toAttachmentPointer(contentType: String): AttachmentPointer {
    return AttachmentPointer(
      cdnNumber = cdnNumber,
      cdnKey = (remoteId as? SignalServiceAttachmentRemoteId.V4)?.cdnKey,
      cdnId = (remoteId as? SignalServiceAttachmentRemoteId.V2)?.cdnId,
      contentType = contentType,
      key = key.toByteString(),
      digest = digest.toByteString(),
      size = dataSize.toInt(),
      incrementalMac = incrementalDigest?.toByteString(),
      chunkSize = incrementalDigestChunkSize.takeIf { incrementalDigest != null && it > 0 },
      uploadTimestamp = uploadTimestamp.takeIf { it > 0 },
      blurHash = blurHash
    )
  }
}
