/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.postprocessing

import org.signal.core.util.readLength
import org.signal.libsignal.media.Mp4Sanitizer
import org.signal.libsignal.media.SanitizedMetadata
import org.thoughtcrime.securesms.video.exceptions.VideoPostProcessingException
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream

/**
 * A post processor that takes a stream of bytes, and using [Mp4Sanitizer], moves the metadata to the front of the file.
 *
 * @property inputStreamFactory factory for the [InputStream]. Expected to be called multiple times.
 */
class Mp4FaststartPostProcessor(private val inputStreamFactory: InputStreamFactory) {

  /**
   * It is the responsibility of the caller to close the resulting [InputStream].
   */
  fun process(inputLength: Long = calculateStreamLength(inputStreamFactory.create())): SequenceInputStream {
    val metadata = inputStreamFactory.create().use { inputStream ->
      sanitizeMetadata(inputStream, inputLength)
    }
    if (metadata.sanitizedMetadata == null) {
      throw VideoPostProcessingException("Sanitized metadata was null!")
    }
    val inputStream = inputStreamFactory.create()
    inputStream.skip(metadata.dataOffset)
    return SequenceInputStream(ByteArrayInputStream(metadata.sanitizedMetadata), LimitedInputStream(inputStream, metadata.dataLength))
  }

  fun processAndWriteTo(outputStream: OutputStream, inputLength: Long = calculateStreamLength(inputStreamFactory.create())): Long {
    process(inputLength).use { inStream ->
      return inStream.copyTo(outputStream)
    }
  }

  fun interface InputStreamFactory {
    fun create(): InputStream
  }

  companion object {
    const val TAG = "Mp4Faststart"

    @JvmStatic
    fun calculateStreamLength(inputStream: InputStream): Long {
      inputStream.use {
        return it.readLength()
      }
    }

    @JvmStatic
    private fun sanitizeMetadata(inputStream: InputStream, inputLength: Long): SanitizedMetadata {
      inputStream.use {
        return Mp4Sanitizer.sanitize(it, inputLength)
      }
    }
  }

  private class LimitedInputStream(innerStream: InputStream, limit: Long) : FilterInputStream(innerStream) {
    private var left: Long = limit
    private var mark: Long = -1

    init {
      if (limit < 0) {
        throw IllegalArgumentException("Limit must be non-negative!")
      }
    }

    @Throws(IOException::class)
    override fun available(): Int {
      return `in`.available().toLong().coerceAtMost(left).toInt()
    }

    @Synchronized
    override fun mark(readLimit: Int) {
      `in`.mark(readLimit)
      mark = left
    }

    @Throws(IOException::class)
    override fun read(): Int {
      if (left == 0L) {
        return -1
      }
      val result = `in`.read()
      if (result != -1) {
        --left
      }
      return result
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
      if (left == 0L) {
        return -1
      }
      val toRead = len.toLong().coerceAtMost(left).toInt()
      val result = `in`.read(b, off, toRead)
      if (result != -1) {
        left -= result.toLong()
      }
      return result
    }

    @Synchronized
    @Throws(IOException::class)
    override fun reset() {
      if (!`in`.markSupported()) {
        throw IOException("Mark not supported")
      }
      if (mark == -1L) {
        throw IOException("Mark not set")
      }
      `in`.reset()
      left = mark
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
      val toSkip = n.coerceAtMost(left)
      val skipped = `in`.skip(toSkip)
      left -= skipped
      return skipped
    }
  }
}
