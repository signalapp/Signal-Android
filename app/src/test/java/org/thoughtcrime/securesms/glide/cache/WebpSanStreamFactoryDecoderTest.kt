/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.bumptech.glide.load.Options
import org.junit.Test
import org.signal.glide.common.io.InputStreamFactory
import java.io.ByteArrayInputStream
import java.io.InputStream

class WebpSanStreamFactoryDecoderTest {

  companion object {
    /** An 8x8 lossless webp. */
    private val VALID_WEBP = byteArrayOf(
      82, 73, 70, 70, 28, 0, 0, 0, 87, 69, 66, 80, 86, 80, 56, 76, 15, 0,
      0, 0, 47, 7, -64, 1, 0, 7, 16, -3, -113, -2, 7, 34, -94, -1, 1, 0
    )
  }

  private val decoder = WebpSanStreamFactoryDecoder()

  @Test
  fun `handles - non-webp data is not blocked`() {
    val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64)

    assertThat(decoder.handles(png.asStreamFactory(), Options())).isFalse()
  }

  @Test
  fun `handles - data too short to have a magic number is blocked`() {
    assertThat(decoder.handles(byteArrayOf(0x52, 0x49, 0x46, 0x46).asStreamFactory(), Options())).isTrue()
  }

  @Test
  fun `handles - valid webp is not blocked`() {
    assertThat(decoder.handles(VALID_WEBP.asStreamFactory(), Options())).isFalse()
  }

  @Test
  fun `handles - malformed webp is blocked`() {
    val malformed = "RIFF".toByteArray() + byteArrayOf(0x40, 0x00, 0x00, 0x00) + "WEBP".toByteArray() + ByteArray(56)

    assertThat(decoder.handles(malformed.asStreamFactory(), Options())).isTrue()
  }

  @Test
  fun `handles - unreadable stream is blocked`() {
    val factory = object : InputStreamFactory {
      override fun create(): InputStream = throw IllegalStateException("nope")
    }

    assertThat(decoder.handles(factory, Options())).isTrue()
  }

  private fun ByteArray.asStreamFactory(): InputStreamFactory {
    return object : InputStreamFactory {
      override fun create(): InputStream = ByteArrayInputStream(this@asStreamFactory)
    }
  }
}
