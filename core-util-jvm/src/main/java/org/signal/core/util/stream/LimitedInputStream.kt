/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.stream

import org.signal.core.util.logging.Log
import java.io.FilterInputStream
import java.io.InputStream
import java.lang.UnsupportedOperationException
import kotlin.math.min

/**
 * An [InputStream] that will read from the target [InputStream] until it reaches the end, or until it has read [maxBytes] bytes.
 *
 * @param maxBytes The maximum number of bytes to read from the stream. If set to -1, there will be no limit.
 */
class LimitedInputStream(private val wrapped: InputStream, private val maxBytes: Long) : FilterInputStream(wrapped) {

  private var totalBytesRead: Long = 0
  private var lastMark = -1L

  companion object {

    private const val UNLIMITED = -1L

    private val TAG = Log.tag(LimitedInputStream::class)

    /**
     * Returns a [LimitedInputStream] that doesn't limit the stream at all -- it'll allow reading the full thing.
     */
    @JvmStatic
    fun withoutLimits(wrapped: InputStream): LimitedInputStream {
      return LimitedInputStream(wrapped = wrapped, maxBytes = UNLIMITED)
    }
  }

  override fun read(): Int {
    if (maxBytes == UNLIMITED) {
      return wrapped.read()
    }

    if (totalBytesRead >= maxBytes) {
      return -1
    }

    val read = wrapped.read()
    if (read >= 0) {
      totalBytesRead++
    }

    return read
  }

  override fun read(destination: ByteArray): Int {
    return read(destination, 0, destination.size)
  }

  override fun read(destination: ByteArray, offset: Int, length: Int): Int {
    if (maxBytes == UNLIMITED) {
      return wrapped.read(destination, offset, length)
    }

    if (totalBytesRead >= maxBytes) {
      return -1
    }

    val bytesRemaining: Long = maxBytes - totalBytesRead
    val bytesToRead: Int = min(length, Math.toIntExact(bytesRemaining))
    val bytesRead = wrapped.read(destination, offset, bytesToRead)

    if (bytesRead > 0) {
      totalBytesRead += bytesRead
    }

    return bytesRead
  }

  override fun skip(requestedSkipCount: Long): Long {
    if (maxBytes == UNLIMITED) {
      return wrapped.skip(requestedSkipCount)
    }

    val bytesRemaining: Long = maxBytes - totalBytesRead
    val bytesToSkip: Long = min(bytesRemaining, requestedSkipCount)
    val skipCount = super.skip(bytesToSkip)

    totalBytesRead += skipCount

    return skipCount
  }

  override fun available(): Int {
    if (maxBytes == UNLIMITED) {
      return wrapped.available()
    }

    val bytesRemaining = Math.toIntExact(maxBytes - totalBytesRead)
    return min(bytesRemaining, wrapped.available())
  }

  override fun markSupported(): Boolean {
    return wrapped.markSupported()
  }

  override fun mark(readlimit: Int) {
    if (!markSupported()) {
      throw UnsupportedOperationException("Mark not supported")
    }

    wrapped.mark(readlimit)

    if (maxBytes == UNLIMITED) {
      return
    }

    lastMark = totalBytesRead
  }

  override fun reset() {
    if (!markSupported()) {
      throw UnsupportedOperationException("Mark not supported")
    }

    if (lastMark == UNLIMITED) {
      throw UnsupportedOperationException("Mark not set")
    }

    wrapped.reset()

    if (maxBytes == UNLIMITED) {
      return
    }

    totalBytesRead = lastMark
  }

  /**
   * If the stream has been fully read, this will return a stream that contains the remaining bytes that were truncated.
   * If the stream was setup with no limit, this will always return an empty stream.
   */
  fun leftoverStream(): InputStream {
    if (maxBytes == UNLIMITED) {
      return ByteArray(0).inputStream()
    }

    if (totalBytesRead < maxBytes) {
      Log.w(TAG, "Reading leftover stream when the stream has not been fully read! maxBytes is $maxBytes, but we've only read $totalBytesRead")
    }

    return wrapped
  }
}
