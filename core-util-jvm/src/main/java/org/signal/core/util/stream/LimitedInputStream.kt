/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.stream

import org.signal.core.util.readAtMostNBytes
import org.signal.core.util.readFully
import java.io.FilterInputStream
import java.io.InputStream
import java.lang.UnsupportedOperationException
import kotlin.math.min

/**
 * An [InputStream] that will read from the target [InputStream] until it reaches the end, or until it has read [maxBytes] bytes.
 */
class LimitedInputStream(private val wrapped: InputStream, private val maxBytes: Long) : FilterInputStream(wrapped) {

  private var totalBytesRead: Long = 0
  private var lastMark = -1L

  override fun read(): Int {
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
    val bytesRemaining: Long = maxBytes - totalBytesRead
    val bytesToSkip: Long = min(bytesRemaining, requestedSkipCount)
    val skipCount = super.skip(bytesToSkip)

    totalBytesRead += skipCount

    return skipCount
  }

  override fun available(): Int {
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
    lastMark = totalBytesRead
  }

  override fun reset() {
    if (!markSupported()) {
      throw UnsupportedOperationException("Mark not supported")
    }

    if (lastMark == -1L) {
      throw UnsupportedOperationException("Mark not set")
    }

    wrapped.reset()
    totalBytesRead = lastMark
  }

  /**
   * If the stream has been fully read, this will return all bytes that were truncated from the stream.
   *
   * @param byteLimit The maximum number of truncated bytes to read. Defaults to no limit.
   */
  fun readTruncatedBytes(byteLimit: Int = -1): ByteArray {
    if (totalBytesRead < maxBytes) {
      throw IllegalStateException("Stream has not been fully read")
    }

    return if (byteLimit < 0) {
      wrapped.readFully()
    } else {
      wrapped.readAtMostNBytes(byteLimit)
    }
  }
}
