/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule

/**
 * Verifies [MessageTable.getOldestUnread] — the unread-divider anchor — and that it stays in agreement with
 * [MessageTable.getUnreadCount] and [MessageTable.getMessagePositionByDateReceivedTimestamp] across reads, edits,
 * and special message types.
 */
@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MessageTableTest_oldestUnread {

  @get:Rule
  val recipients = RecipientTestRule()

  private val messages: MessageTable
    get() = SignalDatabase.messages

  private lateinit var senderId: RecipientId
  private var threadId: Long = 0

  @Before
  fun setUp() {
    senderId = recipients.createRecipient("Sender Name")
    threadId = SignalDatabase.threads.getOrCreateThreadIdFor(senderId, false, ThreadTable.DistributionTypes.DEFAULT)
  }

  @Test
  fun returnsNullWhenThreadHasNoMessages() {
    assertThat(messages.getOldestUnread(threadId)).isNull()
  }

  @Test
  fun returnsNullWhenAllMessagesAreRead() {
    val id = insertIncoming(time = 1000)
    markRead(id)

    assertThat(messages.getOldestUnread(threadId)).isNull()
  }

  @Test
  fun returnsTheOldestUnreadByDateReceived() {
    val first = insertIncoming(time = 1000)
    insertIncoming(time = 1001)
    insertIncoming(time = 1002)

    val oldest = messages.getOldestUnread(threadId)
    assertThat(oldest?.id).isEqualTo(first)
    assertThat(oldest?.dateReceived).isEqualTo(1000)
  }

  @Test
  fun skipsReadMessagesAndReturnsTheNextUnread() {
    val first = insertIncoming(time = 1000)
    val second = insertIncoming(time = 1001)
    markRead(first)

    assertThat(messages.getOldestUnread(threadId)?.id).isEqualTo(second)
  }

  @Test
  fun ignoresOutgoingMessages() {
    insertOutgoing(time = 1000)
    assertThat(messages.getOldestUnread(threadId)).isNull()

    val incoming = insertIncoming(time = 1001)
    assertThat(messages.getOldestUnread(threadId)?.id).isEqualTo(incoming)
  }

  @Test
  fun excludesPinnedMessagesToMatchUnreadCount() {
    val pinned = insertIncoming(time = 1000)
    val normal = insertIncoming(time = 1001)
    setPinned(pinned)

    assertThat(messages.getUnreadCount(threadId)).isEqualTo(1)
    assertThat(messages.getOldestUnread(threadId)?.id).isEqualTo(normal)
  }

  @Test
  fun anchorsToTheLatestRevisionOfAnEditedMessage() {
    insertIncoming(time = 1000)
    val edit = insertEdit(originalSentTimestamp = 1000, editTime = 1001)

    assertThat(messages.getOldestUnread(threadId)?.id).isEqualTo(edit)
  }

  @Test
  fun scrollPositionFromAnchorMatchesItsDisplayIndex() {
    insertIncoming(time = 1000)
    insertIncoming(time = 1001)
    insertIncoming(time = 1002)

    val oldest = messages.getOldestUnread(threadId)
    assertThat(oldest).isNotNull()

    // The oldest of three displayed messages has two newer messages, so it sits at index 2.
    val position = messages.getMessagePositionByDateReceivedTimestamp(threadId, oldest!!.dateReceived, false)
    assertThat(position).isEqualTo(2)
  }

  @Test
  fun unreadCountAndAnchorStayConsistentAsMessagesAreRead() {
    val a = insertIncoming(time = 1000)
    val b = insertIncoming(time = 1001)
    val c = insertIncoming(time = 1002)

    assertThat(messages.getUnreadCount(threadId)).isEqualTo(3)
    assertThat(messages.getOldestUnread(threadId)?.id).isEqualTo(a)

    markRead(a)
    assertThat(messages.getUnreadCount(threadId)).isEqualTo(2)
    assertThat(messages.getOldestUnread(threadId)?.id).isEqualTo(b)

    markRead(b)
    markRead(c)
    assertThat(messages.getUnreadCount(threadId)).isEqualTo(0)
    assertThat(messages.getOldestUnread(threadId)).isNull()
  }

  // region helpers

  private fun insertIncoming(time: Long): Long {
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = senderId,
      sentTimeMillis = time,
      serverTimeMillis = time,
      receivedTimeMillis = time,
      body = "msg $time"
    )
    return messages.insertMessageInbox(message, threadId).get().messageId
  }

  private fun insertOutgoing(time: Long): Long {
    val message = OutgoingMessage.text(
      threadRecipient = Recipient.resolved(senderId),
      body = "out $time",
      expiresIn = 0,
      sentTimeMillis = time
    )
    return messages.insertMessageOutbox(message, threadId).messageId
  }

  private fun insertEdit(originalSentTimestamp: Long, editTime: Long): Long {
    val target = messages.getMessageFor(originalSentTimestamp, senderId) as MmsMessageRecord
    val editMessage = IncomingMessage(
      type = MessageType.NORMAL,
      from = senderId,
      sentTimeMillis = editTime,
      serverTimeMillis = editTime,
      receivedTimeMillis = editTime,
      body = "edited at $editTime"
    )
    return messages.insertEditMessageInbox(editMessage, target).get().messageId
  }

  private fun markRead(messageId: Long) {
    SignalDatabase.writableDatabase.execSQL(
      "UPDATE ${MessageTable.TABLE_NAME} SET ${MessageTable.READ} = 1 WHERE ${MessageTable.ID} = ?",
      arrayOf(messageId)
    )
  }

  private fun setPinned(messageId: Long) {
    val mask = MessageTypes.SPECIAL_TYPES_MASK
    val pinned = MessageTypes.SPECIAL_TYPE_PINNED_MESSAGE
    SignalDatabase.writableDatabase.execSQL(
      "UPDATE ${MessageTable.TABLE_NAME} SET ${MessageTable.TYPE} = (${MessageTable.TYPE} & ~$mask) | $pinned WHERE ${MessageTable.ID} = ?",
      arrayOf(messageId)
    )
  }

  // endregion
}
