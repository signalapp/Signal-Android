/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import android.content.ContentValues
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.testutil.SignalDatabaseMigrationRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class V298_DoNotBackupReleaseNotesTest {

  @get:Rule val signalDatabaseRule = SignalDatabaseMigrationRule(297)

  private val releaseNoteRecipientId = 100L
  private val otherRecipientId = 200L

  @Test
  fun `migrate - attachments from release note recipient - clears archive transfer state`() {
    val messageId = insertMessage(fromRecipientId = releaseNoteRecipientId)
    val attachmentId = insertAttachment(
      messageId = messageId,
      archiveTransferState = AttachmentTable.ArchiveTransferState.FINISHED.value,
      archiveCdn = 2
    )

    runMigration()

    assertArchiveState(attachmentId, expectedState = AttachmentTable.ArchiveTransferState.NONE.value)
  }

  @Test
  fun `migrate - attachments from other recipient - no change`() {
    val messageId = insertMessage(fromRecipientId = otherRecipientId)
    val attachmentId = insertAttachment(
      messageId = messageId,
      archiveTransferState = AttachmentTable.ArchiveTransferState.FINISHED.value,
      archiveCdn = 2
    )

    runMigration()

    assertArchiveState(attachmentId, expectedState = AttachmentTable.ArchiveTransferState.FINISHED.value)
  }

  @Test
  fun `migrate - multiple attachments from release note recipient - all cleared`() {
    val messageId1 = insertMessage(fromRecipientId = releaseNoteRecipientId)
    val messageId2 = insertMessage(fromRecipientId = releaseNoteRecipientId)

    val attachmentId1 = insertAttachment(
      messageId = messageId1,
      archiveTransferState = AttachmentTable.ArchiveTransferState.FINISHED.value,
      archiveCdn = 2
    )
    val attachmentId2 = insertAttachment(
      messageId = messageId2,
      archiveTransferState = AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS.value,
      archiveCdn = 3
    )

    runMigration()

    assertArchiveState(attachmentId1, expectedState = AttachmentTable.ArchiveTransferState.NONE.value)
    assertArchiveState(attachmentId2, expectedState = AttachmentTable.ArchiveTransferState.NONE.value)
  }

  @Test
  fun `migrate - mixed recipients - only release note attachments cleared`() {
    val releaseNoteMessageId = insertMessage(fromRecipientId = releaseNoteRecipientId)
    val otherMessageId = insertMessage(fromRecipientId = otherRecipientId)

    val releaseNoteAttachmentId = insertAttachment(
      messageId = releaseNoteMessageId,
      archiveTransferState = AttachmentTable.ArchiveTransferState.FINISHED.value,
      archiveCdn = 2
    )
    val otherAttachmentId = insertAttachment(
      messageId = otherMessageId,
      archiveTransferState = AttachmentTable.ArchiveTransferState.FINISHED.value,
      archiveCdn = 2
    )

    runMigration()

    assertArchiveState(releaseNoteAttachmentId, expectedState = AttachmentTable.ArchiveTransferState.NONE.value)
    assertArchiveState(otherAttachmentId, expectedState = AttachmentTable.ArchiveTransferState.FINISHED.value)
  }

  private fun runMigration() {
    V298_DoNotBackupReleaseNotes.migrateWithRecipientId(
      db = signalDatabaseRule.database,
      releaseNoteRecipientId = releaseNoteRecipientId
    )
  }

  private val insertedRecipients = mutableSetOf<Long>()
  private val insertedThreads = mutableMapOf<Long, Long>()
  private var dateSentCounter = System.currentTimeMillis()

  private fun insertRecipient(recipientId: Long): Long {
    if (recipientId in insertedRecipients) {
      return recipientId
    }

    val db = signalDatabaseRule.database

    val values = ContentValues().apply {
      put("_id", recipientId)
      put("type", 0)
    }

    db.insert("recipient", null, values)
    insertedRecipients.add(recipientId)
    return recipientId
  }

  private fun insertThread(recipientId: Long): Long {
    insertedThreads[recipientId]?.let { return it }

    val db = signalDatabaseRule.database

    val values = ContentValues().apply {
      put("recipient_id", recipientId)
      put("date", System.currentTimeMillis())
    }

    val threadId = db.insert("thread", null, values)
    insertedThreads[recipientId] = threadId
    return threadId
  }

  private fun insertMessage(fromRecipientId: Long): Long {
    val db = signalDatabaseRule.database

    insertRecipient(fromRecipientId)
    val threadId = insertThread(fromRecipientId)
    dateSentCounter++

    val values = ContentValues().apply {
      put("date_sent", dateSentCounter)
      put("date_received", System.currentTimeMillis())
      put("thread_id", threadId)
      put("from_recipient_id", fromRecipientId)
      put("to_recipient_id", fromRecipientId)
      put("type", 0)
    }

    return db.insert("message", null, values)
  }

  private fun insertAttachment(messageId: Long, archiveTransferState: Int, archiveCdn: Int?): Long {
    val db = signalDatabaseRule.database

    val values = ContentValues().apply {
      put("message_id", messageId)
      put("data_file", "/fake/path/attachment.jpg")
      put("data_random", "/fake/path/attachment.jpg".toByteArray())
      put("transfer_state", 0)
      put("archive_transfer_state", archiveTransferState)
      if (archiveCdn != null) {
        put("archive_cdn", archiveCdn)
      }
    }

    return db.insert("attachment", null, values)
  }

  private fun assertArchiveState(attachmentId: Long, expectedState: Int) {
    val db = signalDatabaseRule.database
    val cursor = db.query(
      "attachment",
      arrayOf("archive_transfer_state"),
      "_id = ?",
      arrayOf(attachmentId.toString()),
      null,
      null,
      null
    )

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)
      assertThat(it.getInt(it.getColumnIndexOrThrow("archive_transfer_state"))).isEqualTo(expectedState)
    }
  }
}
