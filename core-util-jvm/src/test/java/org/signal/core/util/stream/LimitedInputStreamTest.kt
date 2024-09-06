/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.stream

import org.junit.Assert.assertEquals
import org.junit.Test
import org.signal.core.util.readFully
import org.signal.core.util.readNBytesOrThrow

class LimitedInputStreamTest {

  @Test
  fun `when I fully read the stream via a buffer, I should only get maxBytes`() {
    val inputStream = LimitedInputStream(ByteArray(100).inputStream(), maxBytes = 75)
    val data = inputStream.readFully()

    assertEquals(75, data.size)
  }

  @Test
  fun `when I fully read the stream one byte at a time, I should only get maxBytes`() {
    val inputStream = LimitedInputStream(ByteArray(100).inputStream(), maxBytes = 75)

    var count = 0
    var lastRead = inputStream.read()
    while (lastRead != -1) {
      count++
      lastRead = inputStream.read()
    }

    assertEquals(75, count)
  }

  @Test
  fun `when I skip past the maxBytes, I should get -1`() {
    val inputStream = LimitedInputStream(ByteArray(100).inputStream(), maxBytes = 75)

    val skipCount = inputStream.skip(100)
    val read = inputStream.read()

    assertEquals(75, skipCount)
    assertEquals(-1, read)
  }

  @Test
  fun `when I skip, I should still truncate correctly afterwards`() {
    val inputStream = LimitedInputStream(ByteArray(100).inputStream(), maxBytes = 75)

    val skipCount = inputStream.skip(50)
    val data = inputStream.readFully()

    assertEquals(50, skipCount)
    assertEquals(25, data.size)
  }

  @Test
  fun `when I skip more than maxBytes, I only skip maxBytes`() {
    val inputStream = LimitedInputStream(ByteArray(100).inputStream(), maxBytes = 75)

    val skipCount = inputStream.skip(100)

    assertEquals(75, skipCount)
  }

  @Test
  fun `when I finish reading the stream, getTruncatedBytes gives me the rest`() {
    val inputStream = LimitedInputStream(ByteArray(100).inputStream(), maxBytes = 75)
    inputStream.readFully()

    val truncatedBytes = inputStream.readTruncatedBytes()
    assertEquals(25, truncatedBytes.size)
  }

  @Test
  fun `when I finish reading the stream, getTruncatedBytes gives me the rest, respecting the byte limit`() {
    val inputStream = LimitedInputStream(ByteArray(100).inputStream(), maxBytes = 75)
    inputStream.readFully()

    val truncatedBytes = inputStream.readTruncatedBytes(byteLimit = 10)
    assertEquals(10, truncatedBytes.size)
  }

  @Test(expected = IllegalStateException::class)
  fun `if I have not finished reading the stream, getTruncatedBytes throws IllegalStateException`() {
    val inputStream = LimitedInputStream(ByteArray(100).inputStream(), maxBytes = 75)
    inputStream.readTruncatedBytes()
  }

  @Test
  fun `when I call available, it should respect the maxBytes`() {
    val inputStream = LimitedInputStream(ByteArray(100).inputStream(), maxBytes = 75)
    val available = inputStream.available()

    assertEquals(75, available)
  }

  @Test
  fun `when I call available after reading some bytes, it should respect the maxBytes`() {
    val inputStream = LimitedInputStream(ByteArray(100).inputStream(), maxBytes = 75)
    inputStream.readNBytesOrThrow(50)

    val available = inputStream.available()

    assertEquals(25, available)
  }

  @Test
  fun `when I mark and reset, it should jump back to the correct position`() {
    val inputStream = LimitedInputStream(ByteArray(100).inputStream(), maxBytes = 75)

    inputStream.mark(100)
    inputStream.readNBytesOrThrow(10)
    inputStream.reset()

    val data = inputStream.readFully()

    assertEquals(75, data.size)
  }
}
