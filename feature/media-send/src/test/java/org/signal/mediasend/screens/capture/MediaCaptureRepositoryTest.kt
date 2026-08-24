/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import android.app.Application
import android.content.Context
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.media.Media
import org.signal.core.util.ContentTypeUtil
import org.signal.core.util.SeekableFileDescriptor
import org.signal.core.util.contentproviders.BlobProvider
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Covers how a capture is described once it has been written out, and what happens when it cannot be.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MediaCaptureRepositoryTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val mimeType = slot<String>()

  private val imageBuilder: BlobProvider.MemoryBlobBuilder = mockk {
    every { withMimeType(capture(mimeType)) } returns this
    every { createForSingleSessionOnDisk(any()) } returns BLOB_URI
  }

  private val streamBuilder: BlobProvider.BlobBuilder = mockk {
    every { withMimeType(capture(mimeType)) } returns this
    every { createForSingleSessionOnDisk(any()) } returns BLOB_URI
  }

  private val blobs: BlobProvider = mockk {
    every { forData(any<ByteArray>()) } returns imageBuilder
    every { forData(any<InputStream>(), any()) } returns streamBuilder
  }

  private val repository = MediaCaptureRepository(context, blobs)

  @Test
  fun `Given a captured image, when written out, then it describes the blob it was written to`() = runTest {
    val media = repository.writeCapturedImage(data = byteArrayOf(1, 2, 3, 4), width = 640, height = 480)

    assertThat(media?.uri).isEqualTo(BLOB_URI)
    assertThat(media?.contentType).isEqualTo(ContentTypeUtil.IMAGE_JPEG)
    assertThat(mimeType.captured).isEqualTo(ContentTypeUtil.IMAGE_JPEG)
    assertThat(media?.width).isEqualTo(640)
    assertThat(media?.height).isEqualTo(480)
    assertThat(media?.size).isEqualTo(4L)
    assertThat(media?.bucketId).isEqualTo(Media.ALL_MEDIA_BUCKET_ID)
  }

  @Test
  fun `Given the blob cannot be written, when an image is captured, then nothing comes back`() = runTest {
    every { imageBuilder.createForSingleSessionOnDisk(any()) } throws IOException("no space")

    assertThat(repository.writeCapturedImage(data = byteArrayOf(1), width = 1, height = 1)).isNull()
  }

  @Test
  fun `Given a recording, when written out, then it carries the recording's length and leaves its dimensions to population`() = runTest {
    val recording = recording(byteCount = 2_048)

    val media = repository.writeCapturedVideo(recording)

    assertThat(media?.uri).isEqualTo(BLOB_URI)
    assertThat(media?.contentType).isEqualTo(VideoConstants.RECORDED_VIDEO_CONTENT_TYPE)
    assertThat(mimeType.captured).isEqualTo(VideoConstants.RECORDED_VIDEO_CONTENT_TYPE)
    assertThat(media?.size).isEqualTo(2_048L)
    assertThat(media?.width).isEqualTo(0)
    assertThat(media?.height).isEqualTo(0)
  }

  @Test
  fun `Given a recording, when written out, then the descriptor is closed`() = runTest {
    val recording = recording(byteCount = 8)

    repository.writeCapturedVideo(recording)

    assertThat(recording.isClosed).isTrue()
  }

  /**
   * The descriptor is the caller's to close, and a recording that fails part way through is exactly the case where
   * leaking it would go unnoticed.
   */
  @Test
  fun `Given the blob cannot be written, when a recording is captured, then nothing comes back and the descriptor is still closed`() = runTest {
    every { streamBuilder.createForSingleSessionOnDisk(any()) } throws IOException("no space")
    val recording = recording(byteCount = 8)

    assertThat(repository.writeCapturedVideo(recording)).isNull()
    assertThat(recording.isClosed).isTrue()
  }

  /** A descriptor over a real file, since the length is read off the descriptor's own channel. */
  private fun recording(byteCount: Int): FakeRecording {
    val file = temporaryFolder.newFile()
    file.writeBytes(ByteArray(byteCount))
    return FakeRecording(FileInputStream(file))
  }

  private class FakeRecording(private val stream: FileInputStream) : SeekableFileDescriptor {
    var isClosed: Boolean = false
      private set

    override val fileDescriptor: FileDescriptor get() = stream.fd
    override val parcelFd get() = throw UnsupportedOperationException()

    override fun close() {
      isClosed = true
      stream.close()
    }
  }

  private companion object {
    private val BLOB_URI = "content://blob/capture".toUri()
  }
}
