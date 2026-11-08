/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.jobs.protos.MultiDeviceAttachmentBackfillMissingJobData
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.AddressableMessage
import org.whispersystems.signalservice.internal.push.ConversationIdentifier
import org.whispersystems.signalservice.internal.push.SyncMessage
import kotlin.time.Duration.Companion.days

/**
 * Tells linked devices that the requested message from a [SyncMessage.attachmentBackfillRequest] could not be found.
 */
class MultiDeviceAttachmentBackfillMissingJob(
  parameters: Parameters,
  private val targetMessage: AddressableMessage,
  private val targetConversation: ConversationIdentifier
) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(MultiDeviceAttachmentBackfillMissingJob::class)

    const val KEY = "MultiDeviceAttachmentBackfillMissingJob"

    fun enqueue(targetMessage: AddressableMessage, targetConversation: ConversationIdentifier) {
      AppDependencies.jobManager.add(MultiDeviceAttachmentBackfillMissingJob(targetMessage, targetConversation))
    }
  }

  constructor(targetMessage: AddressableMessage, targetConversation: ConversationIdentifier) : this(
    Parameters.Builder()
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .build(),
    targetMessage,
    targetConversation
  )

  override fun getFactoryKey(): String = KEY

  override fun serialize(): ByteArray {
    return MultiDeviceAttachmentBackfillMissingJobData(
      targetMessage = targetMessage,
      targetConversation = targetConversation
    ).encode()
  }

  override suspend fun doRun(): Result {
    val response = SyncMessage.AttachmentBackfillResponse(
      targetMessage = targetMessage,
      targetConversation = targetConversation,
      error = SyncMessage.AttachmentBackfillResponse.Error.MESSAGE_NOT_FOUND
    )

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncAttachmentBackfillResponse(response),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = {
        Log.i(TAG, "[${targetMessage.sentTimestamp}] Successfully sent backfill missing message response.")
        Result.success()
      },
      ifLeft = { error ->
        error.toSyncJobResult(TAG, "attachment backfill missing response", defaultBackoff())
      }
    )
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<MultiDeviceAttachmentBackfillMissingJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceAttachmentBackfillMissingJob {
      val data = MultiDeviceAttachmentBackfillMissingJobData.ADAPTER.decode(serializedData!!)
      return MultiDeviceAttachmentBackfillMissingJob(
        parameters,
        data.targetMessage!!,
        data.targetConversation!!
      )
    }
  }
}
