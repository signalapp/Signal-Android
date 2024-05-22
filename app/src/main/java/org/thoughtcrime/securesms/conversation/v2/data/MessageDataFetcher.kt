/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.data

import androidx.annotation.WorkerThread
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.roundedString
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.database.model.withAttachments
import org.thoughtcrime.securesms.database.model.withCall
import org.thoughtcrime.securesms.database.model.withPayment
import org.thoughtcrime.securesms.database.model.withReactions
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.payments.Payment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

/**
 * Fetches various pieces of associated message data in parallel and returns the result.
 */
object MessageDataFetcher {

  /**
   * Singular version of [fetch].
   */
  fun fetch(messageRecord: MessageRecord): ExtraMessageData {
    return fetch(listOf(messageRecord))
  }

  /**
   * Fetches all associated message data in parallel.
   * It also performs a side-effect of resolving recipients referenced in group update messages.
   *
   * While work is spun off on various threads, the calling thread is blocked until they all complete,
   * so this should be called on a background thread.
   */
  @WorkerThread
  fun fetch(messageRecords: List<MessageRecord>): ExtraMessageData {
    val startTimeNanos = System.nanoTime()
    val context = AppDependencies.application

    val messageIds: List<Long> = messageRecords.map { it.id }
    val executor = SignalExecutors.BOUNDED

    val mentionsFuture = executor.submitTimed {
      SignalDatabase.mentions.getMentionsForMessages(messageIds)
    }

    val hasBeenQuotedFuture = executor.submitTimed {
      SignalDatabase.messages.isQuoted(messageRecords)
    }

    val reactionsFuture = executor.submitTimed {
      SignalDatabase.reactions.getReactionsForMessages(messageIds)
    }

    val attachmentsFuture = executor.submitTimed {
      SignalDatabase.attachments.getAttachmentsForMessages(messageIds)
    }

    val paymentsFuture = executor.submitTimed {
      val paymentUuidToMessageId: Map<UUID, Long> = messageRecords
        .filter { it.isMms && it.isPaymentNotification }
        .map { UuidUtil.parseOrNull(it.body) to it.id }
        .filter { it.first != null }
        .associate { it.first to it.second }

      SignalDatabase
        .payments
        .getPayments(paymentUuidToMessageId.keys)
        .associateBy { paymentUuidToMessageId[it.uuid]!! }
    }

    val callsFuture = executor.submitTimed {
      SignalDatabase.calls.getCalls(messageIds)
    }

    val recipientsFuture = executor.submitTimed {
      messageRecords.forEach { record ->
        record.getUpdateDisplayBody(context, null)?.let { description ->
          val ids = description.mentioned.map { RecipientId.from(it) }
          Recipient.resolvedList(ids)
        }
      }
    }

    val mentionsResult = mentionsFuture.get()
    val hasBeenQuotedResult = hasBeenQuotedFuture.get()
    val reactionsResult = reactionsFuture.get()
    val attachmentsResult = attachmentsFuture.get()
    val paymentsResult = paymentsFuture.get()
    val callsResult = callsFuture.get()
    val recipientsResult = recipientsFuture.get()

    val wallTimeMs = (System.nanoTime() - startTimeNanos).nanoseconds.toDouble(DurationUnit.MILLISECONDS)

    val cpuTimeNanos = arrayOf(mentionsResult, hasBeenQuotedResult, reactionsResult, attachmentsResult, paymentsResult, callsResult, recipientsResult).sumOf { it.durationNanos }
    val cpuTimeMs = cpuTimeNanos.nanoseconds.toDouble(DurationUnit.MILLISECONDS)

    return ExtraMessageData(
      mentionsById = mentionsResult.result,
      hasBeenQuoted = hasBeenQuotedResult.result,
      reactions = reactionsResult.result,
      attachments = attachmentsResult.result,
      payments = paymentsResult.result,
      calls = callsResult.result,
      timeLog = "mentions: ${mentionsResult.duration}, is-quoted: ${hasBeenQuotedResult.duration}, reactions: ${reactionsResult.duration}, attachments: ${attachmentsResult.duration}, payments: ${paymentsResult.duration}, calls: ${callsResult.duration} >> cpuTime: ${cpuTimeMs.roundedString(2)}, wallTime: ${wallTimeMs.roundedString(2)}"
    )
  }

  /**
   * Merges the data in [ExtraMessageData] into the provided list of [MessageRecord], outputted as
   * a new list of models.
   */
  fun updateModelsWithData(messageRecords: List<MessageRecord>, data: ExtraMessageData): List<MessageRecord> {
    return messageRecords.map { it.updateWithData(data) }
  }

  /**
   * Singular version of [updateModelsWithData]
   */
  fun updateModelWithData(messageRecord: MessageRecord, data: ExtraMessageData): MessageRecord {
    return listOf(messageRecord).map { it.updateWithData(data) }.first()
  }

  private fun MessageRecord.updateWithData(data: ExtraMessageData): MessageRecord {
    var output: MessageRecord = this

    output = data.reactions[id]?.let {
      output.withReactions(it)
    } ?: output

    output = data.attachments[id]?.let {
      output.withAttachments(it)
    } ?: output

    output = data.payments[id]?.let {
      output.withPayment(it)
    } ?: output

    output = data.calls[id]?.let {
      output.withCall(it)
    } ?: output

    return output
  }

  private fun <T> ExecutorService.submitTimed(callable: Callable<T>): Future<TimedResult<T>> {
    return this.submit(
      Callable {
        val start = System.nanoTime()
        val result = callable.call()
        val end = System.nanoTime()

        TimedResult(result = result, durationNanos = end - start)
      }
    )
  }

  data class TimedResult<T>(
    val result: T,
    val durationNanos: Long
  ) {
    val duration: String
      get() = durationNanos.nanoseconds.toDouble(DurationUnit.MILLISECONDS).roundedString(2)
  }

  data class ExtraMessageData(
    val mentionsById: Map<Long, List<Mention>>,
    val hasBeenQuoted: Set<Long>,
    val reactions: Map<Long, List<ReactionRecord>>,
    val attachments: Map<Long, List<DatabaseAttachment>>,
    val payments: Map<Long, Payment>,
    val calls: Map<Long, CallTable.Call>,
    val timeLog: String
  )
}
