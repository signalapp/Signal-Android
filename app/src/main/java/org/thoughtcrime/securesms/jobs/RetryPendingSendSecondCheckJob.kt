/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.protos.SecondRoundFixupSendJobData
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import kotlin.time.Duration.Companion.days

/**
 * Only enqueued by [RetryPendingSendsJob] for messages that are pending on app launch. When this job runs, if a message is still pending
 * then no send job happened between app launch and draining of that thread's message send job queue. Thus it should be safe
 * to try to resend the message.
 *
 * Note job is in-memory only to prevent multiple instances being enqueued per message.
 */
class RetryPendingSendSecondCheckJob private constructor(parameters: Parameters, private val messageId: MessageId) : Job(parameters) {

  companion object {
    const val KEY = "RetryPendingSendSecondCheckJob"
    private val TAG = Log.tag(RetryPendingSendSecondCheckJob::class)
  }

  constructor(messageId: MessageId, threadRecipient: Recipient, hasMedia: Boolean) : this(
    parameters = Parameters.Builder()
      .setQueue(threadRecipient.id.toQueueKey(hasMedia))
      .setLifespan(1.days.inWholeMilliseconds)
      .setMemoryOnly(true)
      .build(),
    messageId = messageId
  )

  override fun serialize(): ByteArray? = SecondRoundFixupSendJobData(messageId = messageId.id).encode()

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    val messageRecord = SignalDatabase.messages.getMessageRecord(messageId.id)

    if (!messageRecord.isPending) {
      return Result.success()
    }

    Log.w(TAG, "[${messageRecord.dateSent}] Still pending after queue drain, re-sending MessageId::${messageId.id}!")

    MessageSender.resend(context, messageRecord)

    return Result.success()
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<RetryPendingSendSecondCheckJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RetryPendingSendSecondCheckJob {
      val data = SecondRoundFixupSendJobData.ADAPTER.decode(serializedData!!)
      return RetryPendingSendSecondCheckJob(parameters, MessageId(data.messageId))
    }
  }
}
