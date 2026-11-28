/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.database.update
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.ArchivedAttachment
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.database.AttachmentTable.ArchiveTransferState
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import java.util.UUID
import kotlin.random.Random

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AttachmentTableTest_pendingArchiveUpload {

  @get:Rule val recipients = RecipientTestRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @Test
  fun `attachments in every unfinished state count as pending`() {
    val pendingStates = listOf(
      ArchiveTransferState.NONE,
      ArchiveTransferState.UPLOAD_IN_PROGRESS,
      ArchiveTransferState.COPY_PENDING,
      ArchiveTransferState.TEMPORARY_FAILURE
    )

    for (state in pendingStates) {
      val attachmentId = insertAttachment(state, dataSize = 100)

      assertThat(SignalDatabase.attachments.areAnyAttachmentsPendingArchiveUpload(), state.name).isTrue()
      assertThat(SignalDatabase.attachments.getPendingArchiveUploadBytes(), state.name).isEqualTo(100L)

      setArchiveTransferState(attachmentId, ArchiveTransferState.FINISHED)
    }
  }

  @Test
  fun `finished and permanently failed attachments do not count as pending`() {
    insertAttachment(ArchiveTransferState.FINISHED, dataSize = 100)
    insertAttachment(ArchiveTransferState.PERMANENT_FAILURE, dataSize = 200)

    assertThat(SignalDatabase.attachments.areAnyAttachmentsPendingArchiveUpload()).isFalse()
    assertThat(SignalDatabase.attachments.getPendingArchiveUploadBytes()).isEqualTo(0L)
  }

  @Test
  fun `pending bytes only sum the attachments that still need an upload`() {
    insertAttachment(ArchiveTransferState.NONE, dataSize = 100)
    insertAttachment(ArchiveTransferState.TEMPORARY_FAILURE, dataSize = 20)
    insertAttachment(ArchiveTransferState.FINISHED, dataSize = 1000)
    insertAttachment(ArchiveTransferState.PERMANENT_FAILURE, dataSize = 5000)

    assertThat(SignalDatabase.attachments.areAnyAttachmentsPendingArchiveUpload()).isTrue()
    assertThat(SignalDatabase.attachments.getPendingArchiveUploadBytes()).isEqualTo(120L)
  }

  @Test
  fun `attachments without local data do not count as pending`() {
    insertAttachment(ArchiveTransferState.NONE, dataSize = 100, hasLocalData = false)

    assertThat(SignalDatabase.attachments.areAnyAttachmentsPendingArchiveUpload()).isFalse()
    assertThat(SignalDatabase.attachments.getPendingArchiveUploadBytes()).isEqualTo(0L)
  }

  private fun insertAttachment(state: ArchiveTransferState, dataSize: Long, hasLocalData: Boolean = true): AttachmentId {
    val attachmentId = insertArchivedAttachment()

    SignalDatabase.attachments.writableDatabase
      .update(AttachmentTable.TABLE_NAME)
      .values(
        AttachmentTable.DATA_FILE to if (hasLocalData) "/fake/path/data-${attachmentId.id}" else null,
        AttachmentTable.DATA_RANDOM to if (hasLocalData) Random.nextBytes(32) else null,
        AttachmentTable.DATA_SIZE to dataSize,
        AttachmentTable.TRANSFER_STATE to AttachmentTable.TRANSFER_PROGRESS_DONE
      )
      .where("${AttachmentTable.ID} = ?", attachmentId.id)
      .run()

    setArchiveTransferState(attachmentId, state)
    return attachmentId
  }

  private fun setArchiveTransferState(attachmentId: AttachmentId, state: ArchiveTransferState) {
    SignalDatabase.attachments.writableDatabase
      .update(AttachmentTable.TABLE_NAME)
      .values(AttachmentTable.ARCHIVE_TRANSFER_STATE to state.value)
      .where("${AttachmentTable.ID} = ?", attachmentId.id)
      .run()
  }

  private fun insertArchivedAttachment(): AttachmentId {
    val from = recipients.createRecipient("Some Contact")
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(from))

    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = from,
      body = null,
      sentTimeMillis = 100L,
      serverTimeMillis = 100L,
      receivedTimeMillis = 200L,
      attachments = listOf(createArchivedAttachment())
    )

    val messageId = SignalDatabase.messages.insertMessageInbox(message, threadId).get().messageId
    return SignalDatabase.attachments.getAttachmentsForMessage(messageId).first().attachmentId
  }

  private fun createArchivedAttachment(): Attachment {
    return ArchivedAttachment(
      contentType = "image/jpeg",
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
