/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import androidx.core.content.contentValuesOf
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import kotlin.time.Duration.Companion.days

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class CollapsingMessagesTests {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var message: MessageTable
  private lateinit var thread: ThreadTable

  private lateinit var alice: RecipientId
  private var aliceThread: Long = 0

  private lateinit var bob: RecipientId

  @Before
  fun setUp() {
    message = SignalDatabase.messages
    thread = SignalDatabase.threads

    alice = recipients.createRecipient("Alice Android")
    aliceThread = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(alice))
    bob = recipients.createRecipient("Bob Android")
  }

  @Test
  fun givenCollapsibleMessage_whenIInsert_thenItBecomesHead() {
    val messageId = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 1000L, false).messageId

    val msg = message.getMessageRecord(messageId)
    assertEquals(CollapsedState.HEAD_COLLAPSED, msg.collapsedState)
    assertEquals(messageId, msg.collapsedHeadId)
  }

  @Test
  fun givenSameCollapsibleTypes_whenIInsert_thenAllCollapseUnderHead() {
    val messageId1 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 1000L, false).messageId
    val messageId2 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 2000L, false).messageId
    val messageId3 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 3000L, false).messageId

    val msg1 = message.getMessageRecord(messageId1)
    val msg2 = message.getMessageRecord(messageId2)
    val msg3 = message.getMessageRecord(messageId3)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msg1.collapsedState)
    assertEquals(messageId1, msg1.collapsedHeadId)

    assertEquals(CollapsedState.PENDING_COLLAPSED, msg2.collapsedState)
    assertEquals(messageId1, msg2.collapsedHeadId)

    assertEquals(CollapsedState.PENDING_COLLAPSED, msg3.collapsedState)
    assertEquals(messageId1, msg3.collapsedHeadId)
  }

  @Test
  fun givenDifferentCollapsedTypes_whenIInsert_thenNoCollapsing() {
    val messageId1 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 1000L, false).messageId
    val messageId2 = recipients.insertOutgoingMessage(OutgoingMessage.identityVerifiedMessage(Recipient.resolved(alice), 2000L), aliceThread)

    val msg1 = message.getMessageRecord(messageId1)
    val msg2 = message.getMessageRecord(messageId2)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msg1.collapsedState)
    assertEquals(messageId1, msg1.collapsedHeadId)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msg2.collapsedState)
    assertEquals(messageId2, msg2.collapsedHeadId)
  }

  @Test
  fun givenNonCollapsibleTypes_whenIInsert_thenNoCollapsing() {
    val messageId = recipients.insertOutgoingMessage(alice, sentTimeMillis = 1000L)

    val msg = message.getMessageRecord(messageId)
    assertEquals(CollapsedState.NONE, msg.collapsedState)
    assertEquals(0, msg.collapsedHeadId)
  }

  @Test
  fun givenMessagesOnDifferentDays_whenIInsert_thenNoCollapsing() {
    val messageId1 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 1000L, false).messageId

    message.writableDatabase.update(
      MessageTable.TABLE_NAME,
      contentValuesOf(MessageTable.DATE_RECEIVED to (System.currentTimeMillis() - 1.days.inWholeMilliseconds)),
      "${MessageTable.ID} = ?",
      arrayOf(messageId1.toString())
    )

    val messageId2 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 2000L, false).messageId

    val msg2 = message.getMessageRecord(messageId2)
    assertEquals(CollapsedState.HEAD_COLLAPSED, msg2.collapsedState)
    assertEquals(messageId2, msg2.collapsedHeadId)
  }

  @Test
  fun givenRegularMessageBetweenCollapsed_whenIInsertCollapsed_thenNoCollapsing() {
    val messageId1 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 1000L, false).messageId
    val messageId2 = recipients.insertOutgoingMessage(alice, sentTimeMillis = 2000L)
    val messageId3 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 3000L, false).messageId

    val msg1 = message.getMessageRecord(messageId1)
    val msg2 = message.getMessageRecord(messageId2)
    val msg3 = message.getMessageRecord(messageId3)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msg1.collapsedState)
    assertEquals(messageId1, msg1.collapsedHeadId)

    assertEquals(CollapsedState.NONE, msg2.collapsedState)
    assertEquals(0, msg2.collapsedHeadId)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msg3.collapsedState)
    assertEquals(messageId3, msg3.collapsedHeadId)
  }

  @Test
  fun givenDifferentThreads_whenIInsertCollapsed_thenNoCollapsing() {
    val messageId1 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 1000L, false).messageId
    val messageId2 = message.insertCallLog(bob, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 2000L, false).messageId

    val msg1 = message.getMessageRecord(messageId1)
    val msg2 = message.getMessageRecord(messageId2)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msg1.collapsedState)
    assertEquals(messageId1, msg1.collapsedHeadId)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msg2.collapsedState)
    assertEquals(messageId2, msg2.collapsedHeadId)
  }

  @Test
  fun givenCollapsedMessages_whenIDeleteFirstMessage_thenNextMessageBecomesHead() {
    val messageId1 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 1000L, false).messageId
    val messageId2 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 2000L, false).messageId
    val messageId3 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 3000L, false).messageId

    message.deleteMessage(messageId1, aliceThread)

    val msg2 = message.getMessageRecord(messageId2)
    val msg3 = message.getMessageRecord(messageId3)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msg2.collapsedState)
    assertEquals(messageId2, msg2.collapsedHeadId)

    assertEquals(CollapsedState.PENDING_COLLAPSED, msg3.collapsedState)
    assertEquals(messageId2, msg3.collapsedHeadId)
  }

  @Test
  fun givenCollapsedMessages_whenIDeleteNonFirstMessage_thenFirstMessageStaysHead() {
    val messageId1 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 1000L, false).messageId
    val messageId2 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 2000L, false).messageId
    val messageId3 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 3000L, false).messageId

    message.deleteMessage(messageId2, aliceThread)

    val msg1 = message.getMessageRecord(messageId1)
    val msg3 = message.getMessageRecord(messageId3)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msg1.collapsedState)
    assertEquals(messageId1, msg1.collapsedHeadId)

    assertEquals(CollapsedState.PENDING_COLLAPSED, msg3.collapsedState)
    assertEquals(messageId1, msg3.collapsedHeadId)
  }

  @Test
  fun givenTwoCollapsingTypes_whenIDeleteHeadOfFirstGroup_thenSecondGroupIsUnchanged() {
    val call1 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 1000L, false)
    val call2 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 2000L, false)

    val recipient = Recipient.resolved(alice)
    val identity1Id = recipients.insertOutgoingMessage(OutgoingMessage.identityVerifiedMessage(recipient, 3000L), call1.threadId)
    val identity2Id = recipients.insertOutgoingMessage(OutgoingMessage.identityVerifiedMessage(recipient, 4000L), call1.threadId)

    message.deleteMessage(call1.messageId, call1.threadId)

    val msgCall2 = message.getMessageRecord(call2.messageId)
    assertEquals(CollapsedState.HEAD_COLLAPSED, msgCall2.collapsedState)
    assertEquals(call2.messageId, msgCall2.collapsedHeadId)

    val msgIdentity1 = message.getMessageRecord(identity1Id)
    val msgIdentity2 = message.getMessageRecord(identity2Id)
    assertEquals(CollapsedState.HEAD_COLLAPSED, msgIdentity1.collapsedState)
    assertEquals(identity1Id, msgIdentity1.collapsedHeadId)
    assertEquals(CollapsedState.PENDING_COLLAPSED, msgIdentity2.collapsedState)
    assertEquals(identity1Id, msgIdentity2.collapsedHeadId)
  }

  @Test
  fun givenPendingCollapsingEvents_whenIMarkSeenAtASpecificTime_thenEverythingBeforeThatTimeIsCollapsed() {
    val call1 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 1000L, false)
    val call2 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 2000L, false)

    message.collapsePendingCollapsibleEvents(aliceThread, System.currentTimeMillis())

    val call3 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 3000L, false)

    val msgCall1 = message.getMessageRecord(call1.messageId)
    val msgCall2 = message.getMessageRecord(call2.messageId)
    val msgCall3 = message.getMessageRecord(call3.messageId)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msgCall1.collapsedState)
    assertEquals(CollapsedState.COLLAPSED, msgCall2.collapsedState)
    assertEquals(CollapsedState.PENDING_COLLAPSED, msgCall3.collapsedState)
  }

  @Test
  fun givenPendingCollapsingEvents_whenIMarkAllAsSeen_thenEverythingIsCollapsed() {
    val call1 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 1000L, false)
    val call2 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 2000L, false)

    message.collapseAllPendingCollapsibleEvents()

    val call3 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 3000L, false)

    val msgCall1 = message.getMessageRecord(call1.messageId)
    val msgCall2 = message.getMessageRecord(call2.messageId)
    val msgCall3 = message.getMessageRecord(call3.messageId)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msgCall1.collapsedState)
    assertEquals(CollapsedState.COLLAPSED, msgCall2.collapsedState)
    assertEquals(CollapsedState.PENDING_COLLAPSED, msgCall3.collapsedState)
  }

  @Test
  fun givenCollapsedEvents_whenITrimTheThreadByCount_thenIExpectANewHead() {
    val call1 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 1000L, false)
    val call2 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 2000L, false)
    val call3 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 3000L, false)
    val call4 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 4000L, false)

    val msgCall1 = message.getMessageRecord(call1.messageId)
    val msgCall2 = message.getMessageRecord(call2.messageId)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msgCall1.collapsedState)
    assertEquals(CollapsedState.PENDING_COLLAPSED, msgCall2.collapsedState)

    thread.trimThread(threadId = aliceThread, syncThreadTrimDeletes = false, length = 2)

    val msgCall3 = message.getMessageRecord(call3.messageId)
    val msgCall4 = message.getMessageRecord(call4.messageId)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msgCall3.collapsedState)
    assertEquals(CollapsedState.PENDING_COLLAPSED, msgCall4.collapsedState)
    assertEquals(call3.messageId, msgCall4.collapsedHeadId)
  }

  @Test
  fun givenCollapsedEvents_whenITrimTheThreadByDate_thenIExpectANewHead() {
    val call1 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 1000L, false)
    val call2 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 2000L, false)
    val trimBeforeDate = System.currentTimeMillis()
    val call3 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 3000L, false)
    val call4 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 4000L, false)

    message.collapsePendingCollapsibleEvents(aliceThread, System.currentTimeMillis())

    val msgCall1 = message.getMessageRecord(call1.messageId)
    val msgCall2 = message.getMessageRecord(call2.messageId)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msgCall1.collapsedState)
    assertEquals(CollapsedState.COLLAPSED, msgCall2.collapsedState)

    thread.trimThread(threadId = aliceThread, syncThreadTrimDeletes = false, trimBeforeDate = trimBeforeDate)

    val msgCall3 = message.getMessageRecord(call3.messageId)
    val msgCall4 = message.getMessageRecord(call4.messageId)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msgCall3.collapsedState)
    assertEquals(CollapsedState.COLLAPSED, msgCall4.collapsedState)
    assertEquals(call3.messageId, msgCall4.collapsedHeadId)
  }

  @Test
  fun givenMaxCollapsedSet_whenIAddAnotherEvent_thenIExpectANewHead() {
    mockkObject(CollapsibleEvents)
    every { CollapsibleEvents.MAX_SIZE } returns 2

    val messageId1 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 1000L, false).messageId
    val messageId2 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 2000L, false).messageId
    val messageId3 = message.insertCallLog(alice, MessageTypes.INCOMING_AUDIO_CALL_TYPE, 3000L, false).messageId

    val msg1 = message.getMessageRecord(messageId1)
    val msg2 = message.getMessageRecord(messageId2)
    val msg3 = message.getMessageRecord(messageId3)

    assertEquals(CollapsedState.HEAD_COLLAPSED, msg1.collapsedState)
    assertEquals(CollapsedState.PENDING_COLLAPSED, msg2.collapsedState)
    assertEquals(CollapsedState.HEAD_COLLAPSED, msg3.collapsedState)
    assertEquals(messageId3, msg3.collapsedHeadId)
    unmockkObject(CollapsibleEvents)
  }
}
