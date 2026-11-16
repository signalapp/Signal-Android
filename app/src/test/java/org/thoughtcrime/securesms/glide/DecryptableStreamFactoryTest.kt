/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.bitmaps.BitmapUtil
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class DecryptableStreamFactoryTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private val uri = Uri.parse("content://org.thoughtcrime.securesms/part/1")

  @Before
  fun setUp() {
    mockkStatic(MediaUtil::class, PartAuthority::class, BitmapUtil::class)
  }

  @After
  fun tearDown() {
    unmockkStatic(MediaUtil::class, PartAuthority::class, BitmapUtil::class)
  }

  @Test
  fun `video frame is extracted once across repeated creates`() {
    every { MediaUtil.hasVideoThumbnail(any(), any()) } returns true
    every { MediaUtil.getVideoThumbnail(any(), any(), any()) } answers { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) }

    val factory = DecryptableStreamFactory(uri)
    val reads = (1..4).map { factory.create().use { it.readBytes() } }

    verify(exactly = 1) { MediaUtil.hasVideoThumbnail(any(), any()) }
    verify(exactly = 1) { MediaUtil.getVideoThumbnail(any(), any(), any()) }
    assertThat(reads.all { it.contentEquals(reads.first()) }).isTrue()
  }

  @Test
  fun `size check runs once across repeated creates`() {
    every { MediaUtil.hasVideoThumbnail(any(), any()) } returns false
    every { PartAuthority.isBlobUri(any()) } returns false
    every { PartAuthority.getAttachmentThumbnailStream(any(), any()) } answers { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
    every { BitmapUtil.getDimensions(any()) } returns (100 to 100)

    val factory = DecryptableStreamFactory(uri)
    repeat(4) { factory.create().close() }

    verify(exactly = 1) { BitmapUtil.getDimensions(any()) }
    verify(exactly = 5) { PartAuthority.getAttachmentThumbnailStream(any(), any()) }
  }

  @Test
  fun `failed video extraction is not retried`() {
    every { MediaUtil.hasVideoThumbnail(any(), any()) } returns true
    every { MediaUtil.getVideoThumbnail(any(), any(), any()) } returns null
    every { PartAuthority.isAttachmentUri(any()) } returns false
    every { PartAuthority.isBlobUri(any()) } returns false
    every { PartAuthority.getAttachmentThumbnailStream(any(), any()) } answers { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
    every { BitmapUtil.getDimensions(any()) } returns (100 to 100)

    val factory = DecryptableStreamFactory(uri)
    val reads = (1..3).map { factory.create().use { it.readBytes() } }

    verify(exactly = 1) { MediaUtil.getVideoThumbnail(any(), any(), any()) }
    assertThat(reads.first().size).isEqualTo(3)
  }

  @Test
  fun `image content type hint skips the video lookup`() {
    every { PartAuthority.isBlobUri(any()) } returns false
    every { PartAuthority.getAttachmentThumbnailStream(any(), any()) } answers { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
    every { BitmapUtil.getDimensions(any()) } returns (100 to 100)

    val factory = DecryptableStreamFactory(uri, contentType = "image/jpeg")
    repeat(2) { factory.create().close() }

    verify(exactly = 0) { MediaUtil.hasVideoThumbnail(any(), any()) }
    verify(exactly = 0) { MediaUtil.getVideoThumbnail(any(), any(), any()) }
  }

  @Test
  fun `video content type hint extracts a frame without the video lookup`() {
    every { MediaUtil.getVideoThumbnail(any(), any(), any()) } answers { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) }

    val factory = DecryptableStreamFactory(uri, contentType = "video/mp4")
    repeat(2) { factory.create().close() }

    verify(exactly = 0) { MediaUtil.hasVideoThumbnail(any(), any()) }
    verify(exactly = 1) { MediaUtil.getVideoThumbnail(any(), any(), any()) }
  }
}
