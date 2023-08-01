package org.thoughtcrime.securesms.messages

import android.content.Context
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.database.MessageTable.InsertResult
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.toBodyRangeList
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
import org.thoughtcrime.securesms.jobs.PushProcessEarlyMessagesJob
import org.thoughtcrime.securesms.jobs.SendDeliveryReceiptJob
import org.thoughtcrime.securesms.messages.MessageContentProcessorV2.Companion.log
import org.thoughtcrime.securesms.messages.MessageContentProcessorV2.Companion.warn
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.groupId
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isMediaMessage
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.toPointersWithinLimit
import org.thoughtcrime.securesms.mms.IncomingMediaMessage
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.notifications.v2.ConversationId.Companion.forConversation
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sms.IncomingEncryptedMessage
import org.thoughtcrime.securesms.sms.IncomingTextMessage
import org.thoughtcrime.securesms.util.EarlyMessageCacheEntry
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.MessageConstraintsUtil
import org.thoughtcrime.securesms.util.hasAudio
import org.thoughtcrime.securesms.util.hasSharedContact
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.DataMessage
import java.util.Optional

object EditMessageProcessor {
  fun process(
    context: Context,
    senderRecipient: Recipient,
    threadRecipient: Recipient,
    envelope: SignalServiceProtos.Envelope,
    content: SignalServiceProtos.Content,
    metadata: EnvelopeMetadata,
    earlyMessageCacheEntry: EarlyMessageCacheEntry?
  ) {
    val editMessage = content.editMessage

    log(envelope.timestamp, "[handleEditMessage] Edit message for " + editMessage.targetSentTimestamp)

    var targetMessage: MediaMmsMessageRecord? = SignalDatabase.messages.getMessageFor(editMessage.targetSentTimestamp, senderRecipient.id) as? MediaMmsMessageRecord
    val targetThreadRecipient: Recipient? = if (targetMessage != null) SignalDatabase.threads.getRecipientForThreadId(targetMessage.threadId) else null

    if (targetMessage == null || targetThreadRecipient == null) {
      warn(envelope.timestamp, "[handleEditMessage] Could not find matching message! timestamp: ${editMessage.targetSentTimestamp}  author: ${senderRecipient.id}")

      if (earlyMessageCacheEntry != null) {
        ApplicationDependencies.getEarlyMessageCache().store(senderRecipient.id, editMessage.targetSentTimestamp, earlyMessageCacheEntry)
        PushProcessEarlyMessagesJob.enqueue()
      }

      return
    }

    val message = editMessage.dataMessage
    val isMediaMessage = message.isMediaMessage
    val groupId: GroupId.V2? = message.groupV2.groupId

    val originalMessage = targetMessage.originalMessageId?.let { SignalDatabase.messages.getMessageRecord(it.id) } ?: targetMessage
    val validTiming = MessageConstraintsUtil.isValidEditMessageReceive(originalMessage, senderRecipient, envelope.serverTimestamp)
    val validAuthor = senderRecipient.id == originalMessage.fromRecipient.id
    val validGroup = groupId == targetThreadRecipient.groupId.orNull()
    val validTarget = !originalMessage.isViewOnce && !originalMessage.hasAudio() && !originalMessage.hasSharedContact()

    if (!validTiming || !validAuthor || !validGroup || !validTarget) {
      warn(envelope.timestamp, "[handleEditMessage] Invalid message edit! editTime: ${envelope.serverTimestamp}, targetTime: ${originalMessage.serverTimestamp}, editAuthor: ${senderRecipient.id}, targetAuthor: ${originalMessage.fromRecipient.id}, editThread: ${threadRecipient.id}, targetThread: ${targetThreadRecipient.id}, validity: (timing: $validTiming, author: $validAuthor, group: $validGroup, target: $validTarget)")
      return
    }

    if (groupId != null && MessageContentProcessorV2.handleGv2PreProcessing(context, envelope.timestamp, content, metadata, groupId, message.groupV2, senderRecipient) == MessageContentProcessorV2.Gv2PreProcessResult.IGNORE) {
      warn(envelope.timestamp, "[handleEditMessage] Group processor indicated we should ignore this.")
      return
    }

    DataMessageProcessor.notifyTypingStoppedFromIncomingMessage(context, senderRecipient, threadRecipient.id, metadata.sourceDeviceId)

    targetMessage = targetMessage.withAttachments(context, SignalDatabase.attachments.getAttachmentsForMessage(targetMessage.id))

    val insertResult: InsertResult? = if (isMediaMessage || targetMessage.quote != null || targetMessage.slideDeck.slides.isNotEmpty()) {
      handleEditMediaMessage(senderRecipient.id, groupId, envelope, metadata, message, targetMessage)
    } else {
      handleEditTextMessage(senderRecipient.id, groupId, envelope, metadata, message, targetMessage)
    }

    if (insertResult != null) {
      SignalExecutors.BOUNDED.execute {
        ApplicationDependencies.getJobManager().add(SendDeliveryReceiptJob(senderRecipient.id, message.timestamp, MessageId(insertResult.messageId)))
      }

      if (targetMessage.expireStarted > 0) {
        ApplicationDependencies.getExpiringMessageManager()
          .scheduleDeletion(
            insertResult.messageId,
            true,
            targetMessage.expireStarted,
            targetMessage.expiresIn
          )
      }

      ApplicationDependencies.getMessageNotifier().updateNotification(context, forConversation(insertResult.threadId))
    }
  }

  private fun handleEditMediaMessage(
    senderRecipientId: RecipientId,
    groupId: GroupId.V2?,
    envelope: SignalServiceProtos.Envelope,
    metadata: EnvelopeMetadata,
    message: DataMessage,
    targetMessage: MediaMmsMessageRecord
  ): InsertResult? {
    val messageRanges: BodyRangeList? = message.bodyRangesList.filter { it.hasStyle() }.toList().toBodyRangeList()
    val targetQuote = targetMessage.quote
    val quote: QuoteModel? = if (targetQuote != null && message.hasQuote()) {
      QuoteModel(
        targetQuote.id,
        targetQuote.author,
        targetQuote.displayText.toString(),
        targetQuote.isOriginalMissing,
        emptyList(),
        null,
        targetQuote.quoteType,
        null
      )
    } else {
      null
    }
    val attachments = message.attachmentsList.toPointersWithinLimit()
    attachments.filter {
      MediaUtil.SlideType.LONG_TEXT == MediaUtil.getSlideTypeFromContentType(it.contentType)
    }
    val mediaMessage = IncomingMediaMessage(
      from = senderRecipientId,
      sentTimeMillis = message.timestamp,
      serverTimeMillis = envelope.serverTimestamp,
      receivedTimeMillis = targetMessage.dateReceived,
      expiresIn = targetMessage.expiresIn,
      isViewOnce = message.isViewOnce,
      isUnidentified = metadata.sealedSender,
      body = message.body,
      groupId = groupId,
      attachments = attachments,
      quote = quote,
      sharedContacts = emptyList(),
      linkPreviews = DataMessageProcessor.getLinkPreviews(message.previewList, message.body ?: "", false),
      mentions = DataMessageProcessor.getMentions(message.bodyRangesList),
      serverGuid = envelope.serverGuid,
      messageRanges = messageRanges,
      isPushMessage = true
    )

    val insertResult = SignalDatabase.messages.insertEditMessageInbox(-1, mediaMessage, targetMessage).orNull()
    if (insertResult?.insertedAttachments != null) {
      SignalDatabase.runPostSuccessfulTransaction {
        val downloadJobs: List<AttachmentDownloadJob> = insertResult.insertedAttachments.mapNotNull { (_, attachmentId) ->
          AttachmentDownloadJob(insertResult.messageId, attachmentId, false)
        }
        ApplicationDependencies.getJobManager().addAll(downloadJobs)
      }
    }
    return insertResult
  }

  private fun handleEditTextMessage(
    senderRecipientId: RecipientId,
    groupId: GroupId.V2?,
    envelope: SignalServiceProtos.Envelope,
    metadata: EnvelopeMetadata,
    message: DataMessage,
    targetMessage: MediaMmsMessageRecord
  ): InsertResult? {
    var textMessage = IncomingTextMessage(
      senderRecipientId,
      metadata.sourceDeviceId,
      envelope.timestamp,
      envelope.timestamp,
      targetMessage.dateReceived,
      message.body,
      Optional.ofNullable(groupId),
      targetMessage.expiresIn,
      metadata.sealedSender,
      envelope.serverGuid
    )

    textMessage = IncomingEncryptedMessage(textMessage, message.body)

    return SignalDatabase.messages.insertEditMessageInbox(textMessage, targetMessage).orNull()
  }
}
