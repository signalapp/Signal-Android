/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.attachment

import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId

/**
 * The result of uploading an attachment. Just the additional metadata related to the upload itself.
 */
class AttachmentUploadResult(
  val remoteId: SignalServiceAttachmentRemoteId,
  val cdnNumber: Int,
  val key: ByteArray,
  val iv: ByteArray,
  val digest: ByteArray,
  val incrementalDigest: ByteArray?,
  val incrementalDigestChunkSize: Int,
  val dataSize: Long,
  val uploadTimestamp: Long
)
