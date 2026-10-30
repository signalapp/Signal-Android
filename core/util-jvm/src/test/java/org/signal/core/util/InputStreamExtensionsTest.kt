/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import kotlin.random.Random

class InputStreamExtensionsTest {

  @Test
  fun `readVarInt32OrNull - empty stream returns null`() {
    assertThat(ByteArray(0).inputStream().readVarInt32OrNull()).isNull()
  }

  @Test
  fun `readVarInt32OrNull - single byte`() {
    assertThat(byteArrayOf(0x05).inputStream().readVarInt32OrNull()).isEqualTo(5)
  }

  @Test
  fun `readVarInt32OrNull - multiple bytes`() {
    assertThat(byteArrayOf(0xAC.toByte(), 0x02).inputStream().readVarInt32OrNull()).isEqualTo(300)
  }

  @Test
  fun `readVarInt32OrNull - five byte encoding of all ones is -1, not end of stream`() {
    val bytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F)

    assertThat(bytes.inputStream().readVarInt32OrNull()).isEqualTo(-1)
  }

  @Test
  fun `readVarInt32OrNull - stream ending after a continuation byte throws EOFException`() {
    assertFailure { byteArrayOf(0x80.toByte()).inputStream().readVarInt32OrNull() }
      .isInstanceOf(EOFException::class)
  }

  @Test
  fun `readVarInt32OrNull - more than five continuation bytes is malformed`() {
    val bytes = ByteArray(6) { 0x80.toByte() }

    assertFailure { bytes.inputStream().readVarInt32OrNull() }
      .isInstanceOf(IOException::class)
  }

  @Test
  fun `readVarInt32OrNull - reads back what writeVarInt32 wrote`() {
    val values = (0..10_000).map { Random.nextInt() }
    val bytes = ByteArrayOutputStream().also { out -> values.forEach { out.writeVarInt32(it) } }.toByteArray()

    bytes.inputStream().use { input ->
      for (value in values) {
        assertThat(input.readVarInt32OrNull()).isEqualTo(value)
      }
      assertThat(input.readVarInt32OrNull()).isNull()
    }
  }
}
