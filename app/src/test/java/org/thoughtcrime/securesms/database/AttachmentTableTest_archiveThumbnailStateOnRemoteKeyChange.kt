/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.database.update
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.logging.Log
import org.signal.network.api.AttachmentUploadResult
import org.thoughtcrime.securesms.attachments.ArchivedAttachment
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.database.AttachmentTable.ArchiveTransferState
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import java.util.UUID
import kotlin.random.Random

/**
 * The archived thumbnail's mediaName is derived from the remote key, so any write that gives an archived attachment a new remote key has to clear its
 * thumbnail archive state too, or the thumbnail is never re-uploaded under the new name.
 */
@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AttachmentTableTest_archiveThumbnailStateOnRemoteKeyChange {

  @get:Rule val recipients = RecipientTestRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @Test
  fun `given an archived attachment, when I download identical bytes under a new key, then I expect the thumbnail state reset`() {
    val data = Random.nextBytes(2048)
    val (existingMessageId, existingId) = insertAttachment()
    SignalDatabase.attachments.finalizeAttachmentAfterDownload(existingMessageId, existingId, data.inputStream(), notify = false)
    markFullyArchived(existingId)

    val (newMessageId, newId) = insertAttachment()
    SignalDatabase.attachments.finalizeAttachmentAfterDownload(newMessageId, newId, data.inputStream(), notify = false)

    val existing = SignalDatabase.attachments.getAttachment(existingId)!!
    val new = SignalDatabase.attachments.getAttachment(newId)!!
    assertThat(existing.remoteKey).isEqualTo(new.remoteKey)
    assertThat(existing.archiveTransferState).isEqualTo(ArchiveTransferState.NONE)
    assertThat(existing.archiveThumbnailTransferState).isEqualTo(ArchiveTransferState.NONE)
    assertThat(new.archiveThumbnailTransferState).isEqualTo(ArchiveTransferState.NONE)
  }

  @Test
  fun `given an archived attachment, when I download different bytes, then I expect the thumbnail state untouched`() {
    val (existingMessageId, existingId) = insertAttachment()
    SignalDatabase.attachments.finalizeAttachmentAfterDownload(existingMessageId, existingId, Random.nextBytes(2048).inputStream(), notify = false)
    markFullyArchived(existingId)

    val (newMessageId, newId) = insertAttachment()
    SignalDatabase.attachments.finalizeAttachmentAfterDownload(newMessageId, newId, Random.nextBytes(2048).inputStream(), notify = false)

    val existing = SignalDatabase.attachments.getAttachment(existingId)!!
    assertThat(existing.archiveTransferState).isEqualTo(ArchiveTransferState.FINISHED)
    assertThat(existing.archiveThumbnailTransferState).isEqualTo(ArchiveTransferState.FINISHED)
  }

  @Test
  fun `given an archived attachment, when I finalize an upload under a new key, then I expect the thumbnail state reset`() {
    val (messageId, attachmentId) = insertAttachment()
    SignalDatabase.attachments.finalizeAttachmentAfterDownload(messageId, attachmentId, Random.nextBytes(2048).inputStream(), notify = false)
    markFullyArchived(attachmentId)

    SignalDatabase.attachments.finalizeAttachmentAfterUpload(attachmentId, uploadResult())

    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)!!
    assertThat(attachment.archiveTransferState).isEqualTo(ArchiveTransferState.NONE)
    assertThat(attachment.archiveThumbnailTransferState).isEqualTo(ArchiveTransferState.NONE)
  }

  private fun markFullyArchived(attachmentId: AttachmentId) {
    SignalDatabase.attachments.writableDatabase
      .update(AttachmentTable.TABLE_NAME)
      .values(
        AttachmentTable.ARCHIVE_CDN to 3,
        AttachmentTable.ARCHIVE_TRANSFER_STATE to ArchiveTransferState.FINISHED.value,
        AttachmentTable.ARCHIVE_THUMBNAIL_TRANSFER_STATE to ArchiveTransferState.FINISHED.value
      )
      .where("${AttachmentTable.ID} = ?", attachmentId.id)
      .run()
  }

  private fun insertAttachment(): Pair<Long, AttachmentId> {
    val from = recipients.createRecipient("Some Contact")
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(from))

    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = from,
      body = null,
      sentTimeMillis = 100L,
      serverTimeMillis = 100L,
      receivedTimeMillis = 200L,
      attachments = listOf(createAttachment())
    )

    val messageId = SignalDatabase.messages.insertMessageInbox(message, threadId).get().messageId
    return messageId to SignalDatabase.attachments.getAttachmentsForMessage(messageId).first().attachmentId
  }

  private fun createAttachment(): Attachment {
    return ArchivedAttachment(
      contentType = "image/jpeg",
      size = 2048,
      cdn = 3,
      uploadTimestamp = 0,
      key = Random.nextBytes(64),
      cdnKey = "password",
      archiveCdn = null,
      plaintextHash = Random.nextBytes(32),
      incrementalMac = null,
      incrementalMacChunkSize = 0,
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

  private fun uploadResult(): AttachmentUploadResult {
    return AttachmentUploadResult(
      remoteId = SignalServiceAttachmentRemoteId.V4("cdn-key"),
      cdnNumber = 3,
      key = Random.nextBytes(64),
      digest = Random.nextBytes(32),
      incrementalDigest = null,
      incrementalDigestChunkSize = 0,
      dataSize = 2048,
      uploadTimestamp = 0,
      blurHash = null,
      audioHash = null
    )
  }
}
