/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.whispersystems.signalservice.internal.crypto

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
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
          assertThat(byte).isEqualTo(source[index])
        } else {
          assertThat(byte).isEqualTo(0x00)
        }
      }
    }
  }

  /**
   * Sizes above [Int.MAX_VALUE] must not be truncated. A padded size smaller than the input implies a *negative* amount of padding, which blows up
   * callers that use the difference to size a buffer.
   */
  @Test
  fun `getPaddedSize does not truncate sizes above Int MAX_VALUE`() {
    val sizes = listOf(
      Int.MAX_VALUE.toLong() - 1,
      Int.MAX_VALUE.toLong(),
      Int.MAX_VALUE.toLong() + 1,
      3L * 1024 * 1024 * 1024,
      100L * 1024 * 1024 * 1024
    )

    sizes.forEach { size ->
      assertThat(PaddingInputStream.getPaddedSize(size), "padded size of $size").isGreaterThanOrEqualTo(size)
    }
  }

  @Test
  fun `getMaxUnpaddedSize does not truncate sizes above Int MAX_VALUE`() {
    val maxPaddedSize = 100L * 1024 * 1024 * 1024
    val maxUnpaddedSize = PaddingInputStream.getMaxUnpaddedSize(maxPaddedSize)

    assertThat(maxUnpaddedSize).isGreaterThan(Int.MAX_VALUE.toLong())
    assertThat(PaddingInputStream.getPaddedSize(maxUnpaddedSize)).isLessThanOrEqualTo(maxPaddedSize)
  }
}
