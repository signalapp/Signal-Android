/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push.http

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.blackholeSink
import okio.buffer
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.incrementalmac.ChunkSizeChoice
import org.signal.network.exceptions.RequestCanceledException
import org.whispersystems.signalservice.api.crypto.DigestingOutputStream
import org.whispersystems.signalservice.api.crypto.SkippingOutputStream
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.internal.crypto.AttachmentDigest
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.min

/**
 * Encrypts and digests [inputStream] in a single pass while allowing the ciphertext to be sent across multiple
 * requests. The first [contentStart] bytes of ciphertext are digested but never written.
 */
class DigestingUploadStream(
  private val inputStream: InputStream,
  private val outputStreamFactory: OutputStreamFactory,
  private val contentType: String,
  private val contentLength: Long,
  private val incremental: Boolean,
  private val progressListener: SignalServiceAttachment.ProgressListener?,
  private val cancelationSignal: CancelationSignal?,
  private val contentStart: Long
) {

  companion object {
    private val TAG = Log.tag(DigestingUploadStream::class)
  }

  init {
    require(contentLength >= contentStart)
    require(contentStart >= 0)
  }

  var attachmentDigest: AttachmentDigest? = null
    private set

  private val router = ChunkRouter()
  private val digestStream = ByteArrayOutputStream()
  private val isIncremental = incremental && outputStreamFactory is AttachmentCipherOutputStreamFactory
  private val sizeChoice: ChunkSizeChoice by lazy { ChunkSizeChoice.inferChunkSize(contentLength.toInt()) }
  private var outputStream: DigestingOutputStream? = null
  private var bytesEmitted = 0L
  private var bytesWritten = 0L

  val bytesRemaining: Long
    get() = contentLength - contentStart - bytesEmitted

  /**
   * Creates a one-shot [RequestBody] for the next [byteCount] bytes of ciphertext. Chunks must be written in the order they are created.
   */
  fun nextChunk(byteCount: Long): RequestBody {
    require(byteCount in 0..bytesRemaining)
    val start = bytesEmitted
    bytesEmitted += byteCount
    return ChunkRequestBody(start, byteCount, isFinal = bytesRemaining == 0L)
  }

  @Throws(IOException::class)
  fun digestWithoutSending() {
    blackholeSink().buffer().use { nextChunk(bytesRemaining).writeTo(it) }
  }

  @Throws(IOException::class)
  private fun write(sink: BufferedSink, start: Long, byteCount: Long, isFinal: Boolean) {
    check(start == bytesWritten) { "Chunks must be written in order. Expected start: $bytesWritten, actual: $start" }

    val sinkStream = sink.outputStream()
    router.attach(sinkStream, if (isFinal) Long.MAX_VALUE else byteCount)

    val cipherStream = outputStream ?: createOutputStream().also { outputStream = it }
    val buffer = ByteArray(16 * 1024)

    while (attachmentDigest == null && (router.remaining > 0 || isFinal)) {
      if (cancelationSignal?.isCanceled == true) {
        throw RequestCanceledException()
      }

      val read = inputStream.read(buffer, 0, buffer.size)
      if (read == -1) {
        finish(cipherStream)
      } else {
        cipherStream.write(buffer, 0, read)
        progressListener?.onAttachmentProgress(AttachmentTransferProgress(total = contentLength, transmitted = cipherStream.totalBytesWritten))
      }
    }

    router.detach()
    sinkStream.flush()
    bytesWritten += byteCount
  }

  private fun createOutputStream(): DigestingOutputStream {
    val inner = SkippingOutputStream(contentStart, router)
    return if (isIncremental) {
      (outputStreamFactory as AttachmentCipherOutputStreamFactory).createIncrementalFor(inner, contentLength, sizeChoice, digestStream)
    } else {
      outputStreamFactory.createFor(inner)
    }
  }

  private fun finish(cipherStream: DigestingOutputStream) {
    cipherStream.flush()
    cipherStream.close()

    if (contentLength != cipherStream.totalBytesWritten) {
      Log.w(TAG, "Wrote ${cipherStream.totalBytesWritten} bytes, but expected $contentLength bytes! Difference: ${cipherStream.totalBytesWritten - contentLength}")
    } else {
      Log.d(TAG, "Wrote the expected number of bytes.")
    }

    val incrementalDigest: ByteArray? = if (isIncremental) {
      digestStream.close()
      digestStream.toByteArray()
    } else {
      null
    }

    val incrementalDigestChunkSize: Int = if (incrementalDigest?.isNotEmpty() == true) sizeChoice.sizeInBytes else 0

    attachmentDigest = AttachmentDigest(cipherStream.transmittedDigest, incrementalDigest, incrementalDigestChunkSize)
  }

  private inner class ChunkRequestBody(private val start: Long, private val byteCount: Long, private val isFinal: Boolean) : RequestBody() {
    override fun contentType(): MediaType? = contentType.toMediaTypeOrNull()

    override fun contentLength(): Long = byteCount

    override fun isOneShot(): Boolean = true

    override fun writeTo(sink: BufferedSink) {
      write(sink, start, byteCount, isFinal)
    }
  }

  /**
   * Writes to the attached request up to its limit and buffers the excess for the next request.
   */
  private class ChunkRouter : OutputStream() {
    private val overflow = ByteArrayOutputStream()
    private var target: OutputStream? = null

    var remaining = 0L
      private set

    fun attach(target: OutputStream, byteCount: Long) {
      this.target = target
      remaining = byteCount

      if (overflow.size() > 0) {
        val pending = overflow.toByteArray()
        overflow.reset()
        write(pending, 0, pending.size)
      }
    }

    fun detach() {
      target = null
      remaining = 0
    }

    override fun write(b: Int) {
      write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      val direct = min(remaining, len.toLong()).toInt()
      if (direct > 0) {
        target!!.write(b, off, direct)
        remaining -= direct
      }

      if (direct < len) {
        overflow.write(b, off + direct, len - direct)
      }
    }

    override fun flush() = Unit

    override fun close() = Unit
  }
}
