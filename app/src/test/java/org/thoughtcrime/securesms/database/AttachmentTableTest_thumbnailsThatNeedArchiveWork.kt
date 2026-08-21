/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.logging.Log
import org.signal.core.util.update
import org.thoughtcrime.securesms.attachments.ArchivedAttachment
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.database.AttachmentTable.ArchiveTransferState
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import java.util.UUID
import kotlin.random.Random

/**
 * Coverage for which thumbnails are selected for archive work. A thumbnail qualifies when the archive has no copy, and also when we have no
 * local copy: without a local thumbnail file the attachment can never satisfy the offload eligibility check, so a restored device would be stuck.
 */
@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AttachmentTableTest_thumbnailsThatNeedArchiveWork {

  @get:Rule val recipients = RecipientTestRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @Test
  fun givenAnArchivedThumbnailWithNoLocalFile_whenIQuery_thenIExpectItSelected() {
    val attachmentId = insertAttachment(thumbnailState = ArchiveTransferState.FINISHED, hasLocalThumbnail = false)

    assertThat(SignalDatabase.attachments.getThumbnailsThatNeedArchiveUpload()).containsExactly(attachmentId)
    assertThat(SignalDatabase.attachments.doAnyThumbnailsNeedArchiveUpload()).isTrue()
  }

  @Test
  fun givenAnArchivedThumbnailWithALocalFile_whenIQuery_thenIExpectItSkipped() {
    insertAttachment(thumbnailState = ArchiveTransferState.FINISHED, hasLocalThumbnail = true)

    assertThat(SignalDatabase.attachments.getThumbnailsThatNeedArchiveUpload()).isEmpty()
    assertThat(SignalDatabase.attachments.doAnyThumbnailsNeedArchiveUpload()).isFalse()
  }

  @Test
  fun givenAnUnarchivedThumbnailWithALocalFile_whenIQuery_thenIExpectItSelected() {
    val attachmentId = insertAttachment(thumbnailState = ArchiveTransferState.NONE, hasLocalThumbnail = true)

    assertThat(SignalDatabase.attachments.getThumbnailsThatNeedArchiveUpload()).containsExactly(attachmentId)
  }

  @Test
  fun givenAnUnarchivedThumbnailWithNoLocalFile_whenIQuery_thenIExpectItSelected() {
    val attachmentId = insertAttachment(thumbnailState = ArchiveTransferState.NONE, hasLocalThumbnail = false)

    assertThat(SignalDatabase.attachments.getThumbnailsThatNeedArchiveUpload()).containsExactly(attachmentId)
  }

  @Test
  fun givenATemporarilyFailedThumbnail_whenIQuery_thenIExpectItSelected() {
    val attachmentId = insertAttachment(thumbnailState = ArchiveTransferState.TEMPORARY_FAILURE, hasLocalThumbnail = true)

    assertThat(SignalDatabase.attachments.getThumbnailsThatNeedArchiveUpload()).containsExactly(attachmentId)
  }

  /**
   * An upload writes the thumbnail file only once it finishes, so an in-flight row always looks like it has no local copy. Selecting it would enqueue a second
   * upload for work already underway.
   */
  @Test
  fun givenAnInFlightUploadWithNoLocalFile_whenIQuery_thenIExpectItSkipped() {
    insertAttachment(thumbnailState = ArchiveTransferState.UPLOAD_IN_PROGRESS, hasLocalThumbnail = false)

    assertThat(SignalDatabase.attachments.getThumbnailsThatNeedArchiveUpload()).isEmpty()
    assertThat(SignalDatabase.attachments.doAnyThumbnailsNeedArchiveUpload()).isFalse()
  }

  @Test
  fun givenACopyPendingThumbnailWithNoLocalFile_whenIQuery_thenIExpectItSkipped() {
    insertAttachment(thumbnailState = ArchiveTransferState.COPY_PENDING, hasLocalThumbnail = false)

    assertThat(SignalDatabase.attachments.getThumbnailsThatNeedArchiveUpload()).isEmpty()
  }

  /**
   * If we already established we can't produce a local thumbnail for this media, selecting it again just fails again after every backup.
   */
  @Test
  fun givenAnArchivedThumbnailWeCannotRestoreLocally_whenIQuery_thenIExpectItSkipped() {
    val attachmentId = insertAttachment(thumbnailState = ArchiveTransferState.FINISHED, hasLocalThumbnail = false)
    SignalDatabase.attachments.setThumbnailRestoreState(attachmentId, AttachmentTable.ThumbnailRestoreState.PERMANENT_FAILURE)

    assertThat(SignalDatabase.attachments.getThumbnailsThatNeedArchiveUpload()).isEmpty()
    assertThat(SignalDatabase.attachments.doAnyThumbnailsNeedArchiveUpload()).isFalse()
  }

  @Test
  fun givenAPermanentlyFailedThumbnailWithNoLocalFile_whenIQuery_thenIExpectItSkipped() {
    insertAttachment(thumbnailState = ArchiveTransferState.PERMANENT_FAILURE, hasLocalThumbnail = false)

    assertThat(SignalDatabase.attachments.getThumbnailsThatNeedArchiveUpload()).isEmpty()
    assertThat(SignalDatabase.attachments.doAnyThumbnailsNeedArchiveUpload()).isFalse()
  }

  @Test
  fun givenNoLocalDataFile_whenIQuery_thenIExpectItSkipped() {
    insertAttachment(thumbnailState = ArchiveTransferState.FINISHED, hasLocalThumbnail = false, hasLocalData = false)

    assertThat(SignalDatabase.attachments.getThumbnailsThatNeedArchiveUpload()).isEmpty()
  }

  @Test
  fun givenNonVisualMedia_whenIQuery_thenIExpectItSkipped() {
    insertAttachment(thumbnailState = ArchiveTransferState.FINISHED, hasLocalThumbnail = false, contentType = "application/pdf")

    assertThat(SignalDatabase.attachments.getThumbnailsThatNeedArchiveUpload()).isEmpty()
  }

  @Test
  fun givenAMixOfStates_whenIQuery_thenIExpectOnlyTheOnesNeedingWork() {
    val archivedWithNoFile = insertAttachment(thumbnailState = ArchiveTransferState.FINISHED, hasLocalThumbnail = false)
    val unarchived = insertAttachment(thumbnailState = ArchiveTransferState.NONE, hasLocalThumbnail = true)
    insertAttachment(thumbnailState = ArchiveTransferState.FINISHED, hasLocalThumbnail = true)
    insertAttachment(thumbnailState = ArchiveTransferState.PERMANENT_FAILURE, hasLocalThumbnail = false)

    assertThat(SignalDatabase.attachments.getThumbnailsThatNeedArchiveUpload()).containsExactly(archivedWithNoFile, unarchived)
  }

  @Test
  fun givenALocalThumbnailFile_whenIAskIfItHasOne_thenIExpectTrue() {
    val attachmentId = insertAttachment(thumbnailState = ArchiveTransferState.FINISHED, hasLocalThumbnail = true)

    assertThat(SignalDatabase.attachments.hasThumbnailFile(attachmentId)).isTrue()
  }

  @Test
  fun givenNoLocalThumbnailFile_whenIAskIfItHasOne_thenIExpectFalse() {
    val attachmentId = insertAttachment(thumbnailState = ArchiveTransferState.FINISHED, hasLocalThumbnail = false)

    assertThat(SignalDatabase.attachments.hasThumbnailFile(attachmentId)).isFalse()
  }

  /**
   * [AttachmentTable.ThumbnailRestoreState.PERMANENT_FAILURE] is also what stops the restore job from downloading, so tombstoning a row a restore could still
   * recover would cost the user a thumbnail the archive CDN is holding.
   */
  @Test
  fun givenARestoreIsPending_whenIMarkItUnrestorable_thenIExpectTheRestoreStateUntouched() {
    val attachmentId = insertAttachment(thumbnailState = ArchiveTransferState.FINISHED, hasLocalThumbnail = false)
    SignalDatabase.attachments.setThumbnailRestoreState(attachmentId, AttachmentTable.ThumbnailRestoreState.NEEDS_RESTORE)

    SignalDatabase.attachments.markThumbnailPermanentlyFailedIfUnrestorable(attachmentId)

    assertThat(restoreStateOf(attachmentId)).isEqualTo(AttachmentTable.ThumbnailRestoreState.NEEDS_RESTORE)
  }

  @Test
  fun givenARestoreAlreadyFinished_whenIMarkItUnrestorable_thenIExpectTheRestoreStateUntouched() {
    val attachmentId = insertAttachment(thumbnailState = ArchiveTransferState.FINISHED, hasLocalThumbnail = false)
    SignalDatabase.attachments.setThumbnailRestoreState(attachmentId, AttachmentTable.ThumbnailRestoreState.FINISHED)

    SignalDatabase.attachments.markThumbnailPermanentlyFailedIfUnrestorable(attachmentId)

    assertThat(restoreStateOf(attachmentId)).isEqualTo(AttachmentTable.ThumbnailRestoreState.FINISHED)
  }

  @Test
  fun givenNoRestoreIsPossible_whenIMarkItUnrestorable_thenIExpectItTombstonedAndSkipped() {
    val attachmentId = insertAttachment(thumbnailState = ArchiveTransferState.FINISHED, hasLocalThumbnail = false)
    SignalDatabase.attachments.setThumbnailRestoreState(attachmentId, AttachmentTable.ThumbnailRestoreState.NONE)

    SignalDatabase.attachments.markThumbnailPermanentlyFailedIfUnrestorable(attachmentId)

    assertThat(restoreStateOf(attachmentId)).isEqualTo(AttachmentTable.ThumbnailRestoreState.PERMANENT_FAILURE)
    assertThat(SignalDatabase.attachments.getThumbnailsThatNeedArchiveUpload()).isEmpty()
  }

  private fun restoreStateOf(attachmentId: AttachmentId): AttachmentTable.ThumbnailRestoreState {
    return SignalDatabase.attachments.getAttachment(attachmentId)!!.thumbnailRestoreState
  }

  private fun insertAttachment(
    thumbnailState: ArchiveTransferState,
    hasLocalThumbnail: Boolean,
    hasLocalData: Boolean = true,
    contentType: String = "image/jpeg"
  ): AttachmentId {
    val attachmentId = insertArchivedAttachment(contentType)

    SignalDatabase.attachments.writableDatabase
      .update(AttachmentTable.TABLE_NAME)
      .values(
        AttachmentTable.DATA_FILE to if (hasLocalData) "/fake/path/data-${attachmentId.id}" else null,
        AttachmentTable.DATA_RANDOM to if (hasLocalData) Random.nextBytes(32) else null,
        AttachmentTable.TRANSFER_STATE to AttachmentTable.TRANSFER_PROGRESS_DONE,
        AttachmentTable.THUMBNAIL_FILE to if (hasLocalThumbnail) "/fake/path/thumb-${attachmentId.id}" else null,
        AttachmentTable.THUMBNAIL_RANDOM to if (hasLocalThumbnail) Random.nextBytes(32) else null,
        AttachmentTable.ARCHIVE_THUMBNAIL_TRANSFER_STATE to thumbnailState.value
      )
      .where("${AttachmentTable.ID} = ?", attachmentId.id)
      .run()

    return attachmentId
  }

  private fun insertArchivedAttachment(contentType: String): AttachmentId {
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
