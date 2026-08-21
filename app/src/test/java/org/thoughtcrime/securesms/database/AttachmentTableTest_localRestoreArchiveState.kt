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
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.ArchivedAttachment
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import java.util.UUID
import kotlin.random.Random

/**
 * JVM (Robolectric) coverage for the archive transfer state of media imported from a local backup. Verifies the invariant that a locally-imported attachment is
 * FINISHED only when it carries an archive CDN, and that [AttachmentTable.hasArchiveFinishedLocalBackupMedia] detects exactly the bad state the local-restore
 * migration repairs.
 */
@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AttachmentTableTest_localRestoreArchiveState {

  @get:Rule val recipients = RecipientTestRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @Test
  fun givenLocallyImportedMediaThatCarriesAnArchiveCdn_whenInserted_thenIExpectArchiveStateFinished() {
    val attachmentId = insertArchivedAttachment(archiveCdn = 3, localBackupKey = Random.nextBytes(32))

    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)!!
    assertThat(attachment.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.FINISHED)
    assertThat(attachment.archiveCdn).isEqualTo(3)
  }

  @Test
  fun givenLocallyImportedMediaThatCarriesNoArchiveCdn_whenInserted_thenIExpectArchiveStateNone() {
    val attachmentId = insertArchivedAttachment(archiveCdn = null, localBackupKey = Random.nextBytes(32))

    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)!!
    assertThat(attachment.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)
    assertThat(attachment.archiveCdn).isNull()
  }

  @Test
  fun hasArchiveFinishedLocalBackupMedia_trueForFinishedLocalBackupMedia() {
    insertArchivedAttachment(archiveCdn = 3, localBackupKey = Random.nextBytes(32))

    assertThat(SignalDatabase.attachments.hasArchiveFinishedLocalBackupMedia()).isTrue()
  }

  @Test
  fun hasArchiveFinishedLocalBackupMedia_falseWhenFinishedButNotFromLocalBackup() {
    insertArchivedAttachment(archiveCdn = 3, localBackupKey = null)

    assertThat(SignalDatabase.attachments.hasArchiveFinishedLocalBackupMedia()).isFalse()
  }

  @Test
  fun hasArchiveFinishedLocalBackupMedia_falseOnceLocalBackupMediaIsNoLongerFinished() {
    insertArchivedAttachment(archiveCdn = 3, localBackupKey = Random.nextBytes(32))
    assertThat(SignalDatabase.attachments.hasArchiveFinishedLocalBackupMedia()).isTrue()

    SignalDatabase.attachments.resetArchiveTransferStateForLocalBackupMedia()

    assertThat(SignalDatabase.attachments.hasArchiveFinishedLocalBackupMedia()).isFalse()
  }

  @Test
  fun resetArchiveTransferState_skippedWhenThereIsNoLocalDataFileToReUploadFrom() {
    val attachmentId = insertArchivedAttachment(archiveCdn = 3, localBackupKey = Random.nextBytes(32))
    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)!!

    val results = SignalDatabase.attachments.resetArchiveTransferStateByPlaintextHashAndRemoteKeyIfNecessary(
      listOf(AttachmentTable.MediaNameParts(plaintextHash = attachment.dataHash!!, remoteKey = attachment.remoteKey!!))
    )

    assertThat(results).containsExactly(AttachmentTable.ArchiveTransferStateResetResult.SKIPPED_NO_LOCAL_DATA)

    val after = SignalDatabase.attachments.getAttachment(attachmentId)!!
    assertThat(after.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.FINISHED)
    assertThat(after.archiveCdn).isEqualTo(3)
  }

  @Test
  fun resetArchiveTransferState_notNeededWhenNotFinished() {
    val attachmentId = insertArchivedAttachment(archiveCdn = null, localBackupKey = Random.nextBytes(32))
    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)!!

    val results = SignalDatabase.attachments.resetArchiveTransferStateByPlaintextHashAndRemoteKeyIfNecessary(
      listOf(AttachmentTable.MediaNameParts(plaintextHash = attachment.dataHash!!, remoteKey = attachment.remoteKey!!))
    )

    assertThat(results).containsExactly(AttachmentTable.ArchiveTransferStateResetResult.NOT_NEEDED)
  }

  @Test
  fun resetArchiveTransferState_batchedResultsLineUpWithTheirKeys() {
    val finishedNoLocalData = SignalDatabase.attachments.getAttachment(insertArchivedAttachment(archiveCdn = 3, localBackupKey = Random.nextBytes(32)))!!
    val notFinished = SignalDatabase.attachments.getAttachment(insertArchivedAttachment(archiveCdn = null, localBackupKey = Random.nextBytes(32)))!!

    val results = SignalDatabase.attachments.resetArchiveTransferStateByPlaintextHashAndRemoteKeyIfNecessary(
      listOf(
        AttachmentTable.MediaNameParts.fromBytes(plaintextHash = Random.nextBytes(8), remoteKey = Random.nextBytes(8)),
        AttachmentTable.MediaNameParts(plaintextHash = finishedNoLocalData.dataHash!!, remoteKey = finishedNoLocalData.remoteKey!!),
        AttachmentTable.MediaNameParts(plaintextHash = notFinished.dataHash!!, remoteKey = notFinished.remoteKey!!)
      )
    )

    assertThat(results).containsExactly(
      AttachmentTable.ArchiveTransferStateResetResult.NOT_NEEDED,
      AttachmentTable.ArchiveTransferStateResetResult.SKIPPED_NO_LOCAL_DATA,
      AttachmentTable.ArchiveTransferStateResetResult.NOT_NEEDED
    )
  }

  @Test
  fun resetArchiveThumbnailTransferState_marksUnrecoverableWhenThereIsNoLocalDataFileToRebuildFrom() {
    val attachmentId = insertArchivedAttachment(archiveCdn = 3, localBackupKey = Random.nextBytes(32))
    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)!!
    SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)

    val results = SignalDatabase.attachments.resetArchiveThumbnailTransferStateByPlaintextHashAndRemoteKeyIfNecessary(
      listOf(AttachmentTable.MediaNameParts(plaintextHash = attachment.dataHash!!, remoteKey = attachment.remoteKey!!))
    )

    assertThat(results).containsExactly(AttachmentTable.ArchiveTransferStateResetResult.MARKED_UNRECOVERABLE)
    assertThat(SignalDatabase.attachments.getArchiveThumbnailTransferState(attachmentId)).isEqualTo(AttachmentTable.ArchiveTransferState.PERMANENT_FAILURE)

    // The full-size locator is deliberately left intact, since that media is still on the CDN.
    val after = SignalDatabase.attachments.getAttachment(attachmentId)!!
    assertThat(after.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.FINISHED)
    assertThat(after.archiveCdn).isEqualTo(3)
  }

  @Test
  fun resetArchiveThumbnailTransferState_reportsAlreadyRecordedMediaAsUnrecoverableRatherThanNotNeeded() {
    val attachmentId = insertArchivedAttachment(archiveCdn = 3, localBackupKey = Random.nextBytes(32))
    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)!!
    SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)

    val mediaNames = listOf(
      AttachmentTable.MediaNameParts(plaintextHash = attachment.dataHash!!, remoteKey = attachment.remoteKey!!)
    )

    assertThat(SignalDatabase.attachments.resetArchiveThumbnailTransferStateByPlaintextHashAndRemoteKeyIfNecessary(mediaNames))
      .containsExactly(AttachmentTable.ArchiveTransferStateResetResult.MARKED_UNRECOVERABLE)

    assertThat(SignalDatabase.attachments.resetArchiveThumbnailTransferStateByPlaintextHashAndRemoteKeyIfNecessary(mediaNames))
      .containsExactly(AttachmentTable.ArchiveTransferStateResetResult.MARKED_UNRECOVERABLE)
  }

  @Test(expected = IllegalArgumentException::class)
  fun resetArchiveTransferState_rejectsABatchTooLargeForOneStatement() {
    val tooMany = List(AttachmentTable.ARCHIVE_MEDIA_KEY_BATCH_SIZE + 1) {
      AttachmentTable.MediaNameParts.fromBytes(plaintextHash = Random.nextBytes(8), remoteKey = Random.nextBytes(8))
    }

    SignalDatabase.attachments.resetArchiveTransferStateByPlaintextHashAndRemoteKeyIfNecessary(tooMany)
  }

  @Test
  fun resetArchiveTransferState_noResultsForAnEmptyBatch() {
    assertThat(SignalDatabase.attachments.resetArchiveThumbnailTransferStateByPlaintextHashAndRemoteKeyIfNecessary(emptyList())).isEmpty()
  }

  private fun insertArchivedAttachment(archiveCdn: Int?, localBackupKey: ByteArray?): AttachmentId {
    val from = recipients.createRecipient("Some Contact")
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(from))

    val attachment = createArchivedAttachment(archiveCdn = archiveCdn, localBackupKey = localBackupKey)
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = from,
      body = null,
      sentTimeMillis = 100L,
      serverTimeMillis = 100L,
      receivedTimeMillis = 200L,
      attachments = listOf(attachment)
    )

    val messageId = SignalDatabase.messages.insertMessageInbox(message, threadId).get().messageId
    return SignalDatabase.attachments.getAttachmentsForMessage(messageId).first().attachmentId
  }

  private fun createArchivedAttachment(archiveCdn: Int?, localBackupKey: ByteArray?): Attachment {
    return ArchivedAttachment(
      contentType = "image/jpeg",
      size = 1024,
      cdn = 3,
      uploadTimestamp = 0,
      key = Random.nextBytes(8),
      cdnKey = "password",
      archiveCdn = archiveCdn,
      plaintextHash = Random.nextBytes(8),
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
      localBackupKey = localBackupKey
    )
  }
}
