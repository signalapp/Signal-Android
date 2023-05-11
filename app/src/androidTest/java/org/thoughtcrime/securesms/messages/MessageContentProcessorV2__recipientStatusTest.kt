package org.thoughtcrime.securesms.messages

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.database.GroupReceiptTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.toProtoByteString
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.buildWith
import org.thoughtcrime.securesms.testing.GroupTestingUtils
import org.thoughtcrime.securesms.testing.GroupTestingUtils.asMember
import org.thoughtcrime.securesms.testing.MessageContentFuzzer
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.assertIs
import org.thoughtcrime.securesms.util.MessageTableTestUtils
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.DataMessage
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContextV2

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class MessageContentProcessorV2__recipientStatusTest {

  @get:Rule
  val harness = SignalActivityRule()

  private lateinit var processorV2: MessageContentProcessorV2
  private var envelopeTimestamp: Long = 0

  @Before
  fun setup() {
    processorV2 = MessageContentProcessorV2(harness.context)
    envelopeTimestamp = System.currentTimeMillis()
  }

  /**
   * Process sync group sent text transcript with partial send and then process second sync with recipient update
   * flag set to true with the rest of the send completed.
   */
  @Test
  fun syncGroupSentTextMessageWithRecipientUpdateFollowup() {
    val (groupId, masterKey, groupRecipientId) = GroupTestingUtils.insertGroup(revision = 0, harness.self.asMember(), harness.others[0].asMember(), harness.others[1].asMember())
    val groupContextV2 = GroupContextV2.newBuilder().setRevision(0).setMasterKey(masterKey.serialize().toProtoByteString()).build()

    val initialTextMessage = DataMessage.newBuilder().buildWith {
      body = MessageContentFuzzer.string()
      groupV2 = groupContextV2
      timestamp = envelopeTimestamp
    }

    processorV2.process(
      envelope = MessageContentFuzzer.envelope(envelopeTimestamp),
      content = MessageContentFuzzer.syncSentTextMessage(initialTextMessage, deliveredTo = listOf(harness.others[0])),
      metadata = MessageContentFuzzer.envelopeMetadata(harness.self.id, harness.self.id, groupId),
      serverDeliveredTimestamp = MessageContentFuzzer.fuzzServerDeliveredTimestamp(envelopeTimestamp)
    )

    val threadId = SignalDatabase.threads.getThreadIdFor(groupRecipientId)!!
    val firstSyncMessages = MessageTableTestUtils.getMessages(threadId)
    val firstMessageId = firstSyncMessages[0].id
    val firstReceiptInfo = SignalDatabase.groupReceipts.getGroupReceiptInfo(firstMessageId)

    processorV2.process(
      envelope = MessageContentFuzzer.envelope(envelopeTimestamp),
      content = MessageContentFuzzer.syncSentTextMessage(initialTextMessage, deliveredTo = listOf(harness.others[0], harness.others[1]), recipientUpdate = true),
      metadata = MessageContentFuzzer.envelopeMetadata(harness.self.id, harness.self.id, groupId),
      serverDeliveredTimestamp = MessageContentFuzzer.fuzzServerDeliveredTimestamp(envelopeTimestamp)
    )

    val secondSyncMessages = MessageTableTestUtils.getMessages(threadId)
    val secondReceiptInfo = SignalDatabase.groupReceipts.getGroupReceiptInfo(firstMessageId)

    firstSyncMessages.size assertIs 1
    firstSyncMessages[0].body assertIs initialTextMessage.body
    firstReceiptInfo.first { it.recipientId == harness.others[0] }.status assertIs GroupReceiptTable.STATUS_UNDELIVERED
    firstReceiptInfo.first { it.recipientId == harness.others[1] }.status assertIs GroupReceiptTable.STATUS_UNKNOWN

    secondSyncMessages.size assertIs 1
    secondSyncMessages[0].body assertIs initialTextMessage.body
    secondReceiptInfo.first { it.recipientId == harness.others[0] }.status assertIs GroupReceiptTable.STATUS_UNDELIVERED
    secondReceiptInfo.first { it.recipientId == harness.others[1] }.status assertIs GroupReceiptTable.STATUS_UNDELIVERED
  }
}
