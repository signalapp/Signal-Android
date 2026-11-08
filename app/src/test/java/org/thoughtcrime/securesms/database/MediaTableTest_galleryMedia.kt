/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import android.database.Cursor
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.signal.core.util.requireLong
import org.thoughtcrime.securesms.database.MediaTable.Sorting
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger

/**
 * Coverage for the gallery media queries: the long-standing [MediaTable.getGalleryMediaForThread], and the
 * viewable variants that layer an additional renderability filter on top of the same base query. The two share
 * [MediaTable.Sorting] and the gallery filter, so both are exercised here to catch drift between them.
 */
@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MediaTableTest_galleryMedia {

  @get:Rule
  val recipients = RecipientTestRule()

  private val fixtures = GalleryMediaFixtures(recipients)

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  // region getGalleryMediaForThread

  @Test
  fun `given mixed content types, when I get gallery media, then only images and videos are included`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId)

    val image = fixtures.insertAttachment(messageId, contentType = "image/jpeg")
    val video = fixtures.insertAttachment(messageId, contentType = "video/mp4")
    fixtures.insertAttachment(messageId, contentType = "image/svg+xml")
    fixtures.insertAttachment(messageId, contentType = "audio/aac")
    fixtures.insertAttachment(messageId, contentType = "application/pdf")

    assertThat(galleryIds(threadId)).containsExactly(video, image)
  }

  @Test
  fun `given a video still downloading with an incremental digest, when I get gallery media, then it is included`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId)

    val video = fixtures.insertAttachment(
      messageId = messageId,
      contentType = "video/mp4",
      transferState = AttachmentTable.TRANSFER_PROGRESS_PENDING,
      dataFile = null,
      incrementalDigest = byteArrayOf(1, 2, 3)
    )

    assertThat(galleryIds(threadId)).containsExactly(video)
  }

  @Test
  fun `given an attachment with only a thumbnail, when I get gallery media, then it is included`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId)

    val attachment = fixtures.insertAttachment(
      messageId = messageId,
      transferState = AttachmentTable.TRANSFER_NEEDS_RESTORE,
      dataFile = null,
      thumbnailFile = "/thumbnail/file"
    )

    assertThat(galleryIds(threadId)).containsExactly(attachment)
  }

  @Test
  fun `given an offloaded attachment, when I get gallery media, then it is included`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId)

    val attachment = fixtures.insertAttachment(
      messageId = messageId,
      transferState = AttachmentTable.TRANSFER_RESTORE_OFFLOADED,
      dataFile = null
    )

    assertThat(galleryIds(threadId)).containsExactly(attachment)
  }

  @Test
  fun `given an image with nothing on disk, when I get gallery media, then it is excluded`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId)

    fixtures.insertAttachment(
      messageId = messageId,
      transferState = AttachmentTable.TRANSFER_PROGRESS_PENDING,
      dataFile = null
    )

    assertThat(galleryIds(threadId)).isEmpty()
  }

  @Test
  fun `given link preview and scheduled messages, when I get gallery media, then their attachments are excluded`() {
    val threadId = fixtures.createThread()
    val normalMessage = fixtures.insertMessage(threadId)
    val linkPreviewMessage = fixtures.insertMessage(threadId, linkPreviews = "[]")
    val scheduledMessage = fixtures.insertMessage(threadId, scheduledDate = 1000)

    val normal = fixtures.insertAttachment(normalMessage)
    fixtures.insertAttachment(linkPreviewMessage)
    fixtures.insertAttachment(scheduledMessage)

    assertThat(galleryIds(threadId)).containsExactly(normal)
  }

  @Test
  fun `given media in two threads, when I get gallery media, then only the requested thread is included`() {
    val threadA = fixtures.createThread()
    val threadB = fixtures.createThread()

    val inThreadA = fixtures.insertAttachment(fixtures.insertMessage(threadA))
    val inThreadB = fixtures.insertAttachment(fixtures.insertMessage(threadB))

    assertThat(galleryIds(threadA)).containsExactly(inThreadA)
    assertThat(galleryIds(threadB)).containsExactly(inThreadB)
    assertThat(galleryIds(MediaTable.ALL_THREADS.toLong())).containsExactly(inThreadB, inThreadA)
  }

  @Test
  fun `given media across messages, when I get gallery media, then each sorting orders correctly`() {
    val threadId = fixtures.createThread()
    val olderMessage = fixtures.insertMessage(threadId)
    val newerMessage = fixtures.insertMessage(threadId)

    val small = fixtures.insertAttachment(olderMessage, dataSize = 10)
    val large = fixtures.insertAttachment(newerMessage, dataSize = 100)
    val medium = fixtures.insertAttachment(newerMessage, dataSize = 50)

    assertThat(galleryIds(threadId, Sorting.Newest)).containsExactly(medium, large, small)
    assertThat(galleryIds(threadId, Sorting.Oldest)).containsExactly(small, large, medium)
    assertThat(galleryIds(threadId, Sorting.Largest)).containsExactly(large, medium, small)
  }

  @Test
  fun `given a message with explicit display orders, when I get gallery media, then display order wins within the message`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId)

    val shownSecond = fixtures.insertAttachment(messageId, displayOrder = 1)
    val shownFirst = fixtures.insertAttachment(messageId, displayOrder = 2)

    assertThat(galleryIds(threadId, Sorting.Newest)).containsExactly(shownFirst, shownSecond)
    assertThat(galleryIds(threadId, Sorting.Oldest)).containsExactly(shownFirst, shownSecond)
  }

  @Test
  fun `given more media than the limit, when I get gallery media, then only the first rows are returned`() {
    val threadId = fixtures.createThread()
    val first = fixtures.insertAttachment(fixtures.insertMessage(threadId))
    val second = fixtures.insertAttachment(fixtures.insertMessage(threadId))
    fixtures.insertAttachment(fixtures.insertMessage(threadId))

    assertThat(galleryIds(threadId, Sorting.Oldest, limit = 2)).containsExactly(first, second)
  }

  // endregion

  // region viewable gallery media

  @Test
  fun `given gallery media in various states, when I get viewable gallery media, then only renderable rows are included`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId)

    val done = fixtures.insertAttachment(messageId, transferState = AttachmentTable.TRANSFER_PROGRESS_DONE)
    val started = fixtures.insertAttachment(messageId, transferState = AttachmentTable.TRANSFER_PROGRESS_STARTED)
    val restoredThumbnail = fixtures.insertAttachment(
      messageId = messageId,
      transferState = AttachmentTable.TRANSFER_NEEDS_RESTORE,
      dataFile = null,
      thumbnailFile = "/thumbnail/file",
      thumbnailRestoreState = AttachmentTable.ThumbnailRestoreState.FINISHED
    )
    val pendingVideo = fixtures.insertAttachment(
      messageId = messageId,
      contentType = "video/mp4",
      transferState = AttachmentTable.TRANSFER_PROGRESS_PENDING,
      dataFile = null,
      incrementalDigest = byteArrayOf(1, 2, 3)
    )
    val offloaded = fixtures.insertAttachment(
      messageId = messageId,
      transferState = AttachmentTable.TRANSFER_RESTORE_OFFLOADED,
      dataFile = null
    )

    assertThat(galleryIds(threadId)).containsExactly(offloaded, pendingVideo, restoredThumbnail, started, done)
    assertThat(viewableIds(threadId)).containsExactly(restoredThumbnail, started, done)
  }

  @Test
  fun `given a mix of viewable and non-viewable media, when I count viewable gallery media, then the count matches the query`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId)

    fixtures.insertAttachment(messageId, transferState = AttachmentTable.TRANSFER_PROGRESS_DONE)
    fixtures.insertAttachment(messageId, transferState = AttachmentTable.TRANSFER_PROGRESS_STARTED)
    fixtures.insertAttachment(messageId, transferState = AttachmentTable.TRANSFER_RESTORE_OFFLOADED, dataFile = null)

    assertThat(SignalDatabase.media.getViewableGalleryMediaCountForThread(threadId)).isEqualTo(2)
    assertThat(SignalDatabase.media.getViewableGalleryMediaCountForThread(fixtures.createThread())).isEqualTo(0)
  }

  @Test
  fun `given media across messages with display orders, when I get viewable gallery media ids, then they match the full query order`() {
    val threadId = fixtures.createThread()
    val olderMessage = fixtures.insertMessage(threadId)
    val newerMessage = fixtures.insertMessage(threadId)

    fixtures.insertAttachment(olderMessage, dataSize = 10)
    fixtures.insertAttachment(newerMessage, dataSize = 50, displayOrder = 1)
    fixtures.insertAttachment(newerMessage, dataSize = 100, displayOrder = 2)
    fixtures.insertAttachment(newerMessage, dataSize = 25)

    for (sorting in Sorting.entries) {
      for ((index, id) in viewableIds(threadId, sorting).withIndex()) {
        assertThat(SignalDatabase.media.getViewableGalleryMediaPositionAndCount(threadId, sorting, id)?.position, name = "sorting=$sorting id=$id").isEqualTo(index)
      }
    }
  }

  @Test
  fun `given five viewable items, when I page viewable gallery media, then the pages are contiguous`() {
    val threadId = fixtures.createThread()
    repeat(5) { fixtures.insertAttachment(fixtures.insertMessage(threadId)) }

    val all = viewableIds(threadId)
    val paged = viewableIds(threadId, limit = 2) +
      viewableIds(threadId, limit = 2, offset = 2) +
      viewableIds(threadId, limit = 2, offset = 4)

    assertThat(all.size).isEqualTo(5)
    assertThat(paged).isEqualTo(all)
  }

  @Test
  fun `given an attachment id, when I get viewable gallery media, then only that row is returned`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId)

    val target = fixtures.insertAttachment(messageId)
    fixtures.insertAttachment(messageId)
    val nonViewable = fixtures.insertAttachment(messageId, transferState = AttachmentTable.TRANSFER_RESTORE_OFFLOADED, dataFile = null)

    assertThat(SignalDatabase.media.getViewableGalleryMediaForAttachment(threadId, target)?.attachment?.attachmentId?.id).isEqualTo(target)
    assertThat(SignalDatabase.media.getViewableGalleryMediaForAttachment(threadId, nonViewable)).isNull()
    assertThat(SignalDatabase.media.getViewableGalleryMediaPositionAndCount(threadId, Sorting.Newest, nonViewable)).isNull()
  }

  @Test
  fun `given media in two threads, when I use all threads for viewable queries, then all threads are spanned`() {
    val inThreadA = fixtures.insertAttachment(fixtures.insertMessage(fixtures.createThread()))
    val inThreadB = fixtures.insertAttachment(fixtures.insertMessage(fixtures.createThread()))
    val allThreads = MediaTable.ALL_THREADS.toLong()

    assertThat(viewableIds(allThreads)).containsExactly(inThreadB, inThreadA)
    assertThat(SignalDatabase.media.getViewableGalleryMediaCountForThread(allThreads)).isEqualTo(2)
    assertThat(SignalDatabase.media.getViewableGalleryMediaPositionAndCount(allThreads, Sorting.Oldest, inThreadA)?.position).isEqualTo(0)
    assertThat(SignalDatabase.media.getViewableGalleryMediaPositionAndCount(allThreads, Sorting.Oldest, inThreadB)?.position).isEqualTo(1)
  }

  @Test
  fun `given albums interleaved with another thread, when I page viewable gallery media, then every page matches the gallery query`() {
    val otherThread = fixtures.createThread()
    val threadId = fixtures.createThread()
    repeat(12) { index ->
      fixtures.insertAttachment(fixtures.insertMessage(otherThread))
      val messageId = fixtures.insertMessage(threadId)
      repeat(1 + index % 3) { order -> fixtures.insertAttachment(messageId, displayOrder = order) }
    }

    for (sorting in Sorting.entries) {
      val all = galleryIds(threadId, sorting)
      val paged = (0 until all.size step 5).flatMap { viewableIds(threadId, sorting, limit = 5, offset = it) }

      assertThat(all.size, name = "sorting=$sorting").isEqualTo(24)
      assertThat(viewableIds(threadId, sorting), name = "sorting=$sorting").isEqualTo(all)
      assertThat(paged, name = "sorting=$sorting").isEqualTo(all)
    }
  }

  @Test
  fun `given attachments with equal sizes, when I sort by largest, then position and query order agree`() {
    val threadId = fixtures.createThread()
    repeat(4) { fixtures.insertAttachment(fixtures.insertMessage(threadId), dataSize = 100) }

    val all = viewableIds(threadId, Sorting.Largest)
    for ((index, id) in all.withIndex()) {
      assertThat(SignalDatabase.media.getViewableGalleryMediaPositionAndCount(threadId, Sorting.Largest, id)?.position, name = "id=$id").isEqualTo(index)
    }
  }

  @Test
  fun `given viewable media, when I get position and count, then both match the full query`() {
    val threadId = fixtures.createThread()
    val olderMessage = fixtures.insertMessage(threadId)
    val newerMessage = fixtures.insertMessage(threadId)

    fixtures.insertAttachment(olderMessage, dataSize = 10)
    fixtures.insertAttachment(newerMessage, dataSize = 50, displayOrder = 1)
    fixtures.insertAttachment(newerMessage, dataSize = 100, displayOrder = 2)
    fixtures.insertAttachment(newerMessage, dataSize = 25)
    val nonViewable = fixtures.insertAttachment(newerMessage, transferState = AttachmentTable.TRANSFER_PROGRESS_PENDING, dataFile = null)

    for (sorting in Sorting.entries) {
      val all = viewableIds(threadId, sorting)
      for ((index, id) in all.withIndex()) {
        val result = SignalDatabase.media.getViewableGalleryMediaPositionAndCount(threadId, sorting, id)!!
        assertThat(result.position, name = "sorting=$sorting id=$id").isEqualTo(index)
        assertThat(result.count, name = "sorting=$sorting id=$id").isEqualTo(all.size)
      }
    }

    assertThat(SignalDatabase.media.getViewableGalleryMediaPositionAndCount(threadId, Sorting.Newest, nonViewable)).isNull()
  }

  // endregion

  // region helpers

  private fun galleryIds(threadId: Long, sorting: Sorting = Sorting.Newest, limit: Int = 0): List<Long> {
    return SignalDatabase.media.getGalleryMediaForThread(threadId, sorting, limit).readIds()
  }

  private fun viewableIds(threadId: Long, sorting: Sorting = Sorting.Newest, limit: Int = 0, offset: Int = 0): List<Long> {
    return SignalDatabase.media.getViewableGalleryMediaForThread(threadId, sorting, limit, offset).map { it.attachment!!.attachmentId.id }
  }

  private fun Cursor.readIds(): List<Long> {
    return use {
      val ids = mutableListOf<Long>()
      while (moveToNext()) {
        ids += requireLong(AttachmentTable.ID)
      }
      ids
    }
  }

  // endregion
}
