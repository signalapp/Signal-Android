/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.whispersystems.signalservice.internal.push.SyncMessage

/**
 * Verifies that read syncs from a linked device mark the whole thread read up to the synced message, including when
 * the messages were received long after they were sent (e.g. a backlog drained after the device was offline).
 */
@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MessageTableTest_readSyncTimestamps {

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
  fun latestReadIsTrackedByDateReceived() {
    insertIncoming(sentTime = 1000, receivedTime = 5000)
    insertIncoming(sentTime = 1001, receivedTime = 5001)

    val threadToLatestRead: MutableMap<Long, Long> = mutableMapOf()
    messages.setTimestampReadFromSyncMessage(listOf(readSync(1001)), 1001, threadToLatestRead)

    assertThat(threadToLatestRead[threadId]).isEqualTo(5001)
  }

  @Test
  fun readSyncMarksOlderMessagesReadWhenReceivedLongAfterBeingSent() {
    insertIncoming(sentTime = 1000, receivedTime = 5000)
    insertIncoming(sentTime = 1001, receivedTime = 5001)
    insertIncoming(sentTime = 1002, receivedTime = 5002)

    assertThat(messages.getUnreadCount(threadId)).isEqualTo(3)

    // A linked device only tells us about the newest message it read. Everything older in the thread is implied.
    syncRead(1002)

    assertThat(messages.getUnreadCount(threadId)).isEqualTo(0)
  }

  @Test
  fun readSyncLeavesNewerMessagesUnread() {
    insertIncoming(sentTime = 1000, receivedTime = 5000)
    insertIncoming(sentTime = 1001, receivedTime = 5001)
    insertIncoming(sentTime = 1002, receivedTime = 5002)

    syncRead(1001)

    assertThat(messages.getUnreadCount(threadId)).isEqualTo(1)
    assertThat(messages.getOldestUnread(threadId)?.dateReceived).isEqualTo(5002)
  }

  // region helpers

  private fun insertIncoming(sentTime: Long, receivedTime: Long): Long {
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = senderId,
      sentTimeMillis = sentTime,
      serverTimeMillis = sentTime,
      receivedTimeMillis = receivedTime,
      body = "msg $sentTime"
    )
    return messages.insertMessageInbox(message, threadId).get().messageId
  }

  private fun syncRead(sentTime: Long) {
    val threadToLatestRead: MutableMap<Long, Long> = mutableMapOf()
    messages.setTimestampReadFromSyncMessage(listOf(readSync(sentTime)), sentTime, threadToLatestRead)
    SignalDatabase.threads.setReadSince(threadToLatestRead)
  }

  private fun readSync(sentTime: Long): SyncMessage.Read {
    return SyncMessage.Read(
      senderAci = Recipient.resolved(senderId).requireAci().toString(),
      timestamp = sentTime
    )
  }

  // endregion
}
