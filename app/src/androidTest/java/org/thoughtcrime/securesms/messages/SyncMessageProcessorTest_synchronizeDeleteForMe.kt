/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.hamcrest.Matchers.greaterThan
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.SessionSwitchoverEvent
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.MessageContentFuzzer.DeleteForMeSync
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.assert
import org.thoughtcrime.securesms.testing.assertIs
import org.thoughtcrime.securesms.testing.assertIsNotNull
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.IdentityUtil

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class SyncMessageProcessorTest_synchronizeDeleteForMe {

  @get:Rule
  val harness = SignalActivityRule(createGroup = true)

  private lateinit var messageHelper: MessageHelper

  @Before
  fun setUp() {
    messageHelper = MessageHelper(harness)

    mockkStatic(FeatureFlags::class)
    every { FeatureFlags.deleteSyncEnabled() } returns true
  }

  @After
  fun tearDown() {
    messageHelper.tearDown()

    unmockkStatic(FeatureFlags::class)
  }

  @Test
  fun singleMessageDelete() {
    // GIVEN
    val message1Timestamp = messageHelper.incomingText().timestamp
    messageHelper.incomingText()

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    var messageCount = SignalDatabase.messages.getMessageCountForThread(threadId)
    messageCount assertIs 2

    // WHEN
    messageHelper.syncDeleteForMeMessage(
      DeleteForMeSync(conversationId = messageHelper.alice, messageHelper.alice to message1Timestamp)
    )

    // THEN
    messageCount = SignalDatabase.messages.getMessageCountForThread(threadId)
    messageCount assertIs 1
  }

  @Test
  fun singleOutgoingMessageDelete() {
    // GIVEN
    val message1Timestamp = messageHelper.outgoingText().timestamp
    messageHelper.incomingText()

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    var messageCount = SignalDatabase.messages.getMessageCountForThread(threadId)
    messageCount assertIs 2

    // WHEN
    messageHelper.syncDeleteForMeMessage(
      DeleteForMeSync(conversationId = messageHelper.alice, harness.self.id to message1Timestamp)
    )

    // THEN
    messageCount = SignalDatabase.messages.getMessageCountForThread(threadId)
    messageCount assertIs 1
  }

  @Test
  fun singleGroupMessageDelete() {
    // GIVEN
    val message1Timestamp = messageHelper.incomingText(sender = messageHelper.alice, destination = messageHelper.group.recipientId).timestamp
    messageHelper.incomingText(sender = messageHelper.alice, destination = messageHelper.group.recipientId)
    messageHelper.incomingText(sender = messageHelper.bob, destination = messageHelper.group.recipientId)

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.group.recipientId)!!
    var messageCount = SignalDatabase.messages.getMessageCountForThread(threadId)
    messageCount assertIs 3

    // WHEN
    messageHelper.syncDeleteForMeMessage(
      DeleteForMeSync(conversationId = messageHelper.group.recipientId, messageHelper.alice to message1Timestamp)
    )

    // THEN
    messageCount = SignalDatabase.messages.getMessageCountForThread(threadId)
    messageCount assertIs 2
  }

  @Test
  fun multipleGroupMessageDelete() {
    // GIVEN
    val message1Timestamp = messageHelper.incomingText(sender = messageHelper.alice, destination = messageHelper.group.recipientId).timestamp
    messageHelper.incomingText(sender = messageHelper.alice, destination = messageHelper.group.recipientId)
    val message3Timestamp = messageHelper.incomingText(sender = messageHelper.bob, destination = messageHelper.group.recipientId).timestamp

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.group.recipientId)!!
    var messageCount = SignalDatabase.messages.getMessageCountForThread(threadId)
    messageCount assertIs 3

    // WHEN
    messageHelper.syncDeleteForMeMessage(
      DeleteForMeSync(conversationId = messageHelper.group.recipientId, messageHelper.alice to message1Timestamp, messageHelper.bob to message3Timestamp)
    )

    // THEN
    messageCount = SignalDatabase.messages.getMessageCountForThread(threadId)
    messageCount assertIs 1
  }

  @Test
  fun allMessagesDelete() {
    // GIVEN
    val message1Timestamp = messageHelper.incomingText().timestamp
    val message2Timestamp = messageHelper.incomingText().timestamp

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    var messageCount = SignalDatabase.messages.getMessageCountForThread(threadId)
    messageCount assertIs 2

    // WHEN
    messageHelper.syncDeleteForMeMessage(
      DeleteForMeSync(conversationId = messageHelper.alice, messageHelper.alice to message1Timestamp, messageHelper.alice to message2Timestamp)
    )

    // THEN
    messageCount = SignalDatabase.messages.getMessageCountForThread(threadId)
    messageCount assertIs 0

    val threadRecord = SignalDatabase.threads.getThreadRecord(threadId)
    threadRecord assertIs null
  }

  @Test
  fun earlyMessagesDelete() {
    // GIVEN
    messageHelper.incomingText().timestamp

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    var messageCount = SignalDatabase.messages.getMessageCountForThread(threadId)
    messageCount assertIs 1

    // WHEN
    val nextTextMessageTimestamp = messageHelper.nextStartTime(2)
    messageHelper.syncDeleteForMeMessage(
      DeleteForMeSync(conversationId = messageHelper.alice, messageHelper.alice to nextTextMessageTimestamp)
    )
    messageHelper.incomingText()

    // THEN
    messageCount = SignalDatabase.messages.getMessageCountForThread(threadId)
    messageCount assertIs 1
  }

  @Test
  fun multipleConversationMessagesDelete() {
    // GIVEN
    messageHelper.incomingText(sender = messageHelper.alice)
    val aliceMessage2 = messageHelper.incomingText(sender = messageHelper.alice).timestamp

    messageHelper.incomingText(sender = messageHelper.bob)
    val bobMessage2 = messageHelper.incomingText(sender = messageHelper.bob).timestamp

    val aliceThreadId = SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    var aliceMessageCount = SignalDatabase.messages.getMessageCountForThread(aliceThreadId)
    aliceMessageCount assertIs 2

    val bobThreadId = SignalDatabase.threads.getThreadIdFor(messageHelper.bob)!!
    var bobMessageCount = SignalDatabase.messages.getMessageCountForThread(bobThreadId)
    bobMessageCount assertIs 2

    // WHEN
    messageHelper.syncDeleteForMeMessage(
      DeleteForMeSync(conversationId = messageHelper.alice, messageHelper.alice to aliceMessage2),
      DeleteForMeSync(conversationId = messageHelper.bob, messageHelper.bob to bobMessage2)
    )

    // THEN
    aliceMessageCount = SignalDatabase.messages.getMessageCountForThread(aliceThreadId)
    aliceMessageCount assertIs 1

    bobMessageCount = SignalDatabase.messages.getMessageCountForThread(bobThreadId)
    bobMessageCount assertIs 1
  }

  @Test
  fun singleConversationDelete() {
    // GIVEN
    val messages = mutableListOf<MessageTable.SyncMessageId>()

    for (i in 0 until 10) {
      messages += MessageTable.SyncMessageId(messageHelper.alice, messageHelper.incomingText().timestamp)
      messages += MessageTable.SyncMessageId(harness.self.id, messageHelper.outgoingText().timestamp)
    }

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    SignalDatabase.messages.getMessageCountForThread(threadId) assertIs 20

    // WHEN
    messageHelper.syncDeleteForMeConversation(
      DeleteForMeSync(
        conversationId = messageHelper.alice,
        messages = messages.takeLast(5).map { it.recipientId to it.timetamp },
        isFullDelete = true
      )
    )

    // THEN
    SignalDatabase.messages.getMessageCountForThread(threadId) assertIs 0
    SignalDatabase.threads.getThreadRecord(threadId) assertIs null
  }

  @Test
  fun singleConversationNoRecentsFoundDelete() {
    // GIVEN
    val messages = mutableListOf<MessageTable.SyncMessageId>()

    for (i in 0 until 10) {
      messages += MessageTable.SyncMessageId(messageHelper.alice, messageHelper.incomingText().timestamp)
      messages += MessageTable.SyncMessageId(harness.self.id, messageHelper.outgoingText().timestamp)
    }

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    SignalDatabase.messages.getMessageCountForThread(threadId) assertIs 20

    // WHEN
    val randomFutureMessages = (1..5).map {
      messageHelper.alice to messageHelper.nextStartTime(it)
    }

    messageHelper.syncDeleteForMeConversation(
      DeleteForMeSync(conversationId = messageHelper.alice, randomFutureMessages, true)
    )

    // THEN
    SignalDatabase.messages.getMessageCountForThread(threadId) assertIs 20
    SignalDatabase.threads.getThreadRecord(threadId).assertIsNotNull()

    harness.inMemoryLogger.flush()
    harness.inMemoryLogger.entries().filter { it.message?.contains("Unable to find most recent received at timestamp") == true }.size assertIs 1
  }

  @Test
  fun localOnlyRemainingAfterConversationDeleteWithFullDelete() {
    // GIVEN
    val messages = mutableListOf<MessageTable.SyncMessageId>()

    for (i in 0 until 10) {
      messages += MessageTable.SyncMessageId(messageHelper.alice, messageHelper.incomingText().timestamp)
      messages += MessageTable.SyncMessageId(harness.self.id, messageHelper.outgoingText().timestamp)
    }

    val alice = Recipient.resolved(messageHelper.alice)
    IdentityUtil.markIdentityVerified(harness.context, alice, true, true)
    SignalDatabase.messages.insertProfileNameChangeMessages(alice, "new name", "previous name")
    SignalDatabase.calls.insertOneToOneCall(1, System.currentTimeMillis(), alice.id, CallTable.Type.AUDIO_CALL, CallTable.Direction.OUTGOING, CallTable.Event.ACCEPTED)

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    SignalDatabase.messages.getMessageCountForThread(threadId) assertIs 23

    // WHEN
    messageHelper.syncDeleteForMeConversation(
      DeleteForMeSync(
        conversationId = messageHelper.alice,
        messages = messages.takeLast(5).map { it.recipientId to it.timetamp },
        isFullDelete = true
      )
    )

    // THEN
    SignalDatabase.messages.getMessageCountForThread(threadId) assertIs 0
    SignalDatabase.threads.getThreadRecord(threadId) assertIs null
  }

  @Test
  fun localOnlyRemainingAfterConversationDeleteWithoutFullDelete() {
    // GIVEN
    val messages = mutableListOf<MessageTable.SyncMessageId>()

    for (i in 0 until 10) {
      messages += MessageTable.SyncMessageId(messageHelper.alice, messageHelper.incomingText().timestamp)
      messages += MessageTable.SyncMessageId(harness.self.id, messageHelper.outgoingText().timestamp)
    }

    val alice = Recipient.resolved(messageHelper.alice)
    IdentityUtil.markIdentityVerified(harness.context, alice, true, true)
    SignalDatabase.messages.insertProfileNameChangeMessages(alice, "new name", "previous name")
    SignalDatabase.calls.insertOneToOneCall(1, System.currentTimeMillis(), alice.id, CallTable.Type.AUDIO_CALL, CallTable.Direction.OUTGOING, CallTable.Event.ACCEPTED)

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    SignalDatabase.messages.getMessageCountForThread(threadId) assertIs 23

    // WHEN
    messageHelper.syncDeleteForMeConversation(
      DeleteForMeSync(
        conversationId = messageHelper.alice,
        messages = messages.takeLast(5).map { it.recipientId to it.timetamp },
        isFullDelete = false
      )
    )

    // THEN
    SignalDatabase.messages.getMessageCountForThread(threadId) assertIs 3
    SignalDatabase.threads.getThreadRecord(threadId).assertIsNotNull()
  }

  @Test
  fun groupConversationDelete() {
    // GIVEN
    val messages = mutableListOf<MessageTable.SyncMessageId>()

    for (i in 0 until 50) {
      messages += when (i % 3) {
        1 -> MessageTable.SyncMessageId(messageHelper.alice, messageHelper.incomingText(sender = messageHelper.alice, destination = messageHelper.group.recipientId).timestamp)
        2 -> MessageTable.SyncMessageId(messageHelper.bob, messageHelper.incomingText(sender = messageHelper.bob, destination = messageHelper.group.recipientId).timestamp)
        else -> MessageTable.SyncMessageId(harness.self.id, messageHelper.outgoingText(messageHelper.group.recipientId).timestamp)
      }
    }

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.group.recipientId)!!

    // WHEN
    messageHelper.syncDeleteForMeConversation(
      DeleteForMeSync(
        conversationId = messageHelper.group.recipientId,
        messages = messages.takeLast(5).map { it.recipientId to it.timetamp },
        isFullDelete = true
      )
    )

    // THEN
    SignalDatabase.messages.getMessageCountForThread(threadId) assertIs 0
    SignalDatabase.threads.getThreadRecord(threadId) assertIs null
  }

  @Test
  fun multipleConversationDelete() {
    // GIVEN
    val allMessages = mapOf<RecipientId, MutableList<MessageTable.SyncMessageId>>(
      messageHelper.alice to mutableListOf(),
      messageHelper.bob to mutableListOf()
    )

    allMessages.forEach { (conversation, messages) ->
      for (i in 0 until 10) {
        messages += MessageTable.SyncMessageId(conversation, messageHelper.incomingText(sender = conversation).timestamp)
        messages += MessageTable.SyncMessageId(harness.self.id, messageHelper.outgoingText(conversationId = conversation).timestamp)
      }
    }

    val threadIds = allMessages.keys.map { SignalDatabase.threads.getThreadIdFor(it)!! }
    threadIds.forEach { SignalDatabase.messages.getMessageCountForThread(it) assertIs 20 }

    // WHEN
    messageHelper.syncDeleteForMeConversation(
      DeleteForMeSync(conversationId = messageHelper.alice, allMessages[messageHelper.alice]!!.takeLast(5).map { it.recipientId to it.timetamp }, true),
      DeleteForMeSync(conversationId = messageHelper.bob, allMessages[messageHelper.bob]!!.takeLast(5).map { it.recipientId to it.timetamp }, true)
    )

    // THEN
    threadIds.forEach {
      SignalDatabase.messages.getMessageCountForThread(it) assertIs 0
      SignalDatabase.threads.getThreadRecord(it) assertIs null
    }
  }

  @Test
  fun singleLocalOnlyConversation() {
    // GIVEN
    val alice = Recipient.resolved(messageHelper.alice)

    // Insert placeholder message to prevent early thread update deletes
    val oneToOnePlaceHolderMessage = messageHelper.outgoingText().messageId

    val aliceThreadId = SignalDatabase.threads.getOrCreateThreadIdFor(messageHelper.alice, isGroup = false)

    IdentityUtil.markIdentityVerified(harness.context, alice, true, false)
    SignalDatabase.calls.insertOneToOneCall(1, System.currentTimeMillis(), alice.id, CallTable.Type.AUDIO_CALL, CallTable.Direction.OUTGOING, CallTable.Event.ACCEPTED)
    SignalDatabase.messages.insertProfileNameChangeMessages(alice, "new name", "previous name")
    SignalDatabase.messages.markAsSentFailed(messageHelper.outgoingText().messageId)

    // Cleanup and confirm setup
    SignalDatabase.messages.deleteMessage(messageId = oneToOnePlaceHolderMessage, threadId = aliceThreadId, notify = false, updateThread = false)
    SignalDatabase.messages.getMessageCountForThread(aliceThreadId) assert greaterThan(0)

    // WHEN
    messageHelper.syncDeleteForMeLocalOnlyConversation(messageHelper.alice)

    // THEN
    SignalDatabase.messages.getMessageCountForThread(aliceThreadId) assertIs 0
    SignalDatabase.threads.getThreadRecord(aliceThreadId) assertIs null
  }

  @Test
  fun multipleLocalOnlyConversation() {
    // GIVEN
    val alice = Recipient.resolved(messageHelper.alice)

    // Insert placeholder messages in group and alice thread to prevent early thread update deletes
    val groupPlaceholderMessage = messageHelper.outgoingText(conversationId = messageHelper.group.recipientId).messageId
    val oneToOnePlaceHolderMessage = messageHelper.outgoingText().messageId

    val aliceThreadId = SignalDatabase.threads.getOrCreateThreadIdFor(messageHelper.alice, isGroup = false)
    val groupThreadId = SignalDatabase.threads.getOrCreateThreadIdFor(messageHelper.group.recipientId, isGroup = true)

    // Identity changes
    IdentityUtil.markIdentityVerified(harness.context, alice, true, true)
    IdentityUtil.markIdentityVerified(harness.context, alice, false, true)
    IdentityUtil.markIdentityVerified(harness.context, alice, true, false)
    IdentityUtil.markIdentityVerified(harness.context, alice, false, false)

    IdentityUtil.markIdentityUpdate(harness.context, alice.id)

    // Calls
    SignalDatabase.calls.insertOneToOneCall(1, System.currentTimeMillis(), alice.id, CallTable.Type.AUDIO_CALL, CallTable.Direction.OUTGOING, CallTable.Event.ACCEPTED)
    SignalDatabase.calls.insertOneToOneCall(2, System.currentTimeMillis(), alice.id, CallTable.Type.VIDEO_CALL, CallTable.Direction.INCOMING, CallTable.Event.MISSED)
    SignalDatabase.calls.insertOneToOneCall(3, System.currentTimeMillis(), alice.id, CallTable.Type.AUDIO_CALL, CallTable.Direction.INCOMING, CallTable.Event.MISSED_NOTIFICATION_PROFILE)

    SignalDatabase.calls.insertAcceptedGroupCall(4, messageHelper.group.recipientId, CallTable.Direction.INCOMING, System.currentTimeMillis())
    SignalDatabase.calls.insertDeclinedGroupCall(5, messageHelper.group.recipientId, System.currentTimeMillis())

    // Detected changes
    SignalDatabase.messages.insertProfileNameChangeMessages(alice, "new name", "previous name")
    SignalDatabase.messages.insertLearnedProfileNameChangeMessage(alice, null, "username.42")
    SignalDatabase.messages.insertNumberChangeMessages(alice.id)
    SignalDatabase.messages.insertSmsExportMessage(alice.id, SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!)
    SignalDatabase.messages.insertSessionSwitchoverEvent(alice.id, aliceThreadId, SessionSwitchoverEvent())

    // Sent failed
    SignalDatabase.messages.markAsSending(messageHelper.outgoingText().messageId)
    SignalDatabase.messages.markAsSentFailed(messageHelper.outgoingText().messageId)
    messageHelper.outgoingText().let {
      SignalDatabase.messages.markAsSending(it.messageId)
      SignalDatabase.messages.markAsRateLimited(it.messageId)
    }

    // Group change
    messageHelper.outgoingGroupChange()

    // Cleanup and confirm setup
    SignalDatabase.messages.deleteMessage(messageId = oneToOnePlaceHolderMessage, threadId = aliceThreadId, notify = false, updateThread = false)
    SignalDatabase.messages.deleteMessage(messageId = groupPlaceholderMessage, threadId = aliceThreadId, notify = false, updateThread = false)

    SignalDatabase.messages.getMessageCountForThread(aliceThreadId) assertIs 16
    SignalDatabase.messages.getMessageCountForThread(groupThreadId) assertIs 10

    // WHEN
    messageHelper.syncDeleteForMeLocalOnlyConversation(messageHelper.alice, messageHelper.group.recipientId)

    // THEN
    SignalDatabase.messages.getMessageCountForThread(aliceThreadId) assertIs 0
    SignalDatabase.threads.getThreadRecord(aliceThreadId) assertIs null

    SignalDatabase.messages.getMessageCountForThread(groupThreadId) assertIs 0
    SignalDatabase.threads.getThreadRecord(groupThreadId) assertIs null
  }

  @Test
  fun singleLocalOnlyConversationHasAddressable() {
    // GIVEN
    val messages = mutableListOf<MessageTable.SyncMessageId>()

    for (i in 0 until 10) {
      messages += MessageTable.SyncMessageId(messageHelper.alice, messageHelper.incomingText().timestamp)
      messages += MessageTable.SyncMessageId(harness.self.id, messageHelper.outgoingText().timestamp)
    }

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    SignalDatabase.messages.getMessageCountForThread(threadId) assertIs 20

    // WHEN
    messageHelper.syncDeleteForMeLocalOnlyConversation(messageHelper.alice)

    // THEN
    SignalDatabase.messages.getMessageCountForThread(threadId) assertIs 20
    SignalDatabase.threads.getThreadRecord(threadId).assertIsNotNull()

    harness.inMemoryLogger.flush()
    harness.inMemoryLogger.entries().filter { it.message?.contains("Thread is not local only") == true }.size assertIs 1
  }
}
