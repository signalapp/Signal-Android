/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import android.content.ContentValues
import android.database.Cursor
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.MediaTable.MediaRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MediaTableTest_getLinkMediaForThread {

  @get:Rule
  val harness = RecipientTestRule()

  private var nextSentTime: Long = 1000

  private lateinit var other: RecipientId
  private var threadId: Long = 0

  @Before
  fun setUp() {
    other = harness.createRecipient("Other Person")
    threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(other))
  }

  @Test
  fun givenMessageWithStoredPreview_whenQueryingLinks_thenIncludedUsingThePreviewUrlAndTitle() {
    insertMessage(body = "look at https://signal.org", linkPreviews = previewJson("https://signal.org", "Signal"))

    val record = linkRecords().single()

    assertThat(record.linkUrl).isEqualTo("https://signal.org")
    assertThat(record.linkTitle).isEqualTo("Signal")
  }

  @Test
  fun givenStoredPreviewWithBlankTitle_whenQueryingLinks_thenTitleIsNullRatherThanEmpty() {
    insertMessage(body = "https://signal.org", linkPreviews = previewJson("https://signal.org", ""))

    assertThat(linkRecords().single().linkTitle).isNull()
  }

  @Test
  fun givenStoredPreviewDisagreesWithBody_whenQueryingLinks_thenThePreviewWins() {
    insertMessage(body = "https://body.org", linkPreviews = previewJson("https://preview.org", "Preview"))

    assertThat(linkRecords().single().linkUrl).isEqualTo("https://preview.org")
  }

  @Test
  fun givenStoredPreviewIsAnEmptyJsonArray_whenQueryingLinks_thenItFallsBackToTheBody() {
    insertMessage(body = "https://signal.org", linkPreviews = "[]")

    assertThat(linkRecords().single().linkUrl).isEqualTo("https://signal.org")
  }

  @Test
  fun givenStoredPreviewIsUnparsable_whenQueryingLinks_thenItFallsBackToTheBody() {
    insertMessage(body = "https://signal.org", linkPreviews = "not json")

    assertThat(linkRecords().single().linkUrl).isEqualTo("https://signal.org")
  }

  @Test
  fun givenTextOnlyMessageWithLinkInBody_whenQueryingLinks_thenIncludedUsingTheBodyUrl() {
    insertMessage(body = "have a look at https://signal.org today")

    val record = linkRecords().single()

    assertThat(record.linkUrl).isEqualTo("https://signal.org")
    assertThat(record.linkTitle).isNull()
  }

  @Test
  fun givenIncomingMessageWithLinkInBody_whenQueryingLinks_thenIncluded() {
    insertMessage(body = "https://signal.org", fromRecipientId = other, outgoing = false)

    assertThat(linkRecords().single().linkUrl).isEqualTo("https://signal.org")
  }

  @Test
  fun givenBodyWithSeveralLinks_whenQueryingLinks_thenTheFirstIsUsed() {
    insertMessage(body = "https://first.org and then https://second.org")

    assertThat(linkRecords().single().linkUrl).isEqualTo("https://first.org")
  }

  @Test
  fun givenBodyWithNoLink_whenQueryingLinks_thenExcluded() {
    insertMessage(body = "just some ordinary text")

    assertThat(linkRecords()).isEmpty()
  }

  @Test
  fun givenNullBodyAndNoPreview_whenQueryingLinks_thenExcluded() {
    insertMessage(body = null)

    assertThat(linkRecords()).isEmpty()
  }

  @Test
  fun givenBodiesThatMatchTheLikeButHoldNoUsableLink_whenQueryingLinks_thenRowsComeBackWithNoLink() {
    // SQLite cannot tell a link from an arbitrary string, so these rows do come back from the query and
    // have neither a link nor an attachment. GroupedThreadMediaLoader has to drop them before they reach
    // the adapter; see GroupedThreadMediaLoaderTest_linkMedia.
    insertMessage(body = "https://localhost/admin")
    insertMessage(body = "https://example.com")
    insertMessage(body = "https://192.168.1.10")
    insertMessage(body = "the string https:// on its own")

    val records = linkRecords()

    assertThat(records).hasSize(4)
    assertThat(records.map { it.linkUrl }).containsOnly(null)
    assertThat(records.map { it.attachment }).containsOnly(null)
  }

  @Test
  fun givenHttpRatherThanHttpsLinkInBody_whenQueryingLinks_thenExcluded() {
    insertMessage(body = "http://signal.org")

    assertThat(linkRecords()).isEmpty()
  }

  @Test
  fun givenBareDomainInBody_whenQueryingLinks_thenExcluded() {
    insertMessage(body = "signal.org")

    assertThat(linkRecords()).isEmpty()
  }

  @Test
  fun givenMessageWithAttachmentAndLinkInBody_whenQueryingLinks_thenExcluded() {
    val messageId = insertMessage(body = "a photo and https://signal.org")
    insertAttachment(messageId)

    assertThat(linkRecords()).isEmpty()
  }

  @Test
  fun givenViewOnceMessageWithLinkInBody_whenQueryingLinks_thenExcluded() {
    insertMessage(body = "https://signal.org", viewOnce = true)

    assertThat(linkRecords()).isEmpty()
  }

  @Test
  fun givenStoryWithLinkInBody_whenQueryingLinks_thenExcluded() {
    insertMessage(body = "https://signal.org", storyType = 1)

    assertThat(linkRecords()).isEmpty()
  }

  @Test
  fun givenScheduledMessageWithLinkInBody_whenQueryingLinks_thenExcluded() {
    insertMessage(body = "https://signal.org", scheduledDate = 1L)

    assertThat(linkRecords()).isEmpty()
  }

  @Test
  fun givenSupersededRevisionWithLinkInBody_whenQueryingLinks_thenOnlyTheLatestRevisionIsIncluded() {
    val latest = insertMessage(body = "https://edited.org")
    insertMessage(body = "https://original.org", latestRevisionId = latest)

    assertThat(linkRecords().map { it.linkUrl }).containsExactly("https://edited.org")
  }

  @Test
  fun givenLinkInAnotherThread_whenQueryingLinks_thenExcluded() {
    val stranger = harness.createRecipient("Stranger")
    val otherThread = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(stranger))
    insertMessage(body = "https://elsewhere.org", threadId = otherThread)

    assertThat(linkRecords()).isEmpty()
  }

  @Test
  fun givenSeveralLinks_whenQueryingLinks_thenNewestSortsMostRecentFirstAndOldestReverses() {
    insertMessage(body = "https://first.org", sentTime = 100)
    insertMessage(body = "https://second.org", sentTime = 200)
    insertMessage(body = "https://third.org", sentTime = 300)

    assertThat(linkRecords(MediaTable.Sorting.Newest).map { it.linkUrl })
      .containsExactly("https://third.org", "https://second.org", "https://first.org")

    assertThat(linkRecords(MediaTable.Sorting.Oldest).map { it.linkUrl })
      .containsExactly("https://first.org", "https://second.org", "https://third.org")
  }

  @Test
  fun givenMessageWithStoredPreview_whenQueryingAllMedia_thenItIsStillIncluded() {
    insertMessage(body = "https://signal.org", linkPreviews = previewJson("https://signal.org", "Signal"))

    assertThat(allMediaRecords().map { it.linkUrl }).containsExactly("https://signal.org")
  }

  private fun linkRecords(sorting: MediaTable.Sorting = MediaTable.Sorting.Newest): List<MediaRecord> {
    return SignalDatabase.media.getLinkMediaForThread(threadId, sorting).readRecords()
  }

  private fun allMediaRecords(sorting: MediaTable.Sorting = MediaTable.Sorting.Newest): List<MediaRecord> {
    return SignalDatabase.media.getAllMediaForThread(threadId, sorting).readRecords()
  }

  private fun Cursor.readRecords(): List<MediaRecord> {
    return use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(MediaRecord.from(cursor))
        }
      }
    }
  }

  private fun previewJson(url: String, title: String): String {
    return """[{"url":"$url","title":"$title","description":"","date":0,"attachmentId":null}]"""
  }

  private fun insertMessage(
    body: String?,
    linkPreviews: String? = null,
    threadId: Long = this.threadId,
    fromRecipientId: RecipientId = harness.self,
    outgoing: Boolean = true,
    viewOnce: Boolean = false,
    storyType: Int = 0,
    scheduledDate: Long = -1,
    latestRevisionId: Long? = null,
    sentTime: Long = nextSentTime++
  ): Long {
    val values = ContentValues().apply {
      put(MessageTable.DATE_SENT, sentTime)
      put(MessageTable.DATE_RECEIVED, sentTime)
      put(MessageTable.THREAD_ID, threadId)
      put(MessageTable.FROM_RECIPIENT_ID, fromRecipientId.serialize())
      put(MessageTable.TO_RECIPIENT_ID, if (outgoing) other.serialize() else harness.self.serialize())
      put(MessageTable.TYPE, if (outgoing) MessageTypes.BASE_SENT_TYPE or MessageTypes.PUSH_MESSAGE_BIT else MessageTypes.BASE_INBOX_TYPE or MessageTypes.PUSH_MESSAGE_BIT)
      put(MessageTable.BODY, body)
      put(MessageTable.LINK_PREVIEWS, linkPreviews)
      put(MessageTable.VIEW_ONCE, if (viewOnce) 1 else 0)
      put(MessageTable.STORY_TYPE, storyType)
      put(MessageTable.SCHEDULED_DATE, scheduledDate)
      put(MessageTable.LATEST_REVISION_ID, latestRevisionId)
    }

    return SignalDatabase.writableDatabase.insert(MessageTable.TABLE_NAME, null, values)
  }

  private fun insertAttachment(messageId: Long): Long {
    return SignalDatabase.writableDatabase.insert(
      AttachmentTable.TABLE_NAME,
      null,
      ContentValues().apply {
        put(AttachmentTable.MESSAGE_ID, messageId)
        put(AttachmentTable.TRANSFER_STATE, AttachmentTable.TRANSFER_PROGRESS_DONE)
        put(AttachmentTable.CONTENT_TYPE, "image/jpeg")
        put(AttachmentTable.DATA_FILE, "/tmp/not-a-real-file")
        put(AttachmentTable.DATA_SIZE, 1024)
      }
    )
  }
}
