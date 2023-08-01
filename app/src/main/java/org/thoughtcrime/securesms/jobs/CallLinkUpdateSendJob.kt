/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import com.google.protobuf.ByteString
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.CallLinkUpdateSendJobData
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.util.FeatureFlags
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.CallLinkUpdate
import java.lang.Exception
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * Sends a [CallLinkUpdate] message to linked devices.
 */
class CallLinkUpdateSendJob private constructor(
  parameters: Parameters,
  private val callLinkRoomId: CallLinkRoomId
) : BaseJob(parameters) {

  companion object {
    const val KEY = "CallLinkUpdateSendJob"
    private val TAG = Log.tag(CallLinkUpdateSendJob::class.java)
  }

  constructor(
    callLinkRoomId: CallLinkRoomId
  ) : this(
    Parameters.Builder()
      .setQueue("CallLinkUpdateSendJob")
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .addConstraint(NetworkConstraint.KEY)
      .build(),
    callLinkRoomId
  )

  override fun serialize(): ByteArray = CallLinkUpdateSendJobData.Builder()
    .callLinkRoomId(callLinkRoomId.serialize())
    .build()
    .encode()

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onRun() {
    if (!FeatureFlags.adHocCalling()) {
      Log.i(TAG, "Call links are not enabled. Exiting.")
      return
    }

    val callLink = SignalDatabase.callLinks.getCallLinkByRoomId(callLinkRoomId)
    if (callLink?.credentials == null) {
      Log.i(TAG, "Call link not found or missing credentials. Exiting.")
      return
    }

    val callLinkUpdate = CallLinkUpdate.newBuilder()
      .setRootKey(ByteString.copyFrom(callLink.credentials.linkKeyBytes))
      .build()

    ApplicationDependencies.getSignalServiceMessageSender()
      .sendSyncMessage(SignalServiceSyncMessage.forCallLinkUpdate(callLinkUpdate), Optional.empty())
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return when (e) {
      is ServerRejectedException -> false
      is PushNetworkException -> true
      else -> false
    }
  }

  class Factory : Job.Factory<CallLinkUpdateSendJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CallLinkUpdateSendJob {
      return CallLinkUpdateSendJob(
        parameters,
        CallLinkRoomId.DatabaseSerializer.deserialize(CallLinkUpdateSendJobData.ADAPTER.decode(serializedData!!).callLinkRoomId)
      )
    }
  }
}
