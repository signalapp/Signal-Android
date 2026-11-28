/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import okio.ByteString.Companion.toByteString
import org.junit.Test
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId

class AttachmentUploadResultTest {

  private val key = ByteArray(64) { 1 }
  private val digest = ByteArray(32) { 2 }

  @Test
  fun `a V4 upload becomes a pointer with its cdn key and upload metadata`() {
    val pointer = uploadResult(remoteId = SignalServiceAttachmentRemoteId.V4("cdn-key")).toAttachmentPointer("application/octet-stream")

    assertThat(pointer.cdnNumber).isEqualTo(3)
    assertThat(pointer.cdnKey).isEqualTo("cdn-key")
    assertThat(pointer.cdnId).isNull()
    assertThat(pointer.contentType).isEqualTo("application/octet-stream")
    assertThat(pointer.key).isEqualTo(key.toByteString())
    assertThat(pointer.digest).isEqualTo(digest.toByteString())
    assertThat(pointer.size).isEqualTo(1234)
    assertThat(pointer.blurHash).isEqualTo("blur")
  }

  @Test
  fun `a V2 upload becomes a pointer with its cdn id`() {
    val pointer = uploadResult(remoteId = SignalServiceAttachmentRemoteId.V2(42)).toAttachmentPointer("image/png")

    assertThat(pointer.cdnId).isEqualTo(42L)
    assertThat(pointer.cdnKey).isNull()
  }

  @Test
  fun `unset incremental mac and upload timestamp are left off the pointer`() {
    val pointer = uploadResult(incrementalDigest = null, incrementalDigestChunkSize = 0, uploadTimestamp = 0).toAttachmentPointer("application/octet-stream")

    assertThat(pointer.incrementalMac).isNull()
    assertThat(pointer.chunkSize).isNull()
    assertThat(pointer.uploadTimestamp).isNull()
  }

  @Test
  fun `incremental mac and upload timestamp are carried over when present`() {
    val mac = ByteArray(16) { 3 }
    val pointer = uploadResult(incrementalDigest = mac, incrementalDigestChunkSize = 1024, uploadTimestamp = 1000).toAttachmentPointer("application/octet-stream")

    assertThat(pointer.incrementalMac).isEqualTo(mac.toByteString())
    assertThat(pointer.chunkSize).isEqualTo(1024)
    assertThat(pointer.uploadTimestamp).isEqualTo(1000L)
  }

  private fun uploadResult(
    remoteId: SignalServiceAttachmentRemoteId = SignalServiceAttachmentRemoteId.V4("cdn-key"),
    incrementalDigest: ByteArray? = null,
    incrementalDigestChunkSize: Int = 0,
    uploadTimestamp: Long = 0
  ): AttachmentUploadResult {
    return AttachmentUploadResult(
      remoteId = remoteId,
      cdnNumber = 3,
      key = key,
      digest = digest,
      incrementalDigest = incrementalDigest,
      incrementalDigestChunkSize = incrementalDigestChunkSize,
      dataSize = 1234,
      uploadTimestamp = uploadTimestamp,
      blurHash = "blur",
      audioHash = null
    )
  }
}
