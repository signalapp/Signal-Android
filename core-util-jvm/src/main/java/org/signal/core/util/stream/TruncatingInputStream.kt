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
class TruncatingInputStream(private val wrapped: InputStream, private val maxBytes: Long) : FilterInputStream(wrapped) {

  private var bytesRead: Long = 0
  private var lastMark = -1L

  override fun read(): Int {
    if (bytesRead >= maxBytes) {
      return -1
    }

    return wrapped.read().also {
      if (it >= 0) {
        bytesRead++
      }
    }
  }

  override fun read(destination: ByteArray): Int {
    return read(destination, 0, destination.size)
  }

  override fun read(destination: ByteArray, offset: Int, length: Int): Int {
    if (bytesRead >= maxBytes) {
      return -1
    }

    val bytesRemaining: Long = maxBytes - bytesRead
    val bytesToRead: Int = if (bytesRemaining > length) length else Math.toIntExact(bytesRemaining)
    val bytesRead = wrapped.read(destination, offset, bytesToRead)

    if (bytesRead > 0) {
      this.bytesRead += bytesRead
    }

    return bytesRead
  }

  override fun skip(requestedSkipCount: Long): Long {
    val bytesRemaining: Long = maxBytes - bytesRead
    val bytesToSkip: Long = min(bytesRemaining, requestedSkipCount)

    return super.skip(bytesToSkip).also { bytesSkipped ->
      if (bytesSkipped > 0) {
        this.bytesRead += bytesSkipped
      }
    }
  }

  override fun available(): Int {
    val bytesRemaining = Math.toIntExact(maxBytes - bytesRead)
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
    lastMark = bytesRead
  }

  override fun reset() {
    if (!markSupported()) {
      throw UnsupportedOperationException("Mark not supported")
    }

    if (lastMark == -1L) {
      throw UnsupportedOperationException("Mark not set")
    }

    wrapped.reset()
    bytesRead = lastMark
  }

  /**
   * If the stream has been fully read, this will return all bytes that were truncated from the stream.
   *
   * @param byteLimit The maximum number of truncated bytes to read. Defaults to no limit.
   */
  fun readTruncatedBytes(byteLimit: Int = -1): ByteArray {
    return if (byteLimit < 0) {
      wrapped.readFully()
    } else {
      wrapped.readAtMostNBytes(byteLimit)
    }
  }
}
