/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.bumptech.glide.load.engine

import android.app.Application
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.data.DataRewinder
import com.bumptech.glide.load.resource.SimpleResource
import com.bumptech.glide.util.pool.FactoryPools
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.glide.cache.WebpSanDecoder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Pins the Glide behavior that [WebpSanDecoder] relies on: rejecting an image has to stop the whole decode path, not just skip to the next decoder registered
 * for the same data/resource pair. Lives in Glide's package because [DecodePath.DecodeCallback] is package-private.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class WebpSanDecodePathTest {

  companion object {
    /** An 8x8 lossless webp. */
    private val VALID_WEBP = byteArrayOf(
      82, 73, 70, 70, 28, 0, 0, 0, 87, 69, 66, 80, 86, 80, 56, 76, 15, 0,
      0, 0, 47, 7, -64, 1, 0, 7, 16, -3, -113, -2, 7, 34, -94, -1, 1, 0
    )

    private val MALFORMED_WEBP = "RIFF".toByteArray() + byteArrayOf(0x40, 0x00, 0x00, 0x00) + "WEBP".toByteArray() + ByteArray(56)
  }

  private val sentinel = SentinelDecoder()

  @Test
  fun `a rejected webp never reaches a later decoder`() {
    val path = decodePath(WebpSanDecoder(), sentinel)

    assertThrows(GlideException::class.java) {
      path.decode(MALFORMED_WEBP.asRewinder(), 100, 100, Options()) { it }
    }

    assertThat(sentinel.decoded).isFalse()
  }

  @Test
  fun `an accepted webp still reaches a later decoder`() {
    val path = decodePath(WebpSanDecoder(), sentinel)

    path.decode(VALID_WEBP.asRewinder(), 100, 100, Options()) { it }

    assertThat(sentinel.decoded).isTrue()
  }

  @Test
  fun `an IOException from the gate would let a later decoder run`() {
    val path = decodePath(IoExceptionDecoder(), sentinel)

    path.decode(MALFORMED_WEBP.asRewinder(), 100, 100, Options()) { it }

    assertThat(sentinel.decoded).isTrue()
  }

  private fun decodePath(vararg decoders: ResourceDecoder<InputStream, String>): DecodePath<InputStream, String, String> {
    return DecodePath(
      InputStream::class.java,
      String::class.java,
      String::class.java,
      decoders.toList(),
      { toTranscode, _ -> toTranscode },
      FactoryPools.threadSafeList()
    )
  }

  private fun ByteArray.asRewinder(): DataRewinder<InputStream> {
    return object : DataRewinder<InputStream> {
      override fun rewindAndGet(): InputStream = ByteArrayInputStream(this@asRewinder)
      override fun cleanup() = Unit
    }
  }

  private class SentinelDecoder : ResourceDecoder<InputStream, String> {
    var decoded = false

    override fun handles(source: InputStream, options: Options): Boolean = true

    override fun decode(source: InputStream, width: Int, height: Int, options: Options): Resource<String> {
      decoded = true
      return SimpleResource("decoded")
    }
  }

  private class IoExceptionDecoder : ResourceDecoder<InputStream, String> {
    override fun handles(source: InputStream, options: Options): Boolean = true

    override fun decode(source: InputStream, width: Int, height: Int, options: Options): Resource<String> = throw IOException("rejected")
  }
}
