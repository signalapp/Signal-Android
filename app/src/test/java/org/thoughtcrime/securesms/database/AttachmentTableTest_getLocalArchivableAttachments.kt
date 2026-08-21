/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
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
 * JVM (Robolectric) coverage for [AttachmentTable.getLocalArchivableAttachments]. Verifies that attachments encrypted with the classic scheme (a NULL
 * data_random, predating modern per-attachment randoms) are still listed for local archiving rather than crashing the whole backup export.
 */
@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AttachmentTableTest_getLocalArchivableAttachments {

  @get:Rule val recipients = RecipientTestRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @Test
  fun givenClassicAttachmentWithNullDataRandom_whenGetLocalArchivableAttachments_thenItIsReturnedWithNullRandom() {
    val attachmentId = insertLocalArchivableAttachment(dataRandom = null)

    val result = SignalDatabase.attachments.getLocalArchivableAttachments()

    assertThat(result).hasSize(1)
    assertThat(result.single().attachmentId).isEqualTo(attachmentId)
    assertThat(result.single().random).isNull()
  }

  @Test
  fun givenModernAttachment_whenGetLocalArchivableAttachments_thenItKeepsItsRandom() {
    val random = Random.nextBytes(32)
    insertLocalArchivableAttachment(dataRandom = random)

    val result = SignalDatabase.attachments.getLocalArchivableAttachments()

    assertThat(result).hasSize(1)
    assertThat(result.single().random contentEquals random).isEqualTo(true)
  }

  @Test
  fun givenMixOfClassicAndModernAttachments_whenGetLocalArchivableAttachments_thenBothAreReturned() {
    insertLocalArchivableAttachment(dataRandom = null)
    insertLocalArchivableAttachment(dataRandom = Random.nextBytes(32))

    val result = SignalDatabase.attachments.getLocalArchivableAttachments()

    assertThat(result).hasSize(2)
  }

  /**
   * Inserts an attachment that satisfies the local-archivable criteria (non-null data_file, data_hash_end, and metadata local_backup_key),
   * then forces data_random to [dataRandom] to simulate a classic-era (NULL) or modern (32-byte) row.
   */
  private fun insertLocalArchivableAttachment(dataRandom: ByteArray?): AttachmentId {
    val from = recipients.createRecipient("Some Contact")
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(from))

    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = from,
      body = null,
      sentTimeMillis = 100L,
      serverTimeMillis = 100L,
      receivedTimeMillis = 200L,
      attachments = listOf(createAttachment(localBackupKey = Random.nextBytes(32)))
    )

    val messageId = SignalDatabase.messages.insertMessageInbox(message, threadId).get().messageId
    val attachmentId = SignalDatabase.attachments.getAttachmentsForMessage(messageId).first().attachmentId

    SignalDatabase.attachments.writableDatabase
      .update(AttachmentTable.TABLE_NAME)
      .values(
        AttachmentTable.DATA_FILE to "/data/parts/${attachmentId.id}.mms",
        AttachmentTable.DATA_RANDOM to dataRandom,
        AttachmentTable.DATA_HASH_END to Base64.encodeWithPadding(Random.nextBytes(32)),
        AttachmentTable.DATA_SIZE to 1024L
      )
      .where("${AttachmentTable.ID} = ?", attachmentId.id)
      .run()

    return attachmentId
  }

  private fun createAttachment(localBackupKey: ByteArray?): Attachment {
    return ArchivedAttachment(
      contentType = "image/jpeg",
      size = 1024,
      cdn = 3,
      uploadTimestamp = 0,
      key = Random.nextBytes(8),
      cdnKey = "password",
      archiveCdn = null,
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
