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

  // region per-tab counts

  @Test
  fun `given excluded rows and other threads, when I count each tab, then the counts match the rows each query returns`() {
    val threadId = fixtures.createThread()
    val otherThreadId = fixtures.createThread()

    repeat(5) { fixtures.insertMessage(threadId) }
    val messageId = fixtures.insertMessage(threadId)
    fixtures.insertAttachment(messageId, contentType = "image/jpeg")
    fixtures.insertAttachment(messageId, contentType = "video/mp4")
    fixtures.insertAttachment(messageId, contentType = "image/svg+xml")
    fixtures.insertAttachment(messageId, contentType = "audio/aac")
    fixtures.insertAttachment(messageId, contentType = "application/pdf")
    fixtures.insertAttachment(messageId, contentType = "image/jpeg", dataFile = null)
    fixtures.insertAttachment(fixtures.insertMessage(threadId, scheduledDate = 1000), contentType = "image/jpeg")
    fixtures.insertAttachment(fixtures.insertMessage(otherThreadId), contentType = "image/jpeg")

    val media = SignalDatabase.media
    assertThat(media.getGalleryMediaCountForThread(threadId)).isEqualTo(media.getGalleryMediaForThread(threadId, Sorting.Newest).readIds().size)
    assertThat(media.getDocumentMediaCountForThread(threadId)).isEqualTo(media.getDocumentMediaForThread(threadId, Sorting.Newest).readIds().size)
    assertThat(media.getAudioMediaCountForThread(threadId)).isEqualTo(media.getAudioMediaForThread(threadId, Sorting.Newest).readIds().size)
    assertThat(media.getAllMediaCountForThread(threadId)).isEqualTo(media.getAllMediaForThread(threadId, Sorting.Newest).readIds().size)
    assertThat(media.getGalleryMediaCountForThread(threadId)).isEqualTo(2)
  }

  @Test
  fun `given media in several threads, when I count across all threads, then every thread is included`() {
    fixtures.insertAttachment(fixtures.insertMessage(fixtures.createThread()), contentType = "image/jpeg")
    fixtures.insertAttachment(fixtures.insertMessage(fixtures.createThread()), contentType = "image/jpeg")

    val allThreads = MediaTable.ALL_THREADS.toLong()
    assertThat(SignalDatabase.media.getGalleryMediaCountForThread(allThreads)).isEqualTo(2)
    assertThat(SignalDatabase.media.getGalleryMediaForThread(allThreads, Sorting.Newest).readIds().size).isEqualTo(2)
  }

  @Test
  fun `given links in two threads, when I get link media, then only the thread's links come out in received order`() {
    val threadId = fixtures.createThread()
    val otherThreadId = fixtures.createThread()

    val first = fixtures.insertMessage(threadId, linkPreviews = "[]")
    fixtures.insertAttachment(fixtures.insertMessage(threadId), contentType = "image/jpeg")
    val second = fixtures.insertMessage(threadId, linkPreviews = "[]")
    fixtures.insertMessage(otherThreadId, linkPreviews = "[]")

    val media = SignalDatabase.media
    assertThat(media.getLinkMediaForThread(threadId, Sorting.Newest).readMessageIds()).containsExactly(second, first)
    assertThat(media.getLinkMediaForThread(threadId, Sorting.Oldest).readMessageIds()).containsExactly(first, second)
    assertThat(media.getLinkMediaCountForThread(threadId)).isEqualTo(2)
  }

  @Test
  fun `given attachments and links interleaved, when I page all media, then both halves merge in received order`() {
    val threadId = fixtures.createThread()
    val messages = (0 until 6).map { index ->
      if (index % 2 == 0) {
        fixtures.insertMessage(threadId).also { fixtures.insertAttachment(it, contentType = if (index == 2) "application/pdf" else "image/jpeg") }
      } else {
        fixtures.insertMessage(threadId, linkPreviews = "[]")
      }
    }

    val media = SignalDatabase.media
    val newest = media.getAllMediaForThread(threadId, Sorting.Newest).readMessageIds()
    assertThat(newest).isEqualTo(messages.reversed())
    assertThat(media.getAllMediaForThread(threadId, Sorting.Oldest).readMessageIds()).isEqualTo(messages)
    assertThat(media.getAllMediaForThread(threadId, Sorting.Newest, limit = 3, offset = 2).readMessageIds()).isEqualTo(newest.subList(2, 5))
    assertThat(media.getAllMediaCountForThread(threadId)).isEqualTo(6)
  }

  @Test
  fun `given media across threads, when I page each tab across all threads, then each sorting returns the right rows in order`() {
    val first = fixtures.createThread()
    val second = fixtures.createThread()

    val imageMessage = fixtures.insertMessage(first)
    val image = fixtures.insertAttachment(imageMessage, contentType = "image/jpeg", dataSize = 30)
    val pdfMessage = fixtures.insertMessage(second)
    val pdf = fixtures.insertAttachment(pdfMessage, contentType = "application/pdf", dataSize = 50)
    val offloadedMessage = fixtures.insertMessage(first)
    val offloaded = fixtures.insertAttachment(offloadedMessage, contentType = "video/mp4", transferState = AttachmentTable.TRANSFER_RESTORE_OFFLOADED, dataFile = null, dataSize = 40)
    val audioMessage = fixtures.insertMessage(second)
    val audio = fixtures.insertAttachment(audioMessage, contentType = "audio/aac", dataSize = 10)
    val linkMessage = fixtures.insertMessage(first, linkPreviews = "[]")
    fixtures.insertAttachment(linkMessage, contentType = "image/jpeg", dataSize = 5)
    val videoMessage = fixtures.insertMessage(second)
    val video = fixtures.insertAttachment(videoMessage, contentType = "video/mp4", dataSize = 20)
    fixtures.insertAttachment(fixtures.insertMessage(second, scheduledDate = 1000), contentType = "image/jpeg", dataSize = 60)

    val media = SignalDatabase.media
    val allThreads = MediaTable.ALL_THREADS.toLong()

    assertThat(media.getGalleryMediaForThread(first, Sorting.Newest).readIds()).containsExactly(offloaded, image)
    assertThat(media.getGalleryMediaForThread(allThreads, Sorting.Newest).readIds()).containsExactly(video, image)
    assertThat(media.getGalleryMediaForThread(allThreads, Sorting.Oldest).readIds()).containsExactly(image, video)
    assertThat(media.getGalleryMediaForThread(allThreads, Sorting.Largest).readIds()).containsExactly(image, video)
    assertThat(media.getGalleryMediaForThread(allThreads, Sorting.Largest, limit = 1, offset = 1).readIds()).containsExactly(video)

    for (sorting in Sorting.entries) {
      assertThat(media.getDocumentMediaForThread(allThreads, sorting).readIds(), name = "sorting=$sorting").containsExactly(pdf)
      assertThat(media.getAudioMediaForThread(allThreads, sorting).readIds(), name = "sorting=$sorting").containsExactly(audio)
      assertThat(media.getLinkMediaForThread(allThreads, sorting).readMessageIds(), name = "sorting=$sorting").containsExactly(linkMessage)
    }

    val oldest = listOf(imageMessage, pdfMessage, audioMessage, linkMessage, videoMessage)
    assertThat(media.getAllMediaForThread(allThreads, Sorting.Newest).readMessageIds()).isEqualTo(oldest.reversed())
    assertThat(media.getAllMediaForThread(allThreads, Sorting.Oldest).readMessageIds()).isEqualTo(oldest)
    assertThat(media.getAllMediaForThread(allThreads, Sorting.Largest).readMessageIds()).containsExactly(pdfMessage, imageMessage, videoMessage, audioMessage, linkMessage)
    assertThat(media.getAllMediaForThread(allThreads, Sorting.Newest, limit = 2, offset = 1).readMessageIds()).isEqualTo(oldest.reversed().subList(1, 3))
  }

  @Test
  fun `given media that is not stored locally, when I get each tab across all threads, then it is excluded`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId)

    val image = fixtures.insertAttachment(messageId, contentType = "image/jpeg", dataSize = 10)
    val streamable = fixtures.insertAttachment(messageId, contentType = "video/mp4", transferState = AttachmentTable.TRANSFER_PROGRESS_PENDING, dataFile = null, incrementalDigest = byteArrayOf(1), dataSize = 500)
    val thumbnailOnly = fixtures.insertAttachment(messageId, transferState = AttachmentTable.TRANSFER_NEEDS_RESTORE, dataFile = null, thumbnailFile = "/thumbnail/file", dataSize = 400)
    val offloadedImage = fixtures.insertAttachment(messageId, transferState = AttachmentTable.TRANSFER_RESTORE_OFFLOADED, dataFile = null, dataSize = 300)
    fixtures.insertAttachment(messageId, contentType = "application/pdf", transferState = AttachmentTable.TRANSFER_RESTORE_OFFLOADED, dataFile = null, dataSize = 200)
    fixtures.insertAttachment(messageId, contentType = "audio/aac", transferState = AttachmentTable.TRANSFER_RESTORE_OFFLOADED, dataFile = null, dataSize = 100)

    val media = SignalDatabase.media
    val allThreads = MediaTable.ALL_THREADS.toLong()

    assertThat(galleryIds(threadId).toSet()).isEqualTo(setOf(image, streamable, thumbnailOnly, offloadedImage))
    assertThat(media.getDocumentMediaCountForThread(threadId)).isEqualTo(1)
    assertThat(media.getAudioMediaCountForThread(threadId)).isEqualTo(1)

    for (sorting in Sorting.entries) {
      assertThat(galleryIds(allThreads, sorting), name = "sorting=$sorting").containsExactly(image)
      assertThat(media.getDocumentMediaForThread(allThreads, sorting).readIds(), name = "sorting=$sorting").isEmpty()
      assertThat(media.getAudioMediaForThread(allThreads, sorting).readIds(), name = "sorting=$sorting").isEmpty()
      assertThat(media.getAllMediaForThread(allThreads, sorting).readIds(), name = "sorting=$sorting").containsExactly(image)
    }

    assertThat(media.getGalleryMediaCountForThread(allThreads)).isEqualTo(1)
    assertThat(media.getAllMediaCountForThread(allThreads)).isEqualTo(1)
  }

  @Test
  fun `given links with and without a stored preview image, when I get links across all threads, then only links with a stored image are included`() {
    val threadId = fixtures.createThread()
    val withoutImage = fixtures.insertMessage(threadId, linkPreviews = "[]")
    val notDownloaded = fixtures.insertMessage(threadId, linkPreviews = "[]")
    fixtures.insertAttachment(notDownloaded, transferState = AttachmentTable.TRANSFER_PROGRESS_PENDING, dataFile = null)
    val stored = fixtures.insertMessage(threadId, linkPreviews = "[]")
    fixtures.insertAttachment(stored)

    val media = SignalDatabase.media
    val allThreads = MediaTable.ALL_THREADS.toLong()

    assertThat(media.getLinkMediaForThread(threadId, Sorting.Newest).readMessageIds()).containsExactly(stored, notDownloaded, withoutImage)

    for (sorting in Sorting.entries) {
      assertThat(media.getLinkMediaForThread(allThreads, sorting).readMessageIds(), name = "sorting=$sorting").containsExactly(stored)
      assertThat(media.getAllMediaForThread(allThreads, sorting).readMessageIds(), name = "sorting=$sorting").containsExactly(stored)
    }

    assertThat(media.getLinkMediaCountForThread(allThreads)).isEqualTo(1)
    assertThat(media.getAllMediaCountForThread(allThreads)).isEqualTo(1)
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
  fun `given a restored thumbnail without local data, when I get viewable gallery media across all threads, then it is excluded`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId)
    val stored = fixtures.insertAttachment(messageId)
    val thumbnailOnly = fixtures.insertAttachment(
      messageId = messageId,
      transferState = AttachmentTable.TRANSFER_NEEDS_RESTORE,
      dataFile = null,
      thumbnailFile = "/thumbnail/file",
      thumbnailRestoreState = AttachmentTable.ThumbnailRestoreState.FINISHED
    )
    val allThreads = MediaTable.ALL_THREADS.toLong()

    assertThat(viewableIds(threadId)).containsExactly(thumbnailOnly, stored)
    assertThat(viewableIds(allThreads)).containsExactly(stored)
    assertThat(SignalDatabase.media.getViewableGalleryMediaCountForThread(allThreads)).isEqualTo(1)
    assertThat(SignalDatabase.media.getViewableGalleryMediaPositionAndCount(allThreads, Sorting.Newest, thumbnailOnly)).isNull()
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

  private fun Cursor.readMessageIds(): List<Long> {
    return use {
      val ids = mutableListOf<Long>()
      while (moveToNext()) {
        ids += requireLong(MediaTable.MEDIA_MESSAGE_ID)
      }
      ids
    }
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
