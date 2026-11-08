/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.MultiDeviceSyncRequestJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.SyncMessage
import kotlin.time.Duration.Companion.days

/**
 * Sent by a linked device to ask the primary to send a sync message of the given [SyncMessage.Request.Type].
 */
class MultiDeviceSyncRequestJob private constructor(
  parameters: Parameters,
  private val type: SyncMessage.Request.Type
) : CoroutineJob(parameters) {

  companion object {
    const val KEY = "MultiDeviceSyncRequestJob"

    private val TAG = Log.tag(MultiDeviceSyncRequestJob::class.java)
  }

  constructor(type: SyncMessage.Request.Type) : this(
    Parameters.Builder()
      .setQueue(KEY)
      .addConstraint(NetworkConstraint.KEY)
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    type
  )

  override fun serialize(): ByteArray {
    return MultiDeviceSyncRequestJobData(type = type.value).encode()
  }

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (SignalStore.account.isPrimaryDevice) {
      Log.w(TAG, "Primary device should never request a sync. Dropping $type.")
      return Result.failure()
    }

    if (type == SyncMessage.Request.Type.UNKNOWN) {
      Log.w(TAG, "Unknown sync request type. Dropping.")
      return Result.failure()
    }

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncRequest(type),
      urgent = type == SyncMessage.Request.Type.CONTACTS || type == SyncMessage.Request.Type.KEYS,
      onEncrypted = null
    )

    return result.fold(
      ifRight = {
        Log.i(TAG, "Sent sync request for $type")
        Result.success()
      },
      ifLeft = { error ->
        error.toSyncJobResult(TAG, "sync request for $type", defaultBackoff())
      }
    )
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<MultiDeviceSyncRequestJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceSyncRequestJob {
      val data = MultiDeviceSyncRequestJobData.ADAPTER.decode(serializedData!!)
      return MultiDeviceSyncRequestJob(parameters, SyncMessage.Request.Type.fromValue(data.type) ?: SyncMessage.Request.Type.UNKNOWN)
    }
  }
}
