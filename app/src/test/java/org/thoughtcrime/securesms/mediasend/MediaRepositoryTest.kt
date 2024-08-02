package org.thoughtcrime.securesms.mediasend

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.AttachmentTable.TransformProperties
import org.thoughtcrime.securesms.testutil.EmptyLogger
import org.thoughtcrime.securesms.util.MediaUtil
import java.util.Optional

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MediaRepositoryTest {

  @Rule
  @JvmField
  val mockitoRule: MockitoRule = MockitoJUnit.rule()

  @Mock
  private lateinit var staticMediaUtilMock: MockedStatic<MediaUtil>

  private lateinit var context: Context

  @Before
  fun setUp() {
    Log.initialize(EmptyLogger())

    context = ApplicationProvider.getApplicationContext()
    `when`(MediaUtil.isOctetStream(MediaUtil.OCTET)).thenReturn(true)
  }

  @Test
  fun `Given a valid mime type, do not change media`() {
    // GIVEN
    val media = buildMedia(mimeType = MediaUtil.IMAGE_JPEG)

    // WHEN
    val result: Media = MediaRepository.fixMimeType(context, media)

    // THEN
    assertEquals(media, result)
  }

  @Test
  fun `Given an invalid mime type, change media via MediaUtil`() {
    // GIVEN
    val media = buildMedia(mimeType = MediaUtil.OCTET)

    // WHEN
    `when`(MediaUtil.getMimeType(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(MediaUtil.IMAGE_JPEG)
    val result: Media = MediaRepository.fixMimeType(context, media)

    // THEN
    assertEquals(MediaUtil.IMAGE_JPEG, result.contentType)
  }

  @Test
  fun `Given an invalid mime type with sizing info but no duration, guess image based`() {
    // GIVEN
    val media = buildMedia(
      mimeType = MediaUtil.OCTET,
      width = 100,
      height = 100,
      size = 100
    )

    // WHEN
    val result: Media = MediaRepository.fixMimeType(context, media)

    // THEN
    assertEquals(MediaUtil.IMAGE_JPEG, result.contentType)
  }

  @Test
  fun `Given an invalid mime type with sizing info and duration, guess video based`() {
    // GIVEN
    val media = buildMedia(
      mimeType = MediaUtil.OCTET,
      width = 100,
      height = 100,
      size = 100,
      duration = 100
    )

    // WHEN
    val result: Media = MediaRepository.fixMimeType(context, media)

    // THEN
    assertEquals(MediaUtil.VIDEO_UNSPECIFIED, result.contentType)
  }

  private fun buildMedia(
    uri: Uri = Uri.EMPTY,
    mimeType: String = "",
    date: Long = 0,
    width: Int = 0,
    height: Int = 0,
    size: Long = 0,
    duration: Long = 0,
    borderless: Boolean = false,
    videoGif: Boolean = false,
    bucketId: Optional<String> = Optional.empty(),
    caption: Optional<String> = Optional.empty(),
    transformProperties: Optional<TransformProperties> = Optional.empty(),
    fileName: Optional<String> = Optional.empty()
  ): Media {
    return Media(
      uri,
      mimeType,
      date,
      width,
      height,
      size,
      duration,
      borderless,
      videoGif,
      bucketId,
      caption,
      transformProperties,
      fileName
    )
  }
}
