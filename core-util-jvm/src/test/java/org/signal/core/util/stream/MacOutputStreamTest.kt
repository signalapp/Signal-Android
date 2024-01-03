/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.signal.core.util.StreamUtil
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class MacOutputStreamTest {

  @Test
  fun `stream mac matches normal mac when writing via buffer`() {
    testMacEquality { data, outputStream ->
      StreamUtil.copy(data.inputStream(), outputStream)
    }
  }

  @Test
  fun `stream mac matches normal mac when writing one byte at a time`() {
    testMacEquality { data, outputStream ->
      for (byte in data) {
        outputStream.write(byte.toInt())
      }
    }
  }

  private fun testMacEquality(write: (ByteArray, OutputStream) -> Unit) {
    val data = Random.nextBytes(1_000)
    val key = Random.nextBytes(32)

    val mac1 = Mac.getInstance("HmacSHA256").apply {
      init(SecretKeySpec(key, "HmacSHA256"))
    }

    val mac2 = Mac.getInstance("HmacSHA256").apply {
      init(SecretKeySpec(key, "HmacSHA256"))
    }

    val expectedMac = mac1.doFinal(data)

    val actualMac = MacOutputStream(ByteArrayOutputStream(), mac2).use { stream ->
      write(data, stream)
      stream.mac.doFinal()
    }

    assertArrayEquals(expectedMac, actualMac)
  }
}
