/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class InputStreamExtensionTests {

  @Test
  fun `when I call readLength, it returns the correct length`() {
    for (i in 1..10) {
      val bytes = ByteArray(Random.nextInt(from = 512, until = 8092))
      val length = bytes.inputStream().readLength()
      assertEquals(bytes.size.toLong(), length)
    }
  }

  @Test
  fun `when I call readAtMostNBytes, I only read that many bytes`() {
    val bytes = ByteArray(100)
    val inputStream = bytes.inputStream()
    val readBytes = inputStream.readAtMostNBytes(50)
    assertEquals(50, readBytes.size)
  }

  @Test
  fun `when I call readAtMostNBytes, it will return at most the length of the stream`() {
    val bytes = ByteArray(100)
    val inputStream = bytes.inputStream()
    val readBytes = inputStream.readAtMostNBytes(200)
    assertEquals(100, readBytes.size)
  }
}
