/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.engine.GlideException
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer

class WebpSanByteBufferDecoderTest {

  companion object {
    /** An 8x8 lossless webp. */
    private val VALID_WEBP = byteArrayOf(
      82, 73, 70, 70, 28, 0, 0, 0, 87, 69, 66, 80, 86, 80, 56, 76, 15, 0,
      0, 0, 47, 7, -64, 1, 0, 7, 16, -3, -113, -2, 7, 34, -94, -1, 1, 0
    )
  }

  private val decoder = WebpSanByteBufferDecoder<Any>()

  @Test
  fun `handles - non-webp data is not blocked`() {
    val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64)

    assertThat(decoder.handles(ByteBuffer.wrap(png), Options())).isFalse()
  }

  @Test
  fun `handles - data too short to have a magic number is blocked`() {
    assertThat(decoder.handles(ByteBuffer.wrap(byteArrayOf(0x52, 0x49, 0x46, 0x46)), Options())).isTrue()
  }

  @Test
  fun `handles - valid webp is not blocked`() {
    assertThat(decoder.handles(ByteBuffer.wrap(VALID_WEBP), Options())).isFalse()
  }

  @Test
  fun `handles - malformed webp is blocked`() {
    val malformed = "RIFF".toByteArray() + byteArrayOf(0x40, 0x00, 0x00, 0x00) + "WEBP".toByteArray() + ByteArray(56)

    assertThat(decoder.handles(ByteBuffer.wrap(malformed), Options())).isTrue()
  }

  @Test
  fun `handles - leaves the caller's buffer position untouched`() {
    val buffer = ByteBuffer.wrap(VALID_WEBP)

    decoder.handles(buffer, Options())

    assertThat(buffer.position()).isEqualTo(0)
  }

  @Test
  fun `decode - throws GlideException so that Glide aborts the decode path`() {
    assertThrows(GlideException::class.java) {
      decoder.decode(ByteBuffer.wrap(VALID_WEBP), 100, 100, Options())
    }
  }
}
