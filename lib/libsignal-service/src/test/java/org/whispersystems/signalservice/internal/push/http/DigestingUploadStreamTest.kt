/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push.http

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import okio.Buffer
import org.junit.Test
import org.signal.network.exceptions.RequestCanceledException
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.internal.crypto.AttachmentDigest
import org.whispersystems.signalservice.internal.util.Util
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class DigestingUploadStreamTest {

  companion object {
    private const val PLAINTEXT_LENGTH = 1_000_000
    private val CIPHERTEXT_LENGTH = AttachmentCipherStreamUtil.getCiphertextLength(PLAINTEXT_LENGTH.toLong())
  }

  private val attachmentKey = Util.getSecretBytes(64)
  private val attachmentIv = Util.getSecretBytes(16)
  private val input = Util.getSecretBytes(PLAINTEXT_LENGTH)

  @Test
  fun `chunked upload produces same ciphertext and digest as single request`() {
    val (expectedCiphertext, expectedDigest) = writeSingle(incremental = false, contentStart = 0)
    val (ciphertext, digest) = writeChunked(incremental = false, contentStart = 0, chunkSize = 100_000)

    assertThat(ciphertext.size).isEqualTo(CIPHERTEXT_LENGTH.toInt())
    assertThat(ciphertext.toList()).isEqualTo(expectedCiphertext.toList())
    assertThat(digest.digest.toList()).isEqualTo(expectedDigest.digest.toList())
  }

  @Test
  fun `chunked upload produces same incremental digest as single request`() {
    val (expectedCiphertext, expectedDigest) = writeSingle(incremental = true, contentStart = 0)
    val (ciphertext, digest) = writeChunked(incremental = true, contentStart = 0, chunkSize = 64_000)

    assertThat(ciphertext.toList()).isEqualTo(expectedCiphertext.toList())
    assertThat(digest.digest.toList()).isEqualTo(expectedDigest.digest.toList())
    assertThat(digest.incrementalDigest).isNotNull()
    assertThat(digest.incrementalDigest!!.toList()).isEqualTo(expectedDigest.incrementalDigest!!.toList())
    assertThat(digest.incrementalMacChunkSize).isEqualTo(expectedDigest.incrementalMacChunkSize)
  }

  @Test
  fun `resumed chunked upload writes remaining ciphertext and full digest`() {
    val contentStart = 123_457L
    val (fullCiphertext, expectedDigest) = writeSingle(incremental = false, contentStart = 0)
    val (ciphertext, digest) = writeChunked(incremental = false, contentStart = contentStart, chunkSize = 100_000)

    assertThat(ciphertext.toList()).isEqualTo(fullCiphertext.copyOfRange(contentStart.toInt(), fullCiphertext.size).toList())
    assertThat(digest.digest.toList()).isEqualTo(expectedDigest.digest.toList())
  }

  @Test
  fun `chunks smaller than the read buffer produce same ciphertext and digest as single request`() {
    val (expectedCiphertext, expectedDigest) = writeSingle(incremental = false, contentStart = 0)

    listOf(1_000L, 4_099L).forEach { chunkSize ->
      val (ciphertext, digest) = writeChunked(incremental = false, contentStart = 0, chunkSize = chunkSize)

      assertThat(ciphertext.toList()).isEqualTo(expectedCiphertext.toList())
      assertThat(digest.digest.toList()).isEqualTo(expectedDigest.digest.toList())
    }
  }

  @Test
  fun `resuming at full length writes nothing and still produces full digest`() {
    val (_, expectedDigest) = writeSingle(incremental = false, contentStart = 0)
    val (ciphertext, digest) = writeSingle(incremental = false, contentStart = CIPHERTEXT_LENGTH)

    assertThat(ciphertext.size).isEqualTo(0)
    assertThat(digest.digest.toList()).isEqualTo(expectedDigest.digest.toList())
  }

  @Test
  fun `writing chunks out of order fails`() {
    val stream = createStream(incremental = false, contentStart = 0)
    stream.nextChunk(100_000)
    val second = stream.nextChunk(100_000)

    assertFailure { second.writeTo(Buffer()) }.isInstanceOf<IllegalStateException>()
  }

  @Test
  fun `each chunk writes exactly its declared length`() {
    val stream = createStream(incremental = false, contentStart = 0)
    val chunkSize = 300_000L

    while (stream.bytesRemaining > 0) {
      val chunkLength = minOf(chunkSize, stream.bytesRemaining)
      val body = stream.nextChunk(chunkLength)

      val written = Buffer().use { buffer ->
        body.writeTo(buffer)
        buffer.size
      }

      assertThat(written).isEqualTo(chunkLength)
      assertThat(body.contentLength()).isEqualTo(chunkLength)

      if (stream.bytesRemaining > 0) {
        assertThat(stream.attachmentDigest).isNull()
      }
    }

    assertThat(stream.attachmentDigest).isNotNull()
  }

  @Test
  fun `resumed incremental chunked upload produces the same incremental digest`() {
    val contentStart = 200_003L
    val (fullCiphertext, expectedDigest) = writeSingle(incremental = true, contentStart = 0)
    val (ciphertext, digest) = writeChunked(incremental = true, contentStart = contentStart, chunkSize = 64_000)

    assertThat(ciphertext.toList()).isEqualTo(fullCiphertext.copyOfRange(contentStart.toInt(), fullCiphertext.size).toList())
    assertThat(digest.digest.toList()).isEqualTo(expectedDigest.digest.toList())
    assertThat(digest.incrementalDigest!!.toList()).isEqualTo(expectedDigest.incrementalDigest!!.toList())
  }

  @Test
  fun `chunks that exactly divide the content produce the full ciphertext`() {
    val (expectedCiphertext, expectedDigest) = writeSingle(incremental = false, contentStart = 0)
    val stream = createStream(incremental = false, contentStart = 0)
    val first = CIPHERTEXT_LENGTH / 2

    val output = Buffer()
    stream.nextChunk(first).writeTo(output)
    assertThat(stream.attachmentDigest).isNull()
    stream.nextChunk(CIPHERTEXT_LENGTH - first).writeTo(output)

    assertThat(stream.bytesRemaining).isEqualTo(0L)
    assertThat(output.readByteArray().toList()).isEqualTo(expectedCiphertext.toList())
    assertThat(stream.attachmentDigest!!.digest.toList()).isEqualTo(expectedDigest.digest.toList())
  }

  @Test
  fun `a zero length chunk before the end writes nothing and does not finish the stream`() {
    val (expectedCiphertext, expectedDigest) = writeSingle(incremental = false, contentStart = 0)
    val stream = createStream(incremental = false, contentStart = 0)

    val empty = Buffer().also { stream.nextChunk(0).writeTo(it) }
    assertThat(empty.size).isEqualTo(0L)
    assertThat(stream.attachmentDigest).isNull()

    val output = Buffer().also { stream.nextChunk(stream.bytesRemaining).writeTo(it) }

    assertThat(output.readByteArray().toList()).isEqualTo(expectedCiphertext.toList())
    assertThat(stream.attachmentDigest!!.digest.toList()).isEqualTo(expectedDigest.digest.toList())
  }

  @Test
  fun `digestWithoutSending computes the digest of the remaining input`() {
    val (_, expectedDigest) = writeSingle(incremental = false, contentStart = 0)
    val stream = createStream(incremental = false, contentStart = 0)

    stream.digestWithoutSending()

    assertThat(stream.bytesRemaining).isEqualTo(0L)
    assertThat(stream.attachmentDigest!!.digest.toList()).isEqualTo(expectedDigest.digest.toList())
  }

  @Test
  fun `unencrypted data is sent unchanged across chunks`() {
    val data = Util.getSecretBytes(250_000)
    val stream = DigestingUploadStream(
      inputStream = ByteArrayInputStream(data),
      outputStreamFactory = NoCipherOutputStreamFactory(),
      contentType = "application/octet-stream",
      contentLength = data.size.toLong(),
      incremental = false,
      progressListener = null,
      cancelationSignal = null,
      contentStart = 0
    )

    val output = Buffer()
    while (stream.bytesRemaining > 0) {
      stream.nextChunk(minOf(64_000L, stream.bytesRemaining)).writeTo(output)
    }

    assertThat(output.readByteArray().toList()).isEqualTo(data.toList())
    assertThat(stream.attachmentDigest!!.digest.toList()).isEqualTo(MessageDigest.getInstance("SHA-256").digest(data).toList())
  }

  @Test
  fun `progress is reported against the full length and never goes backwards across chunks`() {
    val updates = mutableListOf<AttachmentTransferProgress>()
    val stream = createStream(incremental = false, contentStart = 0, progressListener = progressListener { updates += it })

    while (stream.bytesRemaining > 0) {
      stream.nextChunk(minOf(100_000L, stream.bytesRemaining)).writeTo(Buffer())
    }

    assertThat(updates.isNotEmpty()).isTrue()
    updates.forEach { assertThat(it.total.inWholeBytes).isEqualTo(CIPHERTEXT_LENGTH) }
    updates.zipWithNext().forEach { (previous, next) -> assertThat(next.transmitted.inWholeBytes).isGreaterThanOrEqualTo(previous.transmitted.inWholeBytes) }
    assertThat(updates.last().transmitted.inWholeBytes).isLessThanOrEqualTo(CIPHERTEXT_LENGTH)
  }

  @Test
  fun `canceling stops the chunk that is being written`() {
    var canceled = false
    val stream = createStream(incremental = false, contentStart = 0, cancelationSignal = { canceled })

    stream.nextChunk(100_000).writeTo(Buffer())
    canceled = true

    assertFailure { stream.nextChunk(100_000).writeTo(Buffer()) }.isInstanceOf<RequestCanceledException>()
    assertThat(stream.attachmentDigest).isNull()
  }

  @Test
  fun `requesting more than remains fails`() {
    val stream = createStream(incremental = false, contentStart = 0)

    assertFailure { stream.nextChunk(CIPHERTEXT_LENGTH + 1) }.isInstanceOf<IllegalArgumentException>()
    assertFailure { stream.nextChunk(-1) }.isInstanceOf<IllegalArgumentException>()

    stream.nextChunk(CIPHERTEXT_LENGTH)
    assertFailure { stream.nextChunk(1) }.isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun `writing a chunk twice fails`() {
    val stream = createStream(incremental = false, contentStart = 0)
    val first = stream.nextChunk(100_000)
    first.writeTo(Buffer())

    assertFailure { first.writeTo(Buffer()) }.isInstanceOf<IllegalStateException>()
  }

  @Test
  fun `a start past the end is rejected`() {
    assertFailure { createStream(incremental = false, contentStart = CIPHERTEXT_LENGTH + 1) }.isInstanceOf<IllegalArgumentException>()
    assertFailure { createStream(incremental = false, contentStart = -1) }.isInstanceOf<IllegalArgumentException>()
  }

  private fun progressListener(onProgress: (AttachmentTransferProgress) -> Unit): SignalServiceAttachment.ProgressListener {
    return object : SignalServiceAttachment.ProgressListener {
      override fun onAttachmentProgress(progress: AttachmentTransferProgress) = onProgress(progress)
      override fun shouldCancel(): Boolean = false
    }
  }

  private fun writeSingle(incremental: Boolean, contentStart: Long): Pair<ByteArray, AttachmentDigest> {
    val body = DigestingRequestBody(
      inputStream = ByteArrayInputStream(input),
      outputStreamFactory = AttachmentCipherOutputStreamFactory(attachmentKey, attachmentIv),
      contentType = "application/octet-stream",
      contentLength = CIPHERTEXT_LENGTH,
      incremental = incremental,
      progressListener = null,
      cancelationSignal = null,
      contentStart = contentStart
    )

    val ciphertext = Buffer().use { buffer ->
      body.writeTo(buffer)
      buffer.readByteArray()
    }

    return ciphertext to body.attachmentDigest!!
  }

  private fun writeChunked(incremental: Boolean, contentStart: Long, chunkSize: Long): Pair<ByteArray, AttachmentDigest> {
    val stream = createStream(incremental, contentStart)
    val output = Buffer()

    while (stream.bytesRemaining > 0) {
      val chunk = Buffer()
      stream.nextChunk(minOf(chunkSize, stream.bytesRemaining)).writeTo(chunk)
      output.writeAll(chunk)
    }

    return output.readByteArray() to stream.attachmentDigest!!
  }

  private fun createStream(
    incremental: Boolean,
    contentStart: Long,
    progressListener: SignalServiceAttachment.ProgressListener? = null,
    cancelationSignal: CancelationSignal? = null
  ): DigestingUploadStream {
    return DigestingUploadStream(
      inputStream = ByteArrayInputStream(input),
      outputStreamFactory = AttachmentCipherOutputStreamFactory(attachmentKey, attachmentIv),
      contentType = "application/octet-stream",
      contentLength = CIPHERTEXT_LENGTH,
      incremental = incremental,
      progressListener = progressListener,
      cancelationSignal = cancelationSignal,
      contentStart = contentStart
    )
  }
}
