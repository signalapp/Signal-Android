package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.CursorUtil
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.SignalDatabaseRule
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EditMessageRevisionTest {

  @get:Rule
  val databaseRule = SignalDatabaseRule()

  private lateinit var senderId: RecipientId
  private var threadId: Long = 0

  @Before
  fun setUp() {
    val senderAci = ACI.from(UUID.randomUUID())
    senderId = SignalDatabase.recipients.getOrInsertFromServiceId(senderAci)
    threadId = SignalDatabase.threads.getOrCreateThreadIdFor(senderId, false, ThreadTable.DistributionTypes.DEFAULT)
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
    return SignalDatabase.rawDatabase
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
    SignalDatabase.rawDatabase.execSQL(
      "UPDATE ${MessageTable.TABLE_NAME} SET ${MessageTable.READ} = 1 WHERE ${MessageTable.ID} = ?",
      arrayOf(messageId)
    )
  }

  private fun markAsReadAndNotified(messageId: Long) {
    SignalDatabase.rawDatabase.execSQL(
      "UPDATE ${MessageTable.TABLE_NAME} SET ${MessageTable.READ} = 1, ${MessageTable.NOTIFIED} = 1 WHERE ${MessageTable.ID} = ?",
      arrayOf(messageId)
    )
  }
}
