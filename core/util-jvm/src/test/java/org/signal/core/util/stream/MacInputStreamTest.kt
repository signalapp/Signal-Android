/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.signal.core.util.readFully
import java.io.InputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class MacInputStreamTest {

  @Test
  fun `stream mac matches normal mac when reading via buffer`() {
    testMacEquality { inputStream ->
      inputStream.readFully()
    }
  }

  @Test
  fun `stream mac matches normal mac when reading one byte at a time`() {
    testMacEquality { inputStream ->
      var lastRead = inputStream.read()
      while (lastRead != -1) {
        lastRead = inputStream.read()
      }
    }
  }

  private fun testMacEquality(read: (InputStream) -> Unit) {
    val data = Random.nextBytes(1_000)
    val key = Random.nextBytes(32)

    val mac1 = Mac.getInstance("HmacSHA256").apply {
      init(SecretKeySpec(key, "HmacSHA256"))
    }

    val mac2 = Mac.getInstance("HmacSHA256").apply {
      init(SecretKeySpec(key, "HmacSHA256"))
    }

    val expectedMac = mac1.doFinal(data)

    val actualMac = MacInputStream(data.inputStream(), mac2).use { stream ->
      read(stream)
      stream.mac.doFinal()
    }

    assertArrayEquals(expectedMac, actualMac)
  }
}
