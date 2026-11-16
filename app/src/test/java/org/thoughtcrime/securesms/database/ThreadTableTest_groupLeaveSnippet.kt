/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ThreadTableTest_groupLeaveSnippet {

  companion object {
    private const val GV2_LEAVE = MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT or MessageTypes.GROUP_V2_LEAVE_BITS
  }

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var alice: RecipientId
  private lateinit var group: RecipientTestRule.TestGroupInfo
  private var threadId: Long = -1

  @Before
  fun setUp() {
    alice = recipients.createRecipient("Alice Android", profileSharing = false)
    group = recipients.createGroup(alice)
    SignalDatabase.recipients.setProfileSharing(group.recipientId, false)
    threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(group.recipientId))
    insertIncomingGroupMessage(sentTimeMillis = 100)
  }

  @Test
  fun `given unaccepted group with only incoming messages, when updated, then snippet is a message request`() {
    SignalDatabase.threads.update(threadId, false)

    val thread = SignalDatabase.threads.getThreadRecord(threadId)!!
    assertFalse(thread.snippetExtras!!.isMessageRequestAccepted)
  }

  @Test
  fun `given unaccepted group, when self leaves, then snippet is the leave and not a message request`() {
    val leaveId = insertGroupLeave(MessageTypes.BASE_SENT_TYPE, sentTimeMillis = 200)

    SignalDatabase.threads.update(threadId, false)

    val thread = SignalDatabase.threads.getThreadRecord(threadId)!!
    assertEquals(leaveId, thread.snippetMessageId)
    assertTrue(thread.snippetExtras!!.isMessageRequestAccepted)
  }

  @Test
  fun `given unaccepted group, when another member leaves, then snippet is unchanged and still a message request`() {
    SignalDatabase.threads.update(threadId, false)
    val messageId = SignalDatabase.threads.getThreadRecord(threadId)!!.snippetMessageId

    insertGroupLeave(MessageTypes.BASE_INBOX_TYPE, sentTimeMillis = 200)
    SignalDatabase.threads.update(threadId, false)

    val thread = SignalDatabase.threads.getThreadRecord(threadId)!!
    assertEquals(messageId, thread.snippetMessageId)
    assertFalse(thread.snippetExtras!!.isMessageRequestAccepted)
  }

  @Test
  fun `given blocked unaccepted group, when updated, then snippet is not a message request`() {
    SignalDatabase.recipients.setBlocked(group.recipientId, true, System.currentTimeMillis())
    Recipient.live(group.recipientId).refresh()

    SignalDatabase.threads.update(threadId, false)

    val thread = SignalDatabase.threads.getThreadRecord(threadId)!!
    assertTrue(thread.snippetExtras!!.isMessageRequestAccepted)
  }

  private fun insertIncomingGroupMessage(sentTimeMillis: Long) {
    SignalDatabase.messages.insertMessageInbox(
      IncomingMessage(
        type = MessageType.NORMAL,
        from = alice,
        groupId = group.groupId,
        body = "hi",
        sentTimeMillis = sentTimeMillis,
        receivedTimeMillis = sentTimeMillis,
        serverTimeMillis = sentTimeMillis,
        isUnidentified = true
      ),
      threadId
    )
  }

  private fun insertGroupLeave(baseType: Long, sentTimeMillis: Long): Long {
    return TestMms.insert(
      db = recipients.signalDatabase.writeableDatabase,
      recipient = Recipient.resolved(group.recipientId),
      recipientId = group.recipientId,
      sentTimeMillis = sentTimeMillis,
      receivedTimestampMillis = sentTimeMillis,
      type = baseType or GV2_LEAVE,
      threadId = threadId
    )
  }
}
