/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.data

import android.content.Context
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.signal.core.util.toInt
import org.signal.paging.PagedDataSource
import org.thoughtcrime.securesms.conversation.ConversationData
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.ConversationMessage.ConversationMessageFactory
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord.NoGroupsInCommon
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord.RemovedContactHidden
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord.UniversalExpireTimerUpdate
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.messagerequests.MessageRequestRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.whispersystems.signalservice.api.push.ServiceId

private typealias ConversationElement = MappingModel<*>

sealed interface ConversationElementKey {

  fun requireMessageId(): Long = error("Not implemented for this key")

  companion object {
    fun forMessage(id: Long): ConversationElementKey = MessageBackedKey(id)
    val threadHeader: ConversationElementKey = ThreadHeaderKey
  }
}

private data class MessageBackedKey(val id: Long) : ConversationElementKey {
  override fun requireMessageId(): Long = id
}

private object ThreadHeaderKey : ConversationElementKey

/**
 * ConversationDataSource for V2. Assumes that ThreadId is never -1L.
 */
class ConversationDataSource(
  private val context: Context,
  private val threadId: Long,
  private val messageRequestData: ConversationData.MessageRequestData,
  private val showUniversalExpireTimerUpdate: Boolean,
  private var baseSize: Int,
  private val messageRequestRepository: MessageRequestRepository = MessageRequestRepository(context)
) : PagedDataSource<ConversationElementKey, ConversationElement> {

  companion object {
    private val TAG = Log.tag(ConversationDataSource::class.java)
    private const val THREAD_HEADER_COUNT = 1
  }

  init {
    check(threadId > 0)
  }

  private val threadRecipient: Recipient by lazy {
    SignalDatabase.threads.getRecipientForThreadId(threadId)!!
  }

  override fun size(): Int {
    val startTime = System.currentTimeMillis()
    val size: Int = getSizeInternal() +
      THREAD_HEADER_COUNT +
      messageRequestData.includeWarningUpdateMessage().toInt() +
      messageRequestData.isHidden.toInt() +
      showUniversalExpireTimerUpdate.toInt()

    Log.d(TAG, "[size(), thread $threadId] ${System.currentTimeMillis() - startTime} ms")

    return size
  }

  private fun getSizeInternal(): Int {
    synchronized(this) {
      if (baseSize != -1) {
        val size = baseSize
        baseSize = -1
        return size
      }
    }

    return SignalDatabase.messages.getMessageCountForThread(threadId)
  }

  override fun load(start: Int, length: Int, totalSize: Int, cancellationSignal: PagedDataSource.CancellationSignal): List<ConversationElement> {
    val stopwatch = Stopwatch("load($start, $length), thread $threadId")
    var records: MutableList<MessageRecord> = ArrayList(length)
    val mentionHelper = MentionHelper()
    val quotedHelper = QuotedHelper()
    val attachmentHelper = AttachmentHelper()
    val reactionHelper = ReactionHelper()
    val paymentHelper = PaymentHelper()
    val callHelper = CallHelper()
    val referencedIds = hashSetOf<ServiceId>()

    MessageTable.mmsReaderFor(SignalDatabase.messages.getConversation(threadId, start.toLong(), length.toLong()))
      .use { reader ->
        reader.forEach { record ->
          if (cancellationSignal.isCanceled) {
            return@forEach
          }

          records.add(record)
          mentionHelper.add(record)
          quotedHelper.add(record)
          reactionHelper.add(record)
          attachmentHelper.add(record)
          paymentHelper.add(record)
          callHelper.add(record)

          val updateDescription = record.getUpdateDisplayBody(context, null)
          if (updateDescription != null) {
            referencedIds.addAll(updateDescription.mentioned)
          }
        }
      }

    if (messageRequestData.includeWarningUpdateMessage() && (start + length >= totalSize)) {
      records.add(NoGroupsInCommon(threadId, messageRequestData.isGroup))
    }

    if (messageRequestData.isHidden && (start + length >= totalSize)) {
      records.add(RemovedContactHidden(threadId))
    }

    if (showUniversalExpireTimerUpdate) {
      records.add(UniversalExpireTimerUpdate(threadId))
    }

    stopwatch.split("messages")

    mentionHelper.fetchMentions(context)
    stopwatch.split("mentions")

    quotedHelper.fetchQuotedState()
    stopwatch.split("is-quoted")

    reactionHelper.fetchReactions()
    stopwatch.split("reactions")

    records = reactionHelper.buildUpdatedModels(records)
    stopwatch.split("reaction-models")

    attachmentHelper.fetchAttachments()
    stopwatch.split("attachments")

    records = attachmentHelper.buildUpdatedModels(context, records)
    stopwatch.split("attachment-models")

    paymentHelper.fetchPayments()
    stopwatch.split("payments")

    records = paymentHelper.buildUpdatedModels(records)
    stopwatch.split("payment-models")

    callHelper.fetchCalls()
    stopwatch.split("calls")

    records = callHelper.buildUpdatedModels(records)
    stopwatch.split("call-models")

    referencedIds.forEach { Recipient.resolved(RecipientId.from(it)) }
    stopwatch.split("recipient-resolves")

    val messages = records.map { record ->
      ConversationMessageFactory.createWithUnresolvedData(
        context,
        record,
        record.getDisplayBody(context),
        mentionHelper.getMentions(record.id),
        quotedHelper.isQuoted(record.id),
        threadRecipient
      ).toMappingModel()
    }

    stopwatch.split("conversion")

    val threadHeaderIndex = totalSize - THREAD_HEADER_COUNT

    val threadHeaders: List<ConversationElement> = if (start + length > threadHeaderIndex) {
      listOf(loadThreadHeader())
    } else {
      emptyList()
    }

    stopwatch.split("header")
    stopwatch.stop(TAG)

    return if (threadHeaders.isNotEmpty()) messages + threadHeaders else messages
  }

  override fun load(key: ConversationElementKey): ConversationElement? {
    if (key is ThreadHeaderKey) {
      return loadThreadHeader()
    }

    if (key !is MessageBackedKey) {
      Log.w(TAG, "Loading non-message related id $key")
      return null
    }

    val stopwatch = Stopwatch("load($key), thread $threadId")
    var record = SignalDatabase.messages.getMessageRecordOrNull(key.id)

    if ((record as? MediaMmsMessageRecord)?.parentStoryId?.isGroupReply() == true) {
      return null
    }

    val scheduleDate = (record as? MediaMmsMessageRecord)?.scheduledDate
    if (scheduleDate != null && scheduleDate != -1L) {
      return null
    }

    stopwatch.split("message")

    try {
      if (record == null) {
        return null
      } else {
        val mentions = SignalDatabase.mentions.getMentionsForMessage(key.id)
        stopwatch.split("mentions")

        val isQuoted = SignalDatabase.messages.isQuoted(record)
        stopwatch.split("is-quoted")

        val reactions = SignalDatabase.reactions.getReactions(MessageId(key.id))
        record = ReactionHelper.recordWithReactions(record, reactions)
        stopwatch.split("reactions")

        val attachments = SignalDatabase.attachments.getAttachmentsForMessage(key.id)
        if (attachments.size > 0) {
          record = (record as MediaMmsMessageRecord).withAttachments(context, attachments)
        }
        stopwatch.split("attachments")

        if (record.isPaymentNotification) {
          record = SignalDatabase.payments.updateMessageWithPayment(record)
        }
        stopwatch.split("payments")

        if (record.isCallLog && !record.isGroupCall) {
          val call = SignalDatabase.calls.getCallByMessageId(record.id)
          if (call != null && record is MediaMmsMessageRecord) {
            record = record.withCall(call)
          }
        }
        stopwatch.split("calls")

        return ConversationMessageFactory.createWithUnresolvedData(
          ApplicationDependencies.getApplication(),
          record,
          record.getDisplayBody(ApplicationDependencies.getApplication()),
          mentions,
          isQuoted,
          threadRecipient
        ).toMappingModel()
      }
    } finally {
      stopwatch.stop(TAG)
    }
  }

  override fun getKey(conversationMessage: ConversationElement): ConversationElementKey {
    return when (conversationMessage) {
      is ConversationMessageElement -> MessageBackedKey(conversationMessage.conversationMessage.messageRecord.id)
      is ThreadHeader -> ThreadHeaderKey
      else -> throw AssertionError()
    }
  }

  private fun loadThreadHeader(): ThreadHeader {
    return ThreadHeader(messageRequestRepository.getRecipientInfo(threadRecipient.id, threadId))
  }

  private fun ConversationMessage.toMappingModel(): MappingModel<*> {
    return if (messageRecord.isUpdate) {
      ConversationUpdate(this)
    } else if (messageRecord.isOutgoing) {
      if (this.isTextOnly(context)) {
        OutgoingTextOnly(this)
      } else {
        OutgoingMedia(this)
      }
    } else {
      if (this.isTextOnly(context)) {
        IncomingTextOnly(this)
      } else {
        IncomingMedia(this)
      }
    }
  }
}
