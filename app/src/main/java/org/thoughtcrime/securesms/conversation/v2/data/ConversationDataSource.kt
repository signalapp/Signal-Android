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
import org.thoughtcrime.securesms.backup.v2.BackupRestoreManager
import org.thoughtcrime.securesms.conversation.ConversationData
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.ConversationMessage.ConversationMessageFactory
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord.RemovedContactHidden
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord.UniversalExpireTimerUpdate
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messagerequests.MessageRequestRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel

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
  private val localContext: Context,
  private val threadId: Long,
  private val messageRequestData: ConversationData.MessageRequestData,
  private val showUniversalExpireTimerUpdate: Boolean,
  private var baseSize: Int,
  private val messageRequestRepository: MessageRequestRepository = MessageRequestRepository(localContext)
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
    val stopwatch = Stopwatch(title = "load($start, $length), thread $threadId", decimalPlaces = 2)
    var records: MutableList<MessageRecord> = ArrayList(length)

    MessageTable.mmsReaderFor(SignalDatabase.messages.getConversation(threadId, start.toLong(), length.toLong()))
      .use { reader ->
        reader.forEach { record ->
          if (cancellationSignal.isCanceled) {
            return@forEach
          }

          records.add(record)
        }
      }

    if (messageRequestData.isHidden && (start + length >= totalSize)) {
      records.add(RemovedContactHidden(threadId))
    }

    if (showUniversalExpireTimerUpdate) {
      records.add(UniversalExpireTimerUpdate(threadId))
    }

    stopwatch.split("messages")

    val extraData = MessageDataFetcher.fetch(records)
    stopwatch.split("extra-data")

    records = MessageDataFetcher.updateModelsWithData(records, extraData).toMutableList()
    stopwatch.split("models")

    if (RemoteConfig.messageBackups && SignalStore.backup.restoreState.inProgress) {
      BackupRestoreManager.prioritizeAttachmentsIfNeeded(records)
      stopwatch.split("restore")
    }

    val messages = records.map { record ->
      ConversationMessageFactory.createWithUnresolvedData(
        localContext,
        record,
        record.getDisplayBody(localContext),
        extraData.mentionsById[record.id],
        extraData.hasBeenQuoted.contains(record.id),
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
    val log = stopwatch.stopAndGetLogString()
    Log.d(TAG, "$log || ${extraData.timeLog}")

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

    val stopwatch = Stopwatch(title = "load($key), thread $threadId", decimalPlaces = 2)
    var record = SignalDatabase.messages.getMessageRecordOrNull(key.id)

    if ((record as? MmsMessageRecord)?.parentStoryId?.isGroupReply() == true) {
      return null
    }

    val scheduleDate = (record as? MmsMessageRecord)?.scheduledDate
    if (scheduleDate != null && scheduleDate != -1L) {
      return null
    }

    stopwatch.split("message")

    var extraData: MessageDataFetcher.ExtraMessageData? = null
    try {
      if (record == null) {
        return null
      } else {
        extraData = MessageDataFetcher.fetch(record)
        stopwatch.split("extra-data")

        record = MessageDataFetcher.updateModelWithData(record, extraData)
        stopwatch.split("models")

        return ConversationMessageFactory.createWithUnresolvedData(
          localContext,
          record,
          record.getDisplayBody(AppDependencies.application),
          extraData.mentionsById[record.id],
          extraData.hasBeenQuoted.contains(record.id),
          threadRecipient
        ).toMappingModel()
      }
    } finally {
      val log = stopwatch.stopAndGetLogString()
      Log.d(TAG, "$log || ${extraData?.timeLog}")
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
      if (this.isTextOnly(localContext)) {
        OutgoingTextOnly(this)
      } else {
        OutgoingMedia(this)
      }
    } else {
      if (this.isTextOnly(localContext)) {
        IncomingTextOnly(this)
      } else {
        IncomingMedia(this)
      }
    }
  }
}
