/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.MediaTable.Sorting
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger

/**
 * Coverage for how the media overview queries treat a message that has both a link preview and an attachment.
 */
@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MediaTableTest_linkPreviewMedia {

  @get:Rule
  val recipients = RecipientTestRule()

  private val fixtures = GalleryMediaFixtures(recipients)

  companion object {
    private const val LINK_PREVIEWS = """[{"url":"https://signal.org","title":"Signal","description":"","date":0,"attachmentId":null}]"""

    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @Test
  fun `given a document with a link preview, when I get document media, then the document is listed as a document`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId, linkPreviews = LINK_PREVIEWS)
    val pdf = fixtures.insertAttachment(messageId, contentType = "application/pdf")

    val records = SignalDatabase.media.getDocumentMediaForThread(threadId, Sorting.Newest).readRecords()

    assertThat(records).hasSize(1)
    assertThat(records[0].attachment?.attachmentId?.id).isEqualTo(pdf)
    assertThat(records[0].linkPreviewJson).isNull()
  }

  @Test
  fun `given a document with a link preview, when I get link media, then the link is listed without the document`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId, linkPreviews = LINK_PREVIEWS)
    fixtures.insertAttachment(messageId, contentType = "application/pdf")

    val records = SignalDatabase.media.getLinkMediaForThread(threadId, Sorting.Newest).readRecords()

    assertThat(records).hasSize(1)
    assertThat(records[0].attachment).isNull()
    assertThat(records[0].linkPreviewJson).isNotNull()
  }

  @Test
  fun `given a link preview with a thumbnail, when I get link media, then the thumbnail is listed as the link`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId, linkPreviews = LINK_PREVIEWS)
    val thumbnail = fixtures.insertAttachment(messageId, contentType = "image/jpeg")

    val records = SignalDatabase.media.getLinkMediaForThread(threadId, Sorting.Newest).readRecords()

    assertThat(records).hasSize(1)
    assertThat(records[0].attachment?.attachmentId?.id).isEqualTo(thumbnail)
    assertThat(records[0].linkPreviewJson).isNotNull()
  }

  @Test
  fun `given a video with a link preview, when I get gallery media, then the video is listed`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId, linkPreviews = LINK_PREVIEWS)
    val video = fixtures.insertAttachment(messageId, contentType = "video/mp4")

    assertThat(SignalDatabase.media.getGalleryMediaForThread(threadId, Sorting.Newest).readIds()).containsExactly(video)
    assertThat(SignalDatabase.media.getViewableGalleryMediaForThread(threadId, Sorting.Newest).map { it.attachment!!.attachmentId.id }).containsExactly(video)
    assertThat(SignalDatabase.media.getViewableGalleryMediaForAttachment(threadId, video)?.attachment?.attachmentId?.id).isEqualTo(video)
  }

  @Test
  fun `given a link preview with a thumbnail, when I get gallery media, then the thumbnail is excluded`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId, linkPreviews = LINK_PREVIEWS)
    fixtures.insertAttachment(messageId, contentType = "image/jpeg")

    assertThat(SignalDatabase.media.getGalleryMediaForThread(threadId, Sorting.Newest).readIds()).isEmpty()
    assertThat(SignalDatabase.media.getViewableGalleryMediaForThread(threadId, Sorting.Newest)).isEmpty()
  }

  @Test
  fun `given a document with a link preview, when I get all media, then both the document and the link are listed`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId, linkPreviews = LINK_PREVIEWS)
    val pdf = fixtures.insertAttachment(messageId, contentType = "application/pdf")

    val records = SignalDatabase.media.getAllMediaForThread(threadId, Sorting.Newest).readRecords()

    assertThat(records).hasSize(2)
    val document = records.single { it.linkPreviewJson == null }
    val link = records.single { it.linkPreviewJson != null }
    assertThat(document.attachment?.attachmentId?.id).isEqualTo(pdf)
    assertThat(link.attachment).isNull()
  }

  @Test
  fun `given a document with a link preview, when I get all media, then the document and the link keep a stable order`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId, linkPreviews = LINK_PREVIEWS)
    val pdf = fixtures.insertAttachment(messageId, contentType = "application/pdf")

    val newest = SignalDatabase.media.getAllMediaForThread(threadId, Sorting.Newest).readRecords()
    val oldest = SignalDatabase.media.getAllMediaForThread(threadId, Sorting.Oldest).readRecords()

    assertThat(newest.map { it.attachment?.attachmentId?.id }).containsExactly(pdf, null)
    assertThat(oldest.map { it.attachment?.attachmentId?.id }).containsExactly(null, pdf)
  }

  @Test
  fun `given a link preview with a thumbnail, when I get all media, then only the link is listed`() {
    val threadId = fixtures.createThread()
    val messageId = fixtures.insertMessage(threadId, linkPreviews = LINK_PREVIEWS)
    fixtures.insertAttachment(messageId, contentType = "image/jpeg")

    val records = SignalDatabase.media.getAllMediaForThread(threadId, Sorting.Newest).readRecords()

    assertThat(records).hasSize(1)
    assertThat(records[0].linkPreviewJson).isNotNull()
  }
}
