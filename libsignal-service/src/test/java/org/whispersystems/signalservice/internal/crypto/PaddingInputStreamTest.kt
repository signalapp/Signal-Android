/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.whispersystems.signalservice.internal.crypto

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.Is.`is`
import org.junit.Test
import org.signal.core.util.StreamUtil
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PaddingInputStreamTest {

  /**
   * Small stress test to confirm padding input only returns the source stream data
   * followed strictly by zeros.
   */
  @Test
  fun stressTest() {
    (0..2048).forEach { length ->
      val source = ByteArray(length).apply { fill(42) }
      val sourceInput = ByteArrayInputStream(source)
      val paddingInput = PaddingInputStream(sourceInput, length.toLong())

      val paddedData = ByteArrayOutputStream().let {
        StreamUtil.copy(paddingInput, it)
        it.toByteArray()
      }

      paddedData.forEachIndexed { index, byte ->
        if (index < length) {
          assertThat(byte, `is`(source[index]))
        } else {
          assertThat(byte, `is`(0x00))
        }
      }
    }
  }
}
