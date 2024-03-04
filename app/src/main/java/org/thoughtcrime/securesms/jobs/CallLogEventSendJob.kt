/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.CallLogEventSendJobData
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * Sends CallLogEvents to synced devices.
 */
class CallLogEventSendJob private constructor(
  parameters: Parameters,
  private val callLogEvent: SyncMessage.CallLogEvent
) : BaseJob(parameters) {

  companion object {
    const val KEY = "CallLogEventSendJob"

    fun forClearHistory(
      timestamp: Long
    ) = CallLogEventSendJob(
      Parameters.Builder()
        .setQueue("CallLogEventSendJob")
        .setLifespan(TimeUnit.DAYS.toMillis(1))
        .setMaxAttempts(Parameters.UNLIMITED)
        .addConstraint(NetworkConstraint.KEY)
        .build(),
      SyncMessage.CallLogEvent(
        timestamp = timestamp,
        type = SyncMessage.CallLogEvent.Type.CLEAR
      )
    )

    fun forMarkedAsRead(
      timestamp: Long
    ) = CallLogEventSendJob(
      Parameters.Builder()
        .setQueue("CallLogEventSendJob")
        .setLifespan(TimeUnit.DAYS.toMillis(1))
        .setMaxAttempts(Parameters.UNLIMITED)
        .addConstraint(NetworkConstraint.KEY)
        .build(),
      SyncMessage.CallLogEvent(
        timestamp = timestamp,
        type = SyncMessage.CallLogEvent.Type.MARKED_AS_READ
      )
    )
  }

  override fun serialize(): ByteArray = CallLogEventSendJobData.Builder()
    .callLogEvent(callLogEvent.encodeByteString())
    .build()
    .encode()

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onRun() {
    ApplicationDependencies.getSignalServiceMessageSender()
      .sendSyncMessage(
        SignalServiceSyncMessage.forCallLogEvent(callLogEvent),
        Optional.empty()
      )
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return when (e) {
      is ServerRejectedException -> false
      is PushNetworkException -> true
      else -> false
    }
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
