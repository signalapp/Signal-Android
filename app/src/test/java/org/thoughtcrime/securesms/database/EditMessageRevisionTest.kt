/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import android.database.Cursor
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.mockk.every
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.CursorUtil
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.util.RemoteConfig

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class EditMessageRevisionTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var senderId: RecipientId
  private var threadId: Long = 0
  private lateinit var contactId: RecipientId
  private var contactThreadId: Long = 0

  @Before
  fun setUp() {
    every { RemoteConfig.regularDeleteThreshold } returns 86_400L
    every { RemoteConfig.adminDeleteThreshold } returns 86_400L

    senderId = recipients.createRecipient("Sender Name")
    threadId = SignalDatabase.threads.getOrCreateThreadIdFor(senderId, false, ThreadTable.DistributionTypes.DEFAULT)
    contactId = recipients.createRecipient("Contact Name")
    contactThreadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(contactId))
  }

  @Test
  fun singleEditSetsLatestRevisionIdOnOriginal() {
    val originalId = insertOriginalMessage(sentTimeMillis = 1000)
    val editId = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1001)

    assertThat(getLatestRevisionId(originalId)).isNotNull().isEqualTo(editId)
    assertThat(getLatestRevisionId(editId)).isNull()
  }

  @Test
  fun singleEditOnlyLatestRevisionAppearsInNotificationState() {
    val originalId = insertOriginalMessage(sentTimeMillis = 1000)
    val editId = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1001)

    val notificationIds = getNotificationStateMessageIds()
    assertEquals(listOf(editId), notificationIds)
  }

  @Test
  fun multiEditSetsLatestRevisionIdOnAllPreviousRevisions() {
    val originalId = insertOriginalMessage(sentTimeMillis = 1000)

    val edit1Id = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1001)

    assertThat(getLatestRevisionId(originalId)).isNotNull().isEqualTo(edit1Id)
    assertThat(getLatestRevisionId(edit1Id)).isNull()

    val edit2Id = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1002)

    assertThat(getLatestRevisionId(originalId)).isNotNull().isEqualTo(edit2Id)
    assertThat(getLatestRevisionId(edit1Id)).isNotNull().isEqualTo(edit2Id)
  }

  @Test
  fun multiEditOnlyLatestRevisionAppearsInNotificationState() {
    val originalId = insertOriginalMessage(sentTimeMillis = 1000)

    insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1001)
    val edit2Id = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1002)

    val notificationIds = getNotificationStateMessageIds()
    assertEquals("Only the latest revision should appear in notification state", listOf(edit2Id), notificationIds)
  }

  @Test
  fun readSyncThenMultipleEditsDoNotCreateOrphanedUnreadRevisions() {
    val originalId = insertOriginalMessage(sentTimeMillis = 1000)

    markAsRead(originalId)
    assertEquals("No notifications after read sync", 0, getNotificationStateMessageIds().size)

    insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1001)
    insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1002)

    val notificationIds = getNotificationStateMessageIds()
    assertEquals(
      "No notifications should appear after edits to a message that was already read via sync",
      emptyList<Long>(),
      notificationIds
    )
  }

  @Test
  fun readSyncOnLatestRevisionThenSecondEditDoesNotCreateOrphanedNotification() {
    val originalId = insertOriginalMessage(sentTimeMillis = 1000)

    val edit1Id = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1001)

    // Read sync updates the latestRevisionId (edit1), not the original
    markAsRead(edit1Id)
    assertEquals("No notifications after read sync on edited message", 0, getNotificationStateMessageIds().size)

    val edit2Id = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1002)

    val notificationIds = getNotificationStateMessageIds()
    assertEquals(
      "Only the latest revision or no revisions should appear depending on read state",
      notificationIds.filter { it != edit2Id },
      emptyList<Long>()
    )
  }

  @Test
  fun tripleEditCorrectlyChainsAllRevisions() {
    val originalId = insertOriginalMessage(sentTimeMillis = 1000)

    val edit1Id = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1001)
    val edit2Id = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1002)
    val edit3Id = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1003)

    assertThat(getLatestRevisionId(originalId)).isNotNull().isEqualTo(edit3Id)
    assertThat(getLatestRevisionId(edit1Id)).isNotNull().isEqualTo(edit3Id)
    assertThat(getLatestRevisionId(edit2Id)).isNotNull().isEqualTo(edit3Id)
    assertThat(getLatestRevisionId(edit3Id)).isNull()

    assertEquals(listOf(edit3Id), getNotificationStateMessageIds())
  }

  @Test
  fun multiEditWithReadSyncBetweenEditsNotificationDismissedAndStaysDismissed() {
    val originalId = insertOriginalMessage(sentTimeMillis = 1000)

    assertEquals("Original unread message should be in notification state", 1, getNotificationStateMessageIds().size)

    markAsReadAndNotified(originalId)
    assertEquals("No notifications after read sync", 0, getNotificationStateMessageIds().size)

    insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1001)
    assertEquals("No notifications after first edit (original was read)", 0, getNotificationStateMessageIds().size)

    val edit2Id = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1002)

    val notificationIds = getNotificationStateMessageIds()
    assertEquals(
      "No notifications should appear - message was read via sync before edits arrived",
      emptyList<Long>(),
      notificationIds
    )

    // Verify revision chain integrity
    assertThat(getLatestRevisionId(originalId)).isNotNull().isEqualTo(edit2Id)
    val edit1Id = edit2Id - 1 // edit1 was inserted right before edit2
    assertThat(getLatestRevisionId(edit1Id)).isNotNull().isEqualTo(edit2Id)
    assertThat(getLatestRevisionId(edit2Id)).isNull()
  }

  @Test
  fun removeDuplicatesRepairsOrphanedLatestRevisionIdInsteadOfLeavingForeignKeyViolation() {
    val originalId = insertOriginalMessage(sentTimeMillis = 1000)
    val edit1Id = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1001)
    val edit2Id = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1002)

    assertThat(getLatestRevisionId(originalId)).isNotNull().isEqualTo(edit2Id)

    // Simulate what a backup import can produce: the latest revision is gone (deleted as a duplicate, or never inserted), but the
    // earlier revisions still point at it via latest_revision_id. Foreign keys are disabled during import, so this is not caught until
    // the final integrity check.
    SignalDatabase.writableDatabase.execSQL("PRAGMA foreign_keys=OFF")
    SignalDatabase.writableDatabase.execSQL("DELETE FROM ${MessageTable.TABLE_NAME} WHERE ${MessageTable.ID} = ?", arrayOf(edit2Id))
    assertThat(getLatestRevisionId(originalId)).isNotNull().isEqualTo(edit2Id)

    SignalDatabase.messages.removeDuplicatesPostBackupRestore()

    assertEquals("Orphaned latest_revision_id references must be cleaned up", 0, countDanglingLatestRevisionIds())
    SignalDatabase.writableDatabase.execSQL("PRAGMA foreign_keys=ON")
  }

  @Test
  fun outgoingSequentialEditsChainProperly() {
    val originalId = recipients.insertOutgoingMessage(contactId, body = "original", sentTimeMillis = 2000)
    val edit1Id = insertOutgoingEdit(messageToEdit = originalId, sentTimeMillis = 2001)
    val edit2Id = insertOutgoingEdit(messageToEdit = edit1Id, sentTimeMillis = 2002)

    assertThat(getLatestRevisionId(originalId)).isNotNull().isEqualTo(edit2Id)
    assertThat(getLatestRevisionId(edit1Id)).isNotNull().isEqualTo(edit2Id)
    assertThat(getLatestRevisionId(edit2Id)).isNull()

    assertEquals("Exactly one visible revision should remain", 1, countVisibleRevisions(originalId))
  }

  @Test
  fun outgoingEditTargetingStaleRevisionDoesNotDuplicate() {
    val originalId = recipients.insertOutgoingMessage(contactId, body = "original", sentTimeMillis = 2000)
    val edit1Id = insertOutgoingEdit(messageToEdit = originalId, sentTimeMillis = 2001)
    val edit2Id = insertOutgoingEdit(messageToEdit = originalId, sentTimeMillis = 2002)

    assertThat(getLatestRevisionId(originalId)).isNotNull().isEqualTo(edit2Id)
    assertThat(getLatestRevisionId(edit1Id)).isNotNull().isEqualTo(edit2Id)
    assertThat(getLatestRevisionId(edit2Id)).isNull()

    assertEquals("A stale-target edit must not produce a duplicate visible revision", 1, countVisibleRevisions(originalId))
  }

  @Test
  fun outgoingEditTargetingStaleRevisionNumbersFromLatestRevision() {
    val originalId = recipients.insertOutgoingMessage(contactId, body = "original", sentTimeMillis = 2000)
    val edit1Id = insertOutgoingEdit(messageToEdit = originalId, sentTimeMillis = 2001)
    val edit2Id = insertOutgoingEdit(messageToEdit = originalId, sentTimeMillis = 2002)

    assertEquals("First edit is revision 1", 1, getRevisionNumber(edit1Id))
    assertEquals("Second edit builds on the first, not on the original", 2, getRevisionNumber(edit2Id))
    assertEquals("Every revision points back at the chain root", originalId, getOriginalMessageId(edit2Id))
  }

  @Test
  fun outgoingEditTargetingStaleRevisionMovesReactionsToNewRevision() {
    val originalId = recipients.insertOutgoingMessage(contactId, body = "original", sentTimeMillis = 2000)
    val edit1Id = insertOutgoingEdit(messageToEdit = originalId, sentTimeMillis = 2001)
    SignalDatabase.reactions.addReaction(MessageId(edit1Id), ReactionRecord(emoji = "\uD83D\uDC4D", author = contactId, dateSent = 2001, dateReceived = 2001))

    val edit2Id = insertOutgoingEdit(messageToEdit = originalId, sentTimeMillis = 2002)

    assertEquals("Reactions must follow the chain onto the visible revision", 1, SignalDatabase.reactions.getReactions(MessageId(edit2Id)).size)
    assertEquals("Reactions must not be stranded on a hidden revision", 0, SignalDatabase.reactions.getReactions(MessageId(edit1Id)).size)
  }

  @Test
  fun outgoingEditRecoversFromChainWithMultipleVisibleRevisions() {
    val originalId = recipients.insertOutgoingMessage(contactId, body = "original", sentTimeMillis = 2000)
    val edit1Id = insertOutgoingEdit(messageToEdit = originalId, sentTimeMillis = 2001)
    val edit2Id = insertOutgoingEdit(messageToEdit = edit1Id, sentTimeMillis = 2002)

    SignalDatabase.writableDatabase.execSQL(
      "UPDATE ${MessageTable.TABLE_NAME} SET ${MessageTable.LATEST_REVISION_ID} = NULL WHERE ${MessageTable.ID} = ?",
      arrayOf(edit1Id)
    )
    assertEquals("Precondition: chain is damaged", 2, countVisibleRevisions(originalId))

    val edit3Id = insertOutgoingEdit(messageToEdit = edit1Id, sentTimeMillis = 2003)

    assertEquals("A damaged chain must collapse back to a single visible revision", 1, countVisibleRevisions(originalId))
    assertThat(getLatestRevisionId(originalId)).isNotNull().isEqualTo(edit3Id)
    assertThat(getLatestRevisionId(edit1Id)).isNotNull().isEqualTo(edit3Id)
    assertThat(getLatestRevisionId(edit2Id)).isNotNull().isEqualTo(edit3Id)
    assertThat(getLatestRevisionId(edit3Id)).isNull()
  }

  @Test
  fun outgoingEditCollapsesVisibleChainRoot() {
    val originalId = recipients.insertOutgoingMessage(contactId, body = "original", sentTimeMillis = 2000)
    val edit1Id = insertOutgoingEdit(messageToEdit = originalId, sentTimeMillis = 2001)

    SignalDatabase.writableDatabase.execSQL(
      "UPDATE ${MessageTable.TABLE_NAME} SET ${MessageTable.LATEST_REVISION_ID} = NULL WHERE ${MessageTable.ID} = ?",
      arrayOf(originalId)
    )
    assertEquals("Precondition: chain root is visible", 2, countVisibleRevisions(originalId))

    val edit2Id = insertOutgoingEdit(messageToEdit = originalId, sentTimeMillis = 2002)

    assertEquals("A visible chain root must be collapsed too", 1, countVisibleRevisions(originalId))
    assertThat(getLatestRevisionId(originalId)).isNotNull().isEqualTo(edit2Id)
    assertThat(getLatestRevisionId(edit1Id)).isNotNull().isEqualTo(edit2Id)
    assertThat(getLatestRevisionId(edit2Id)).isNull()
  }

  private fun insertOutgoingEdit(messageToEdit: Long, sentTimeMillis: Long): Long {
    val message = OutgoingMessage(
      recipient = Recipient.resolved(contactId),
      body = "edited at $sentTimeMillis",
      timestamp = sentTimeMillis,
      isSecure = true,
      messageToEdit = messageToEdit
    )
    return recipients.insertOutgoingMessage(message, contactThreadId)
  }

  private fun countVisibleRevisions(originalId: Long): Int {
    return SignalDatabase.writableDatabase
      .query(
        "SELECT COUNT(*) FROM ${MessageTable.TABLE_NAME} WHERE ${MessageTable.LATEST_REVISION_ID} IS NULL AND (${MessageTable.ID} = ? OR ${MessageTable.ORIGINAL_MESSAGE_ID} = ?)",
        arrayOf(originalId, originalId)
      )
      .use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
      }
  }

  private fun getRevisionNumber(messageId: Long): Int {
    return readColumn(messageId, MessageTable.REVISION_NUMBER) { cursor, idx -> cursor.getInt(idx) }
  }

  private fun getOriginalMessageId(messageId: Long): Long {
    return readColumn(messageId, MessageTable.ORIGINAL_MESSAGE_ID) { cursor, idx -> cursor.getLong(idx) }
  }

  private fun <T> readColumn(messageId: Long, column: String, read: (Cursor, Int) -> T): T {
    return SignalDatabase.writableDatabase
      .query(MessageTable.TABLE_NAME, arrayOf(column), "${MessageTable.ID} = ?", arrayOf(messageId.toString()), null, null, null)
      .use { cursor ->
        require(cursor.moveToFirst()) { "No message with id $messageId" }
        read(cursor, cursor.getColumnIndexOrThrow(column))
      }
  }

  private fun countDanglingLatestRevisionIds(): Int {
    return SignalDatabase.writableDatabase
      .query("SELECT COUNT(*) FROM ${MessageTable.TABLE_NAME} WHERE ${MessageTable.LATEST_REVISION_ID} IS NOT NULL AND ${MessageTable.LATEST_REVISION_ID} NOT IN (SELECT ${MessageTable.ID} FROM ${MessageTable.TABLE_NAME})")
      .use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
      }
  }

  private fun insertOriginalMessage(sentTimeMillis: Long): Long {
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = senderId,
      sentTimeMillis = sentTimeMillis,
      serverTimeMillis = sentTimeMillis,
      receivedTimeMillis = System.currentTimeMillis(),
      body = "original message"
    )
    return SignalDatabase.messages.insertMessageInbox(message, threadId).get().messageId
  }

  /**
   * The target is always retrieved via [MessageTable.getMessageFor] using the original sent
   * timestamp — this matches what [EditMessageProcessor] does and means targetMessage.id
   * is always the original message's row ID.
   */
  private fun insertEdit(originalSentTimestamp: Long, editSentTimeMillis: Long): Long {
    val targetMessage = SignalDatabase.messages.getMessageFor(originalSentTimestamp, senderId) as MmsMessageRecord

    val editMessage = IncomingMessage(
      type = MessageType.NORMAL,
      from = senderId,
      sentTimeMillis = editSentTimeMillis,
      serverTimeMillis = editSentTimeMillis,
      receivedTimeMillis = System.currentTimeMillis(),
      body = "edited at $editSentTimeMillis"
    )
    return SignalDatabase.messages.insertEditMessageInbox(editMessage, targetMessage).get().messageId
  }

  private fun getLatestRevisionId(messageId: Long): Long? {
    return SignalDatabase.writableDatabase
      .query(MessageTable.TABLE_NAME, arrayOf(MessageTable.LATEST_REVISION_ID), "${MessageTable.ID} = ?", arrayOf(messageId.toString()), null, null, null)
      .use { cursor ->
        if (cursor.moveToFirst()) {
          val idx = cursor.getColumnIndexOrThrow(MessageTable.LATEST_REVISION_ID)
          if (cursor.isNull(idx)) null else cursor.getLong(idx)
        } else {
          null
        }
      }
  }

  private fun getNotificationStateMessageIds(): List<Long> {
    return SignalDatabase.messages.getMessagesForNotificationState(emptyList()).use { cursor ->
      val ids = mutableListOf<Long>()
      while (cursor.moveToNext()) {
        ids.add(CursorUtil.requireLong(cursor, MessageTable.ID))
      }
      ids
    }
  }

  private fun markAsRead(messageId: Long) {
    SignalDatabase.writableDatabase.execSQL(
      "UPDATE ${MessageTable.TABLE_NAME} SET ${MessageTable.READ} = 1 WHERE ${MessageTable.ID} = ?",
      arrayOf(messageId)
    )
  }

  private fun markAsReadAndNotified(messageId: Long) {
    SignalDatabase.writableDatabase.execSQL(
      "UPDATE ${MessageTable.TABLE_NAME} SET ${MessageTable.READ} = 1, ${MessageTable.NOTIFIED} = 1 WHERE ${MessageTable.ID} = ?",
      arrayOf(messageId)
    )
  }
}
