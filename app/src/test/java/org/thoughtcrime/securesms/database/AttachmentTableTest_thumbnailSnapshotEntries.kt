/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.core.util.update
import org.thoughtcrime.securesms.attachments.ArchivedAttachment
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import java.util.UUID
import kotlin.random.Random

/**
 * Coverage for which thumbnail entries survive into the backup media snapshot. An entry that does not survive falls out of the snapshot and is
 * therefore marked for deletion off of the archive CDN, so media with no local data file but an archived thumbnail has to be kept.
 */
@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AttachmentTableTest_thumbnailSnapshotEntries {

  @get:Rule val recipients = RecipientTestRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @Test
  fun givenNoLocalDataFileButAnArchivedThumbnail_whenIFilter_thenIExpectTheEntryKept() {
    val attachmentId = insertArchivedAttachment()
    SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)

    val entry = thumbnailEntryFor(attachmentId)
    val kept = SignalDatabase.attachments.filterThumbnailsWithoutEligibleAttachment(setOf(entry))

    assertThat(kept.size).isEqualTo(1)
    assertThat(kept.first().mediaId).isEqualTo(entry.mediaId)
  }

  @Test
  fun givenNoLocalDataFileAndNoArchivedThumbnail_whenIFilter_thenIExpectTheEntryDropped() {
    val attachmentId = insertArchivedAttachment()

    val kept = SignalDatabase.attachments.filterThumbnailsWithoutEligibleAttachment(setOf(thumbnailEntryFor(attachmentId)))

    assertThat(kept).isEmpty()
  }

  @Test
  fun givenAThumbnailRestoredFromTheCdn_whenIFilter_thenIExpectTheEntryKept() {
    val attachmentId = insertArchivedAttachment()
    SignalDatabase.attachments.setThumbnailRestoreState(listOf(attachmentId), AttachmentTable.ThumbnailRestoreState.FINISHED)

    val entry = thumbnailEntryFor(attachmentId)
    val kept = SignalDatabase.attachments.filterThumbnailsWithoutEligibleAttachment(setOf(entry))

    assertThat(kept.size).isEqualTo(1)
    assertThat(kept.first().mediaId).isEqualTo(entry.mediaId)
  }

  @Test
  fun givenAPermanentlyFailedThumbnail_whenIFilter_thenIExpectTheEntryDropped() {
    val attachmentId = insertArchivedAttachment()
    SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.PERMANENT_FAILURE)

    val kept = SignalDatabase.attachments.filterThumbnailsWithoutEligibleAttachment(setOf(thumbnailEntryFor(attachmentId)))

    assertThat(kept).isEmpty()
  }

  /**
   * These four mirror the cases above against [AttachmentTable.getMediaNamesWithNoEligibleThumbnail], which answers the same question with an indexed lookup
   * instead of a full scan. The two share a predicate, so a divergence here means the CDN-delete side and the snapshot-write side have drifted apart.
   */
  @Test
  fun givenNoLocalDataFileButAnArchivedThumbnail_whenIQueryByName_thenIExpectItStillWanted() {
    val attachmentId = insertArchivedAttachment()
    SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)

    assertThat(SignalDatabase.attachments.getMediaNamesWithNoEligibleThumbnail(setOf(mediaNameFor(attachmentId)))).isEmpty()
  }

  @Test
  fun givenNoLocalDataFileAndNoArchivedThumbnail_whenIQueryByName_thenIExpectItUnwanted() {
    val attachmentId = insertArchivedAttachment()

    assertThat(SignalDatabase.attachments.getMediaNamesWithNoEligibleThumbnail(setOf(mediaNameFor(attachmentId)))).hasSize(1)
  }

  @Test
  fun givenAThumbnailRestoredFromTheCdn_whenIQueryByName_thenIExpectItStillWanted() {
    val attachmentId = insertArchivedAttachment()
    SignalDatabase.attachments.setThumbnailRestoreState(listOf(attachmentId), AttachmentTable.ThumbnailRestoreState.FINISHED)

    assertThat(SignalDatabase.attachments.getMediaNamesWithNoEligibleThumbnail(setOf(mediaNameFor(attachmentId)))).isEmpty()
  }

  @Test
  fun givenAPermanentlyFailedThumbnail_whenIQueryByName_thenIExpectItUnwanted() {
    val attachmentId = insertArchivedAttachment()
    SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.PERMANENT_FAILURE)

    assertThat(SignalDatabase.attachments.getMediaNamesWithNoEligibleThumbnail(setOf(mediaNameFor(attachmentId)))).hasSize(1)
  }

  @Test
  fun givenAMediaNameWithNoAttachmentAtAll_whenIQueryByName_thenIExpectItUnwanted() {
    val orphan = AttachmentTable.MediaNameParts(
      plaintextHash = Base64.encodeWithPadding(Random.nextBytes(32)),
      remoteKey = Base64.encodeWithPadding(Random.nextBytes(32))
    )

    assertThat(SignalDatabase.attachments.getMediaNamesWithNoEligibleThumbnail(setOf(orphan))).hasSize(1)
  }

  /**
   * These pin the clause against [org.thoughtcrime.securesms.jobs.BackupMessagesJob]'s thumbnail filters. Anything the write side refuses to put in a snapshot
   * has to read as unwanted here, or we keep paying to store an object nothing will ever reference again.
   */
  @Test
  fun givenAWallpaper_whenIFilter_thenIExpectTheEntryDropped() {
    val attachmentId = insertArchivedAttachment()
    SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)
    setColumn(attachmentId, AttachmentTable.MESSAGE_ID, AttachmentTable.WALLPAPER_MESSAGE_ID)

    assertThat(SignalDatabase.attachments.filterThumbnailsWithoutEligibleAttachment(setOf(thumbnailEntryFor(attachmentId)))).isEmpty()
  }

  @Test
  fun givenAWallpaper_whenIQueryByName_thenIExpectItUnwanted() {
    val attachmentId = insertArchivedAttachment()
    SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)
    setColumn(attachmentId, AttachmentTable.MESSAGE_ID, AttachmentTable.WALLPAPER_MESSAGE_ID)

    assertThat(SignalDatabase.attachments.getMediaNamesWithNoEligibleThumbnail(setOf(mediaNameFor(attachmentId)))).hasSize(1)
  }

  @Test
  fun givenNonVisualMedia_whenIQueryByName_thenIExpectItUnwanted() {
    val attachmentId = insertArchivedAttachment(contentType = "application/pdf")
    SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)

    assertThat(SignalDatabase.attachments.getMediaNamesWithNoEligibleThumbnail(setOf(mediaNameFor(attachmentId)))).hasSize(1)
  }

  @Test
  fun givenAnSvg_whenIQueryByName_thenIExpectItUnwanted() {
    val attachmentId = insertArchivedAttachment(contentType = "image/svg+xml")
    SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)

    assertThat(SignalDatabase.attachments.getMediaNamesWithNoEligibleThumbnail(setOf(mediaNameFor(attachmentId)))).hasSize(1)
  }

  /**
   * Stickers are excluded from thumbnail generation upstream, so in practice they never reach the CDN and this branch is unreachable. It stays unfiltered
   * deliberately: this clause exists to mirror the snapshot write side, and the write side doesn't special-case stickers either.
   */
  @Test
  fun givenASticker_whenIQueryByName_thenIExpectItStillWanted() {
    val attachmentId = insertArchivedAttachment()
    SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)

    // Read before writing the sticker id, since setting it alone leaves a sticker locator the attachment reader can't parse.
    val mediaName = mediaNameFor(attachmentId)
    setColumn(attachmentId, AttachmentTable.STICKER_ID, 7L)

    assertThat(SignalDatabase.attachments.getMediaNamesWithNoEligibleThumbnail(setOf(mediaName))).isEmpty()
  }

  private fun setColumn(attachmentId: AttachmentId, column: String, value: Long) {
    SignalDatabase.attachments.writableDatabase
      .update(AttachmentTable.TABLE_NAME)
      .values(column to value)
      .where("${AttachmentTable.ID} = ?", attachmentId.id)
      .run()
  }

  private fun mediaNameFor(attachmentId: AttachmentId): AttachmentTable.MediaNameParts {
    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)!!

    return AttachmentTable.MediaNameParts(
      plaintextHash = attachment.dataHash!!,
      remoteKey = attachment.remoteKey!!
    )
  }

  private fun thumbnailEntryFor(attachmentId: AttachmentId): BackupMediaSnapshotTable.MediaEntry {
    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)!!

    return BackupMediaSnapshotTable.MediaEntry(
      mediaId = "media-id-${attachment.attachmentId.id}",
      cdn = 3,
      plaintextHash = Base64.decode(attachment.dataHash!!),
      remoteKey = Base64.decode(attachment.remoteKey!!),
      isThumbnail = true
    )
  }

  private fun insertArchivedAttachment(contentType: String = "image/jpeg"): AttachmentId {
    val from = recipients.createRecipient("Some Contact")
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(from))

    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = from,
      body = null,
      sentTimeMillis = 100L,
      serverTimeMillis = 100L,
      receivedTimeMillis = 200L,
      attachments = listOf(createArchivedAttachment(contentType))
    )

    val messageId = SignalDatabase.messages.insertMessageInbox(message, threadId).get().messageId
    return SignalDatabase.attachments.getAttachmentsForMessage(messageId).first().attachmentId
  }

  private fun createArchivedAttachment(contentType: String): Attachment {
    return ArchivedAttachment(
      contentType = contentType,
      size = 1024,
      cdn = 3,
      uploadTimestamp = 0,
      key = Random.nextBytes(32),
      cdnKey = "password",
      archiveCdn = 3,
      plaintextHash = Random.nextBytes(32),
      incrementalMac = Random.nextBytes(8),
      incrementalMacChunkSize = 8,
      width = 100,
      height = 100,
      caption = null,
      blurHash = null,
      voiceNote = false,
      borderless = false,
      stickerLocator = null,
      gif = false,
      quote = false,
      quoteTargetContentType = null,
      uuid = UUID.randomUUID(),
      fileName = null,
      localBackupKey = null
    )
  }
}
