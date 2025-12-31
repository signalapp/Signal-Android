/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.random.Random

class Base64Test {

  @Test
  fun `decode - correctly decode all strings regardless of url safety or padding`() {
    val stopwatch = Stopwatch("time", 2)

    for (len in 0 until 256) {
      for (i in 0..2_000) {
        val bytes = Random.nextBytes(len)

        val padded = Base64.encodeWithPadding(bytes)
        val unpadded = Base64.encodeWithoutPadding(bytes)
        val urlSafePadded = Base64.encodeUrlSafeWithPadding(bytes)
        val urlSafeUnpadded = Base64.encodeUrlSafeWithoutPadding(bytes)

        assertArrayEquals(bytes, Base64.decode(padded))
        assertArrayEquals(bytes, Base64.decode(unpadded))
        assertArrayEquals(bytes, Base64.decode(urlSafePadded))
        assertArrayEquals(bytes, Base64.decode(urlSafeUnpadded))
      }
    }

    println(stopwatch.stopAndGetLogString())
  }
}
