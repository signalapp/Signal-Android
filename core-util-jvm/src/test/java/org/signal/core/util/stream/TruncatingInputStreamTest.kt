/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.stream

import org.junit.Assert.assertEquals
import org.junit.Test
import org.signal.core.util.readFully

class TruncatingInputStreamTest {

  @Test
  fun `when I fully read the stream via a buffer, I should only get maxBytes`() {
    val inputStream = TruncatingInputStream(ByteArray(100).inputStream(), maxBytes = 75)
    val data = inputStream.readFully()

    assertEquals(75, data.size)
  }

  @Test
  fun `when I fully read the stream one byte at a time, I should only get maxBytes`() {
    val inputStream = TruncatingInputStream(ByteArray(100).inputStream(), maxBytes = 75)

    var count = 0
    var lastRead = inputStream.read()
    while (lastRead != -1) {
      count++
      lastRead = inputStream.read()
    }

    assertEquals(75, count)
  }
}
