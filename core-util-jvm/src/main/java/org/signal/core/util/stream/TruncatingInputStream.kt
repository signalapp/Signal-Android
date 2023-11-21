/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.stream

import java.io.FilterInputStream
import java.io.InputStream
import java.lang.UnsupportedOperationException

/**
 * An [InputStream] that will read from the target [InputStream] until it reaches the end, or until it has read [maxBytes] bytes.
 */
class TruncatingInputStream(private val wrapped: InputStream, private val maxBytes: Long) : FilterInputStream(wrapped) {

  private var bytesRead: Long = 0

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

  override fun skip(n: Long): Long {
    throw UnsupportedOperationException()
  }

  override fun reset() {
    throw UnsupportedOperationException()
  }
}
