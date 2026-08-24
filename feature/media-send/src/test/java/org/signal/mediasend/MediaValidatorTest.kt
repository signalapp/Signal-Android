/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.app.Application
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.media.Media
import org.signal.core.util.ContentTypeUtil
import org.thoughtcrime.securesms.video.TranscodingConfig

/**
 * Covers [MediaValidator.filterMedia]'s two jobs: deciding what survives, and reporting which item did not and why.
 *
 * Content-type classification runs against the real [ContentTypeUtil], so these also pin down which mime types the
 * filtering actually accepts. Nothing is mocked -- story restrictions arrive as a plain predicate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MediaValidatorTest {

  private val constraints: MediaConstraints = TestMediaConstraints()

  @Test
  fun `Given all valid media, when filtering, then everything survives with no error`() {
    val media = listOf(
      media(uri = "content://1", contentType = ContentTypeUtil.IMAGE_JPEG),
      media(uri = "content://2", contentType = ContentTypeUtil.VIDEO_MP4, size = VIDEO_MAX - 1),
      media(uri = "content://3", contentType = PDF, size = DOCUMENT_MAX - 1)
    )

    val result = filter(media)

    assertEquals(media, result.filteredMedia)
    assertNull(result.filterError)
  }

  @Test
  fun `Given an oversized video among valid media, when filtering, then the video is reported as the offender`() {
    val image = media(uri = "content://image", contentType = ContentTypeUtil.IMAGE_JPEG)
    val oversizedVideo = media(uri = "content://video", contentType = ContentTypeUtil.VIDEO_MP4, size = VIDEO_MAX + 1)

    val result = filter(listOf(image, oversizedVideo))

    assertEquals(listOf(image), result.filteredMedia)
    assertEquals(MediaValidator.FilterError.ItemTooLarge(oversizedVideo), result.filterError)
  }

  /**
   * Regression: the filtering treats valid as `size < max`, so an item sitting exactly on the limit is dropped. A
   * previous re-derivation of the offender elsewhere asked `size > max` and so could not name this item at all.
   */
  @Test
  fun `Given a video exactly at the size limit, when filtering, then it is dropped and named as the offender`() {
    val exactlyAtLimit = media(uri = "content://video", contentType = ContentTypeUtil.VIDEO_MP4, size = VIDEO_MAX)
    val image = media(uri = "content://image", contentType = ContentTypeUtil.IMAGE_JPEG)

    val result = filter(listOf(image, exactlyAtLimit))

    assertEquals(listOf(image), result.filteredMedia)
    assertEquals(MediaValidator.FilterError.ItemTooLarge(exactlyAtLimit), result.filterError)
  }

  /**
   * Regression: non-gif images are accepted at any size, so a huge image is not the offender when something else was
   * actually dropped. A previous re-derivation applied an image size limit the filtering never applies, and would
   * name the image here because it sorts first.
   */
  @Test
  fun `Given a huge image and an oversized video, when filtering, then the video is the offender and the image survives`() {
    val hugeImage = media(uri = "content://image", contentType = ContentTypeUtil.IMAGE_JPEG, size = IMAGE_MAX * 100L)
    val oversizedVideo = media(uri = "content://video", contentType = ContentTypeUtil.VIDEO_MP4, size = VIDEO_MAX + 1)

    val result = filter(listOf(hugeImage, oversizedVideo))

    assertEquals(listOf(hugeImage), result.filteredMedia)
    assertEquals(MediaValidator.FilterError.ItemTooLarge(oversizedVideo), result.filterError)
  }

  @Test
  fun `Given an unsupported type among valid media, when filtering, then it is reported as an invalid type`() {
    val image = media(uri = "content://image", contentType = ContentTypeUtil.IMAGE_JPEG)
    val longText = media(uri = "content://text", contentType = ContentTypeUtil.LONG_TEXT)

    val result = filter(listOf(image, longText))

    assertEquals(listOf(image), result.filteredMedia)
    assertEquals(MediaValidator.FilterError.ItemInvalidType(longText), result.filterError)
  }

  @Test
  fun `Given an oversized gif, when filtering, then it is dropped and named as the offender`() {
    val oversizedGif = media(uri = "content://gif", contentType = ContentTypeUtil.IMAGE_GIF, size = GIF_MAX + 1)

    val result = filter(listOf(oversizedGif, media(uri = "content://image", contentType = ContentTypeUtil.IMAGE_JPEG)))

    assertEquals(MediaValidator.FilterError.ItemTooLarge(oversizedGif), result.filterError)
  }

  @Test
  fun `Given an oversized document, when filtering, then it is dropped and named as the offender`() {
    val oversizedDocument = media(uri = "content://doc", contentType = PDF, size = DOCUMENT_MAX + 1)

    val result = filter(listOf(oversizedDocument, media(uri = "content://image", contentType = ContentTypeUtil.IMAGE_JPEG)))

    assertEquals(MediaValidator.FilterError.ItemTooLarge(oversizedDocument), result.filterError)
  }

  @Test
  fun `Given nothing survives filtering, when filtering, then NoItems carries the underlying reason`() {
    val oversizedVideo = media(uri = "content://video", contentType = ContentTypeUtil.VIDEO_MP4, size = VIDEO_MAX + 1)

    val result = filter(listOf(oversizedVideo))

    val error = result.filterError as MediaValidator.FilterError.NoItems
    assertEquals(MediaValidator.FilterError.ItemTooLarge(oversizedVideo), error.cause)
    assertEquals(emptyList<Media>(), result.filteredMedia)
  }

  @Test
  fun `Given only an unsupported type, when filtering, then NoItems carries the invalid type reason`() {
    val longText = media(uri = "content://text", contentType = ContentTypeUtil.LONG_TEXT)

    val result = filter(listOf(longText))

    val error = result.filterError as MediaValidator.FilterError.NoItems
    assertEquals(MediaValidator.FilterError.ItemInvalidType(longText), error.cause)
  }

  @Test
  fun `Given no media at all, when filtering, then NoItems has no underlying reason`() {
    val result = filter(emptyList())

    val error = result.filterError as MediaValidator.FilterError.NoItems
    assertNull(error.cause)
  }

  @Test
  fun `Given more media than the max selection, when filtering, then the list is truncated and TooManyItems is reported`() {
    val media = (1..5).map { media(uri = "content://$it", contentType = ContentTypeUtil.IMAGE_JPEG) }

    val result = filter(media, maxSelection = 3)

    assertEquals(media.take(3), result.filteredMedia)
    assertEquals(MediaValidator.FilterError.TooManyItems, result.filterError)
  }

  @Test
  fun `Given both an oversized item and too many survivors, when filtering, then TooManyItems wins`() {
    val media = (1..5).map { media(uri = "content://$it", contentType = ContentTypeUtil.IMAGE_JPEG) } +
      media(uri = "content://video", contentType = ContentTypeUtil.VIDEO_MP4, size = VIDEO_MAX + 1)

    val result = filter(media, maxSelection = 3)

    assertEquals(MediaValidator.FilterError.TooManyItems, result.filterError)
  }

  @Test
  fun `Given a story with media that cannot be sent, when filtering, then it is dropped and named as the offender`() {
    val validForStory = media(uri = "content://image", contentType = ContentTypeUtil.IMAGE_JPEG)
    val tooLongVideo = media(uri = "content://video", contentType = ContentTypeUtil.VIDEO_MP4, size = VIDEO_MAX - 1)

    val result = filter(listOf(validForStory, tooLongVideo), isStory = true) { it.uri != tooLongVideo.uri }

    assertEquals(listOf(validForStory), result.filteredMedia)
    assertEquals(MediaValidator.FilterError.ItemTooLarge(tooLongVideo), result.filterError)
  }

  @Test
  fun `Given a non-story send, when filtering, then the story check is not consulted`() {
    val media = media(uri = "content://video", contentType = ContentTypeUtil.VIDEO_MP4, size = VIDEO_MAX - 1)

    val result = filter(listOf(media), isStory = false) { error("Story check must not run for a non-story send.") }

    assertEquals(listOf(media), result.filteredMedia)
    assertNull(result.filterError)
  }

  /**
   * The offender is matched by uri, which two items in one selection are not guaranteed to differ on. Callers get a
   * null offender rather than a wrong one, which is why the reported item is nullable.
   */
  @Test
  fun `Given a dropped item sharing a uri with a survivor, when filtering, then no offender is named`() {
    val sharedUri = "content://shared"
    val image = media(uri = sharedUri, contentType = ContentTypeUtil.IMAGE_JPEG)
    val longText = media(uri = sharedUri, contentType = ContentTypeUtil.LONG_TEXT)

    val result = filter(listOf(image, longText))

    assertEquals(listOf(image), result.filteredMedia)
    assertEquals(MediaValidator.FilterError.ItemInvalidType(null), result.filterError)
  }

  @Test
  fun `Given media all from one bucket, when filtering, then that bucket is returned`() {
    val media = listOf(
      media(uri = "content://1", contentType = ContentTypeUtil.IMAGE_JPEG, bucketId = "camera"),
      media(uri = "content://2", contentType = ContentTypeUtil.IMAGE_JPEG, bucketId = "camera")
    )

    assertEquals("camera", filter(media).bucketId)
  }

  @Test
  fun `Given media from differing buckets, when filtering, then the all-media bucket is returned`() {
    val media = listOf(
      media(uri = "content://1", contentType = ContentTypeUtil.IMAGE_JPEG, bucketId = "camera"),
      media(uri = "content://2", contentType = ContentTypeUtil.IMAGE_JPEG, bucketId = "downloads")
    )

    assertEquals(Media.ALL_MEDIA_BUCKET_ID, filter(media).bucketId)
  }

  @Test
  fun `Given no surviving media, when filtering, then the all-media bucket is returned`() {
    assertEquals(Media.ALL_MEDIA_BUCKET_ID, filter(emptyList()).bucketId)
  }

  private fun filter(
    media: List<Media>,
    maxSelection: Int = 32,
    isStory: Boolean = false,
    canSendToStory: (Media) -> Boolean = { true }
  ): MediaValidator.FilterResult {
    return MediaValidator.filterMedia(media, constraints, maxSelection, isStory, canSendToStory)
  }

  private fun media(
    uri: String,
    contentType: String,
    size: Long = 1,
    bucketId: String? = Media.ALL_MEDIA_BUCKET_ID
  ): Media {
    return Media(
      uri = Uri.parse(uri),
      contentType = contentType,
      date = 0,
      width = 0,
      height = 0,
      size = size,
      duration = 0,
      isBorderless = false,
      isVideoGif = false,
      bucketId = bucketId,
      caption = null,
      transformProperties = null,
      fileName = null
    )
  }

  /** Fixed, small limits so the boundaries under test are obvious. */
  private class TestMediaConstraints : MediaConstraints() {
    override fun getImageMaxSize(): Int = IMAGE_MAX
    override fun getGifMaxSize(): Long = GIF_MAX
    override fun getVideoMaxSize(): Long = VIDEO_MAX
    override fun getDocumentMaxSize(): Long = DOCUMENT_MAX

    override fun getImageMaxWidth(): Int = 0
    override fun getImageMaxHeight(): Int = 0
    override fun getImageDimensionTargets(): IntArray = intArrayOf()
    override fun getVideoTranscodingSettings(): List<TranscodingConfig.QualityTier> = emptyList()
    override fun getAudioMaxSize(): Long = Long.MAX_VALUE
    override fun getMaxAttachmentSize(): Long = Long.MAX_VALUE
  }

  companion object {
    private const val PDF = "application/pdf"

    private const val IMAGE_MAX = 1_000
    private const val GIF_MAX = 2_000L
    private const val VIDEO_MAX = 3_000L
    private const val DOCUMENT_MAX = 4_000L
  }
}
