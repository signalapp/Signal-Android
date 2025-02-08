/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push.http

import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.internal.util.Util
import java.io.ByteArrayInputStream

class DigestingRequestBodyTest {
  private val attachmentKey = Util.getSecretBytes(64)
  private val attachmentIV = Util.getSecretBytes(16)
  private val input = Util.getSecretBytes(CONTENT_LENGTH)

  private val outputStreamFactory = AttachmentCipherOutputStreamFactory(attachmentKey, attachmentIV)

  @Test
  fun givenSameKeyAndIV_whenIWriteToBuffer_thenIExpectSameDigests() {
    val fromStart = getBody(0)
    val fromMiddle = getBody(CONTENT_LENGTH / 2L)

    Buffer().use { buffer ->
      fromStart.writeTo(buffer)
    }

    Buffer().use { buffer ->
      fromMiddle.writeTo(buffer)
    }

    val fullResult = fromStart.attachmentDigest
    assertNotNull(fullResult)

    val partialResult = fromMiddle.attachmentDigest
    assertNotNull(partialResult)

    assertArrayEquals(fullResult?.digest, partialResult?.digest)
    assertArrayEquals(fullResult?.incrementalDigest, partialResult?.incrementalDigest)
  }

  @Test
  fun givenSameKeyAndIV_whenIWriteToBuffer_thenIExpectSameContents() {
    val fromStart = getBody(0)
    val fromMiddle = getBody(CONTENT_LENGTH / 2L)

    val cipher1: ByteArray

    Buffer().use { buffer ->
      fromStart.writeTo(buffer)
      cipher1 = buffer.readByteArray()
    }

    val cipher2: ByteArray

    Buffer().use { buffer ->
      fromMiddle.writeTo(buffer)
      cipher2 = buffer.readByteArray()
    }

    assertEquals(cipher1.size, TOTAL_LENGTH)
    assertEquals(cipher2.size, TOTAL_LENGTH - (CONTENT_LENGTH / 2))

    cipher2.indices.forEach { i ->
      assertEquals(cipher2[i], cipher1[i + (CONTENT_LENGTH / 2)])
    }
  }

  private fun getBody(contentStart: Long): DigestingRequestBody {
    return DigestingRequestBody(
      inputStream = ByteArrayInputStream(input),
      outputStreamFactory = outputStreamFactory,
      contentType = "application/octet",
      contentLength = CONTENT_LENGTH.toLong(),
      incremental = false,
      progressListener = object : SignalServiceAttachment.ProgressListener {
        override fun onAttachmentProgress(total: Long, progress: Long) {
          // no-op
        }

        override fun shouldCancel() = false
      },
      cancelationSignal = { false },
      contentStart = contentStart
    )
  }

  companion object {
    private const val CONTENT_LENGTH = 70_000
    private val TOTAL_LENGTH = AttachmentCipherStreamUtil.getCiphertextLength(CONTENT_LENGTH.toLong()).toInt()
  }
}
