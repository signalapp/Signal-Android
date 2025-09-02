/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import kotlin.time.Duration.Companion.days

/**
 * It's possible for a message to be inserted into the database but the job to send it to be lost. This
 * job tries to catch those rare situations by finding any pending messages and enqueue a second check
 * at the end of the thread's message sending queue.
 *
 * If that second job still sees the message as pending, then it will try to resend it.
 *
 * Note job is in-memory only as it should only run once on app launch and the job will always
 * be last in the overall queue at time of submission.
 */
class RetryPendingSendsJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    const val KEY = "RetryPendingSendsJob"
    private val TAG = Log.tag(RetryPendingSendsJob::class)

    @JvmStatic
    fun enqueueForAll() {
      AppDependencies.jobManager.add(RetryPendingSendsJob())
    }
  }

  private constructor() : this(
    Parameters.Builder()
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxInstancesForFactory(1)
      .setMemoryOnly(true)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    SignalDatabase.messages.getRecentPendingMessages().use { reader ->
      reader.forEach { message ->
        val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(message.threadId)
        if (threadRecipient != null) {
          val hasMedia = (message as? MmsMessageRecord)?.slideDeck?.slides?.isNotEmpty() == true
          Log.d(TAG, "[${message.dateSent}] Found pending message MessageId::${message.id}, enqueueing second check job")
          AppDependencies.jobManager.add(RetryPendingSendSecondCheckJob(MessageId(message.id), threadRecipient, hasMedia))
        }
      }
    }

    return Result.success()
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<RetryPendingSendsJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RetryPendingSendsJob {
      return RetryPendingSendsJob(parameters)
    }
  }
}
