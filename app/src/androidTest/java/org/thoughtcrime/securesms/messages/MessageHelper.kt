/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import android.net.Uri
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.UriAttachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.UriAttachmentBuilder
import org.thoughtcrime.securesms.database.model.GroupsV2UpdateMessageConverter
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context
import org.thoughtcrime.securesms.database.model.databaseprotos.GV2UpdateDescription
import org.thoughtcrime.securesms.jobs.ThreadUpdateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.GroupTestingUtils
import org.thoughtcrime.securesms.testing.MessageContentFuzzer
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.util.MediaUtil
import java.util.UUID
import kotlin.random.Random

/**
 * Makes inserting messages through the "normal" code paths simpler. Mostly focused on incoming messages.
 */
class MessageHelper(private val harness: SignalActivityRule, var startTime: Long = System.currentTimeMillis()) {

  val alice: RecipientId = harness.others[0]
  val bob: RecipientId = harness.others[1]
  val group: GroupTestingUtils.TestGroupInfo = harness.group!!
  val processor: MessageContentProcessor = MessageContentProcessor(harness.context)

  init {
    val threadIdSlot = slot<Long>()
    mockkStatic(ThreadUpdateJob::class)
    every { ThreadUpdateJob.enqueue(capture(threadIdSlot)) } answers {
      SignalDatabase.threads.update(threadIdSlot.captured, false)
    }
  }

  fun tearDown() {
    unmockkStatic(ThreadUpdateJob::class)
  }

  fun incomingText(sender: RecipientId = alice, destination: RecipientId = harness.self.id): MessageData {
    startTime = nextStartTime()

    val messageData = MessageData(author = sender, timestamp = startTime)

    processor.process(
      envelope = MessageContentFuzzer.envelope(messageData.timestamp, serverGuid = messageData.serverGuid),
      content = MessageContentFuzzer.fuzzTextMessage(
        sentTimestamp = messageData.timestamp,
        groupContextV2 = if (destination == group.recipientId) group.groupV2Context else null,
        allowExpireTimeChanges = false
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

  fun outgoingText(conversationId: RecipientId = alice, successfulSend: Boolean = true, updateMessage: ((OutgoingMessage) -> OutgoingMessage)? = null): MessageData {
    startTime = nextStartTime()

    val messageData = MessageData(author = harness.self.id, timestamp = startTime)
    val threadRecipient = Recipient.resolved(conversationId)

    val message = OutgoingMessage(
      threadRecipient = threadRecipient,
      body = MessageContentFuzzer.string(),
      sentTimeMillis = messageData.timestamp,
      isUrgent = true,
      isSecure = true
    ).let { updateMessage?.invoke(it) ?: it }

    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(threadRecipient)
    val messageId = SignalDatabase.messages.insertMessageOutbox(message, threadId, false, null)

    if (successfulSend) {
      SignalDatabase.messages.markAsSent(messageId, true)
    }

    return messageData.copy(messageId = messageId)
  }

  fun outgoingMessage(conversationId: RecipientId = alice, updateMessage: OutgoingMessage.() -> OutgoingMessage): MessageData {
    startTime = nextStartTime()

    val messageData = MessageData(author = harness.self.id, timestamp = startTime)
    val threadRecipient = Recipient.resolved(conversationId)

    val message = OutgoingMessage(
      threadRecipient = threadRecipient,
      sentTimeMillis = messageData.timestamp,
      isUrgent = true,
      isSecure = true
    ).apply { updateMessage() }

    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(threadRecipient)
    val messageId = SignalDatabase.messages.insertMessageOutbox(message, threadId, false, null)

    return messageData.copy(messageId = messageId)
  }

  fun outgoingAttachment(data: ByteArray, uuid: UUID? = UUID.randomUUID()): Attachment {
    val uri: Uri = BlobProvider.getInstance().forData(data).createForSingleSessionInMemory()

    val attachment: UriAttachment = UriAttachmentBuilder.build(
      id = Random.nextLong(),
      uri = uri,
      contentType = MediaUtil.IMAGE_JPEG,
      transformProperties = AttachmentTable.TransformProperties(),
      uuid = uuid
    )

    return attachment
  }

  fun outgoingGroupChange(): MessageData {
    startTime = nextStartTime()

    val messageData = MessageData(author = harness.self.id, timestamp = startTime)
    val groupRecipient = Recipient.resolved(group.recipientId)
    val decryptedGroupV2Context = DecryptedGroupV2Context(
      context = group.groupV2Context,
      groupState = SignalDatabase.groups.getGroup(group.groupId).get().requireV2GroupProperties().decryptedGroup
    )

    val updateDescription = GV2UpdateDescription.Builder()
      .gv2ChangeDescription(decryptedGroupV2Context)
      .groupChangeUpdate(GroupsV2UpdateMessageConverter.translateDecryptedChange(SignalStore.account.getServiceIds(), decryptedGroupV2Context))
      .build()

    val outgoingMessage = OutgoingMessage.groupUpdateMessage(groupRecipient, updateDescription, startTime)

    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(groupRecipient)
    val messageId = SignalDatabase.messages.insertMessageOutbox(outgoingMessage, threadId, false, null)
    SignalDatabase.messages.markAsSent(messageId, true)

    return messageData.copy(messageId = messageId)
  }

  fun incomingMedia(sender: RecipientId = alice, destination: RecipientId = harness.self.id): MessageData {
    startTime = nextStartTime()

    val messageData = MessageData(author = sender, timestamp = startTime)

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
    startTime = nextStartTime()

    val messageData = MessageData(author = sender, timestamp = startTime)

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
    startTime = nextStartTime()
    val messageData = MessageData(timestamp = startTime)

    processor.process(
      envelope = MessageContentFuzzer.envelope(messageData.timestamp, serverGuid = messageData.serverGuid),
      content = MessageContentFuzzer.syncReadsMessage(reads.toList()),
      metadata = MessageContentFuzzer.envelopeMetadata(harness.self.id, harness.self.id, sourceDeviceId = 2),
      serverDeliveredTimestamp = messageData.timestamp + 10
    )

    return messageData
  }

  fun syncDeleteForMeMessage(vararg deletes: MessageContentFuzzer.DeleteForMeSync): MessageData {
    startTime = nextStartTime()
    val messageData = MessageData(timestamp = startTime)

    processor.process(
      envelope = MessageContentFuzzer.envelope(messageData.timestamp, serverGuid = messageData.serverGuid),
      content = MessageContentFuzzer.syncDeleteForMeMessage(deletes.toList()),
      metadata = MessageContentFuzzer.envelopeMetadata(harness.self.id, harness.self.id, sourceDeviceId = 2),
      serverDeliveredTimestamp = messageData.timestamp + 10
    )

    return messageData
  }

  fun syncDeleteForMeConversation(vararg deletes: MessageContentFuzzer.DeleteForMeSync): MessageData {
    startTime = nextStartTime()
    val messageData = MessageData(timestamp = startTime)

    processor.process(
      envelope = MessageContentFuzzer.envelope(messageData.timestamp, serverGuid = messageData.serverGuid),
      content = MessageContentFuzzer.syncDeleteForMeConversation(deletes.toList()),
      metadata = MessageContentFuzzer.envelopeMetadata(harness.self.id, harness.self.id, sourceDeviceId = 2),
      serverDeliveredTimestamp = messageData.timestamp + 10
    )

    return messageData
  }

  fun syncDeleteForMeLocalOnlyConversation(vararg conversations: RecipientId): MessageData {
    startTime = nextStartTime()
    val messageData = MessageData(timestamp = startTime)

    processor.process(
      envelope = MessageContentFuzzer.envelope(messageData.timestamp, serverGuid = messageData.serverGuid),
      content = MessageContentFuzzer.syncDeleteForMeLocalOnlyConversation(conversations.toList()),
      metadata = MessageContentFuzzer.envelopeMetadata(harness.self.id, harness.self.id, sourceDeviceId = 2),
      serverDeliveredTimestamp = messageData.timestamp + 10
    )

    return messageData
  }

  fun syncDeleteForMeAttachment(conversationId: RecipientId, message: Pair<RecipientId, Long>, uuid: UUID?, digest: ByteArray?, plainTextHash: String?): MessageData {
    startTime = nextStartTime()
    val messageData = MessageData(timestamp = startTime)

    processor.process(
      envelope = MessageContentFuzzer.envelope(messageData.timestamp, serverGuid = messageData.serverGuid),
      content = MessageContentFuzzer.syncDeleteForMeAttachment(conversationId, message, uuid, digest, plainTextHash),
      metadata = MessageContentFuzzer.envelopeMetadata(harness.self.id, harness.self.id, sourceDeviceId = 2),
      serverDeliveredTimestamp = messageData.timestamp + 10
    )

    return messageData
  }

  /**
   * Get the next "sentTimestamp" for current + [nextMessageOffset]th message. Useful for early message processing and future message timestamps.
   */
  fun nextStartTime(nextMessageOffset: Int = 1): Long {
    return startTime + 1000 * nextMessageOffset
  }

  data class MessageData(
    val author: RecipientId = RecipientId.UNKNOWN,
    val serverGuid: UUID = UUID.randomUUID(),
    val timestamp: Long,
    val messageId: Long = -1L
  )
}
