/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import androidx.annotation.WorkerThread
import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.jobs.protos.CallLogEventSendJobData
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.util.concurrent.TimeUnit

/**
 * Sends CallLogEvents to synced devices.
 */
class CallLogEventSendJob private constructor(
  parameters: Parameters,
  private val callLogEvent: SyncMessage.CallLogEvent
) : CoroutineJob(parameters) {

  companion object {
    const val KEY = "CallLogEventSendJob"

    private val TAG = Log.tag(CallLogEventSendJob::class.java)

    @WorkerThread
    fun forClearHistory(
      call: CallTable.Call
    ) = CallLogEventSendJob(
      Parameters.Builder()
        .setQueue("CallLogEventSendJob")
        .setLifespan(TimeUnit.DAYS.toMillis(1))
        .setMaxAttempts(Parameters.UNLIMITED)
        .addConstraint(NetworkConstraint.KEY)
        .addConstraint(SealedSenderConstraint.KEY)
        .build(),
      SyncMessage.CallLogEvent(
        timestamp = call.timestamp,
        callId = call.callId,
        conversationId = Recipient.resolved(call.peer).requireCallConversationId().toByteString(),
        type = SyncMessage.CallLogEvent.Type.CLEAR
      )
    )

    @WorkerThread
    fun forMarkedAsRead(
      call: CallTable.Call
    ) = CallLogEventSendJob(
      Parameters.Builder()
        .setQueue("CallLogEventSendJob")
        .setLifespan(TimeUnit.DAYS.toMillis(1))
        .setMaxAttempts(Parameters.UNLIMITED)
        .addConstraint(NetworkConstraint.KEY)
        .addConstraint(SealedSenderConstraint.KEY)
        .build(),
      SyncMessage.CallLogEvent(
        timestamp = call.timestamp,
        callId = call.callId,
        conversationId = Recipient.resolved(call.peer).requireCallConversationId().toByteString(),
        type = SyncMessage.CallLogEvent.Type.MARKED_AS_READ
      )
    )

    @JvmStatic
    @WorkerThread
    fun forMarkedAsReadInConversation(
      call: CallTable.Call
    ) = CallLogEventSendJob(
      Parameters.Builder()
        .setQueue("CallLogEventSendJob")
        .setLifespan(TimeUnit.DAYS.toMillis(1))
        .setMaxAttempts(Parameters.UNLIMITED)
        .addConstraint(NetworkConstraint.KEY)
        .addConstraint(SealedSenderConstraint.KEY)
        .build(),
      SyncMessage.CallLogEvent(
        timestamp = call.timestamp,
        callId = call.callId,
        conversationId = Recipient.resolved(call.peer).requireCallConversationId().toByteString(),
        type = SyncMessage.CallLogEvent.Type.MARKED_AS_READ_IN_CONVERSATION
      )
    )
  }

  override fun serialize(): ByteArray = CallLogEventSendJobData.Builder()
    .callLogEvent(callLogEvent.encodeByteString())
    .build()
    .encode()

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override suspend fun doRun(): Result {
    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncCallLogEvent(callLogEvent),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error ->
        error.toSyncJobResult(TAG, "call log event sync", defaultBackoff())
      }
    )
  }

  class Factory : Job.Factory<CallLogEventSendJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CallLogEventSendJob {
      return CallLogEventSendJob(
        parameters,
        SyncMessage.CallLogEvent.ADAPTER.decode(
          CallLogEventSendJobData.ADAPTER.decode(serializedData!!).callLogEvent.toByteArray()
        )
      )
    }
  }
}
