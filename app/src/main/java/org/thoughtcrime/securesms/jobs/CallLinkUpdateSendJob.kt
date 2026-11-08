/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.jobs.protos.CallLinkUpdateSendJobData
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.SyncMessage.CallLinkUpdate
import java.util.concurrent.TimeUnit

/**
 * Sends a [CallLinkUpdate] message to linked devices.
 */
class CallLinkUpdateSendJob private constructor(
  parameters: Parameters,
  private val callLinkRoomId: CallLinkRoomId,
  private val callLinkUpdateType: CallLinkUpdate.Type
) : CoroutineJob(parameters) {

  companion object {
    const val KEY = "CallLinkUpdateSendJob"
    private val TAG = Log.tag(CallLinkUpdateSendJob::class.java)
  }

  constructor(
    callLinkRoomId: CallLinkRoomId,
    callLinkUpdateType: CallLinkUpdate.Type = CallLinkUpdate.Type.UPDATE
  ) : this(
    Parameters.Builder()
      .setQueue("CallLinkUpdateSendJob")
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .build(),
    callLinkRoomId,
    callLinkUpdateType
  )

  override fun serialize(): ByteArray = CallLinkUpdateSendJobData.Builder()
    .callLinkRoomId(callLinkRoomId.serialize())
    .type(
      when (callLinkUpdateType) {
        CallLinkUpdate.Type.UPDATE -> CallLinkUpdateSendJobData.Type.UPDATE
      }
    )
    .build()
    .encode()

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override suspend fun doRun(): Result {
    val callLink = SignalDatabase.callLinks.getCallLinkByRoomId(callLinkRoomId)
    if (callLink?.credentials == null) {
      Log.i(TAG, "Call link not found or missing credentials. Exiting.")
      return Result.success()
    }

    val callLinkUpdate = CallLinkUpdate(
      rootKey = callLink.credentials.linkKeyBytes.toByteString(),
      adminPasskey = callLink.credentials.adminPassBytes?.toByteString(),
      type = callLinkUpdateType
    )

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncCallLinkUpdate(callLinkUpdate),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error ->
        error.toSyncJobResult(TAG, "call link update sync", defaultBackoff())
      }
    )
  }

  class Factory : Job.Factory<CallLinkUpdateSendJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CallLinkUpdateSendJob {
      val jobData = CallLinkUpdateSendJobData.ADAPTER.decode(serializedData!!)
      val type: CallLinkUpdate.Type = when (jobData.type) {
        CallLinkUpdateSendJobData.Type.UPDATE, null -> CallLinkUpdate.Type.UPDATE
      }

      return CallLinkUpdateSendJob(
        parameters,
        CallLinkRoomId.DatabaseSerializer.deserialize(jobData.callLinkRoomId),
        type
      )
    }
  }
}
