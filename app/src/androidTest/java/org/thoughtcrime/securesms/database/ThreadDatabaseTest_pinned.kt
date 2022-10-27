package org.thoughtcrime.securesms.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.core.util.CursorUtil
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.SignalDatabaseRule
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.UUID

@Suppress("ClassName")
class ThreadDatabaseTest_pinned {

  @Rule
  @JvmField
  val databaseRule = SignalDatabaseRule()

  private lateinit var recipient: Recipient

  @Before
  fun setUp() {
    recipient = Recipient.resolved(SignalDatabase.recipients.getOrInsertFromServiceId(ServiceId.from(UUID.randomUUID())))
  }

  @Test
  fun givenAPinnedThread_whenIDeleteTheLastMessage_thenIDoNotDeleteOrUnpinTheThread() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    val messageId = MmsHelper.insert(recipient = recipient, threadId = threadId)
    SignalDatabase.threads.pinConversations(listOf(threadId))

    // WHEN
    SignalDatabase.mms.deleteMessage(messageId)

    // THEN
    val pinned = SignalDatabase.threads.pinnedThreadIds
    assertTrue(threadId in pinned)
  }

  @Test
  fun givenAPinnedThread_whenIDeleteTheLastMessage_thenIExpectTheThreadInUnarchivedCount() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    val messageId = MmsHelper.insert(recipient = recipient, threadId = threadId)
    SignalDatabase.threads.pinConversations(listOf(threadId))

    // WHEN
    SignalDatabase.mms.deleteMessage(messageId)

    // THEN
    val unarchivedCount = SignalDatabase.threads.unarchivedConversationListCount
    assertEquals(1, unarchivedCount)
  }

  @Test
  fun givenAPinnedThread_whenIDeleteTheLastMessage_thenIExpectPinnedThreadInUnarchivedList() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    val messageId = MmsHelper.insert(recipient = recipient, threadId = threadId)
    SignalDatabase.threads.pinConversations(listOf(threadId))

    // WHEN
    SignalDatabase.mms.deleteMessage(messageId)

    // THEN
    SignalDatabase.threads.getUnarchivedConversationList(true, 0, 1).use {
      it.moveToFirst()
      assertEquals(threadId, CursorUtil.requireLong(it, ThreadDatabase.ID))
    }
  }
}
