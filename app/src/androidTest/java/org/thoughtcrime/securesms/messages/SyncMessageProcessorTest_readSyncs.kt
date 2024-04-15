/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobs.ThreadUpdateJob
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.GroupTestingUtils
import org.thoughtcrime.securesms.testing.MessageContentFuzzer
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.assertIs
import java.util.UUID

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class SyncMessageProcessorTest_readSyncs {

  @get:Rule
  val harness = SignalActivityRule(createGroup = true)

  private lateinit var alice: RecipientId
  private lateinit var bob: RecipientId
  private lateinit var group: GroupTestingUtils.TestGroupInfo
  private lateinit var processor: MessageContentProcessor

  @Before
  fun setUp() {
    alice = harness.others[0]
    bob = harness.others[1]
    group = harness.group!!

    processor = MessageContentProcessor(harness.context)

    val threadIdSlot = slot<Long>()
    mockkStatic(ThreadUpdateJob::class)
    every { ThreadUpdateJob.enqueue(capture(threadIdSlot)) } answers {
      SignalDatabase.threads.update(threadIdSlot.captured, false)
    }
  }

  @After
  fun tearDown() {
    unmockkStatic(ThreadUpdateJob::class)
  }

  @Test
  fun handleSynchronizeReadMessage() {
    val messageHelper = MessageHelper()

    val message1Timestamp = messageHelper.incomingText().timestamp
    val message2Timestamp = messageHelper.incomingText().timestamp

    val threadId = SignalDatabase.threads.getThreadIdFor(alice)!!
    var threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 2

    messageHelper.syncReadMessage(alice to message1Timestamp, alice to message2Timestamp)

    threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 0
  }

  @Test
  fun handleSynchronizeReadMessageMissingTimestamp() {
    val messageHelper = MessageHelper()

    messageHelper.incomingText().timestamp
    val message2Timestamp = messageHelper.incomingText().timestamp

    val threadId = SignalDatabase.threads.getThreadIdFor(alice)!!
    var threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 2

    messageHelper.syncReadMessage(alice to message2Timestamp)

    threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 0
  }

  @Test
  fun handleSynchronizeReadWithEdits() {
    val messageHelper = MessageHelper()

    val message1Timestamp = messageHelper.incomingText().timestamp
    messageHelper.syncReadMessage(alice to message1Timestamp)

    val editMessage1Timestamp1 = messageHelper.incomingEditText(message1Timestamp).timestamp
    val editMessage1Timestamp2 = messageHelper.incomingEditText(editMessage1Timestamp1).timestamp

    val message2Timestamp = messageHelper.incomingMedia().timestamp

    val threadId = SignalDatabase.threads.getThreadIdFor(alice)!!
    var threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 2

    messageHelper.syncReadMessage(alice to message2Timestamp, alice to editMessage1Timestamp1, alice to editMessage1Timestamp2)

    threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 0
  }

  @Test
  fun handleSynchronizeReadWithEditsInGroup() {
    val messageHelper = MessageHelper()

    val message1Timestamp = messageHelper.incomingText(sender = alice, destination = group.recipientId).timestamp

    messageHelper.syncReadMessage(alice to message1Timestamp)

    val editMessage1Timestamp1 = messageHelper.incomingEditText(targetTimestamp = message1Timestamp, sender = alice, destination = group.recipientId).timestamp
    val editMessage1Timestamp2 = messageHelper.incomingEditText(targetTimestamp = editMessage1Timestamp1, sender = alice, destination = group.recipientId).timestamp

    val message2Timestamp = messageHelper.incomingMedia(sender = bob, destination = group.recipientId).timestamp

    val threadId = SignalDatabase.threads.getThreadIdFor(group.recipientId)!!
    var threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 2

    messageHelper.syncReadMessage(bob to message2Timestamp, alice to editMessage1Timestamp1, alice to editMessage1Timestamp2)

    threadRecord = SignalDatabase.threads.getThreadRecord(threadId)!!
    threadRecord.unreadCount assertIs 0
  }

  private inner class MessageHelper(var startTime: Long = System.currentTimeMillis()) {

    fun incomingText(sender: RecipientId = alice, destination: RecipientId = harness.self.id): MessageData {
      startTime += 1000

      val messageData = MessageData(timestamp = startTime)

      processor.process(
        envelope = MessageContentFuzzer.envelope(messageData.timestamp, serverGuid = messageData.serverGuid),
        content = MessageContentFuzzer.fuzzTextMessage(
          sentTimestamp = messageData.timestamp,
          groupContextV2 = if (destination == group.recipientId) group.groupV2Context else null
        ),
        metadata = MessageContentFuzzer.envelopeMetadata(
          source = sender,
          destination = harness.self.id,
          groupId = if (destination == group.recipientId) group.groupId else null
        ),
        serverDeliveredTimestamp = messageData.timestamp + 10
      )

      return messageData
    }

    fun incomingMedia(sender: RecipientId = alice, destination: RecipientId = harness.self.id): MessageData {
      startTime += 1000

      val messageData = MessageData(timestamp = startTime)

      processor.process(
        envelope = MessageContentFuzzer.envelope(messageData.timestamp, serverGuid = messageData.serverGuid),
        content = MessageContentFuzzer.fuzzStickerMediaMessage(
          sentTimestamp = messageData.timestamp,
          groupContextV2 = if (destination == group.recipientId) group.groupV2Context else null
        ),
        metadata = MessageContentFuzzer.envelopeMetadata(
          source = sender,
          destination = harness.self.id,
          groupId = if (destination == group.recipientId) group.groupId else null
        ),
        serverDeliveredTimestamp = messageData.timestamp + 10
      )

      return messageData
    }

    fun incomingEditText(targetTimestamp: Long = System.currentTimeMillis(), sender: RecipientId = alice, destination: RecipientId = harness.self.id): MessageData {
      startTime += 1000

      val messageData = MessageData(timestamp = startTime)

      processor.process(
        envelope = MessageContentFuzzer.envelope(messageData.timestamp, serverGuid = messageData.serverGuid),
        content = MessageContentFuzzer.editTextMessage(
          targetTimestamp = targetTimestamp,
          editedDataMessage = MessageContentFuzzer.fuzzTextMessage(
            sentTimestamp = messageData.timestamp,
            groupContextV2 = if (destination == group.recipientId) group.groupV2Context else null
          ).dataMessage!!
        ),
        metadata = MessageContentFuzzer.envelopeMetadata(
          source = sender,
          destination = harness.self.id,
          groupId = if (destination == group.recipientId) group.groupId else null
        ),
        serverDeliveredTimestamp = messageData.timestamp + 10
      )

      return messageData
    }

    fun syncReadMessage(vararg reads: Pair<RecipientId, Long>): MessageData {
      startTime += 1000
      val messageData = MessageData(timestamp = startTime)

      processor.process(
        envelope = MessageContentFuzzer.envelope(messageData.timestamp, serverGuid = messageData.serverGuid),
        content = MessageContentFuzzer.syncReadsMessage(reads.toList()),
        metadata = MessageContentFuzzer.envelopeMetadata(harness.self.id, harness.self.id, sourceDeviceId = 2),
        serverDeliveredTimestamp = messageData.timestamp + 10
      )

      return messageData
    }
  }

  private data class MessageData(val serverGuid: UUID = UUID.randomUUID(), val timestamp: Long)
}
