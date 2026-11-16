/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.attachments.toAttachmentPointer
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.jobs.protos.MultiDeviceAttachmentBackfillUpdateJobData
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.AddressableMessage
import org.whispersystems.signalservice.internal.push.ConversationIdentifier
import org.whispersystems.signalservice.internal.push.SyncMessage
import org.whispersystems.signalservice.internal.push.SyncMessage.AttachmentBackfillResponse.AttachmentData
import kotlin.time.Duration.Companion.days

/**
 * Tells linked devices about all the attachments that have been re-uploaded for a given [SyncMessage.attachmentBackfillRequest].
 */
class MultiDeviceAttachmentBackfillUpdateJob(
  parameters: Parameters,
  private val targetMessage: AddressableMessage,
  private val targetConversation: ConversationIdentifier,
  private val messageId: Long
) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(MultiDeviceAttachmentBackfillUpdateJob::class)

    const val KEY = "MultiDeviceAttachmentBackfillUpdateJob"

    private val JOB_LIFESPAN = 1.days.inWholeMilliseconds
    private val UPLOAD_THRESHOLD = AttachmentUploadJob.UPLOAD_REUSE_THRESHOLD + JOB_LIFESPAN

    fun enqueue(targetMessage: AddressableMessage, targetConversation: ConversationIdentifier, messageId: Long) {
      AppDependencies.jobManager.add(MultiDeviceAttachmentBackfillUpdateJob(targetMessage, targetConversation, messageId))
    }
  }

  constructor(
    targetMessage: AddressableMessage,
    targetConversation: ConversationIdentifier,
    messageId: Long
  ) : this(
    Parameters.Builder()
      .setLifespan(JOB_LIFESPAN)
      .setMaxAttempts(Parameters.UNLIMITED)
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .build(),
    targetMessage,
    targetConversation,
    messageId
  )

  override fun getFactoryKey(): String = KEY

  override fun serialize(): ByteArray {
    return MultiDeviceAttachmentBackfillUpdateJobData(
      targetMessage = targetMessage,
      targetConversation = targetConversation,
      messageId = messageId
    ).encode()
  }

  override suspend fun doRun(): Result {
    val contract = AttachmentBackfill.backfillContractForMessage(messageId)
    val syncAttachments: List<DatabaseAttachment> = contract.bodyAttachments
    val longTextAttachment: DatabaseAttachment? = contract.longTextAttachment

    if (syncAttachments.isEmpty() && longTextAttachment == null) {
      Log.w(TAG, "Failed to find any attachments for the message! Sending a missing response.")
      MultiDeviceAttachmentBackfillMissingJob.enqueue(targetMessage, targetConversation)
      return Result.failure()
    }

    val response = SyncMessage.AttachmentBackfillResponse(
      targetMessage = targetMessage,
      targetConversation = targetConversation,
      attachments = SyncMessage.AttachmentBackfillResponse.AttachmentDataList(
        attachments = syncAttachments.map { it.toAttachmentData() },
        longText = longTextAttachment?.toAttachmentData()
      )
    )

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncAttachmentBackfillResponse(response),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = {
        Log.i(TAG, "[${targetMessage.sentTimestamp}] Successfully sent backfill update message response.")
        Result.success()
      },
      ifLeft = { error ->
        error.toSyncJobResult(TAG, "attachment backfill update response", defaultBackoff())
      }
    )
  }

  override fun onFailure() {
    if (isCascadingFailure) {
      Log.w(TAG, "The upload job failed! Enqueuing another instance of the job to notify of the failure.")
      MultiDeviceAttachmentBackfillUpdateJob.enqueue(targetMessage, targetConversation, messageId)
    }
  }

  private fun DatabaseAttachment.toAttachmentData(): AttachmentData {
    return when {
      this.hasData && !this.isInProgress && this.withinUploadThreshold() -> {
        AttachmentData(attachment = this.toAttachmentPointer(context))
      }
      !this.hasData || this.isPermanentlyFailed -> {
        AttachmentData(status = AttachmentData.Status.TERMINAL_ERROR)
      }
      else -> {
        AttachmentData(status = AttachmentData.Status.PENDING)
      }
    }
  }

  private fun DatabaseAttachment.withinUploadThreshold(): Boolean {
    return this.uploadTimestamp > 0 && System.currentTimeMillis() - this.uploadTimestamp < UPLOAD_THRESHOLD
  }

  class Factory : Job.Factory<MultiDeviceAttachmentBackfillUpdateJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceAttachmentBackfillUpdateJob {
      val data = MultiDeviceAttachmentBackfillUpdateJobData.ADAPTER.decode(serializedData!!)
      return MultiDeviceAttachmentBackfillUpdateJob(
        parameters,
        data.targetMessage!!,
        data.targetConversation!!,
        data.messageId
      )
    }
  }
}
