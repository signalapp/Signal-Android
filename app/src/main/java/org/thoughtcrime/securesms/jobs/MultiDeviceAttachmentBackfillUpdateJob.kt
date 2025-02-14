/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.attachments.toAttachmentPointer
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.MultiDeviceAttachmentBackfillUpdateJobData
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException
import org.whispersystems.signalservice.internal.push.AddressableMessage
import org.whispersystems.signalservice.internal.push.ConversationIdentifier
import org.whispersystems.signalservice.internal.push.SyncMessage
import org.whispersystems.signalservice.internal.push.SyncMessage.AttachmentBackfillResponse.AttachmentData
import java.io.IOException
import kotlin.time.Duration.Companion.days

/**
 * Tells linked devices about all the attachments that have been re-uploaded for a given [SyncMessage.attachmentBackfillRequest].
 */
class MultiDeviceAttachmentBackfillUpdateJob(
  parameters: Parameters,
  private val targetMessage: AddressableMessage,
  private val targetConversation: ConversationIdentifier,
  private val messageId: Long
) : Job(parameters) {

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

  override fun run(): Result {
    val attachments = SignalDatabase.attachments.getAttachmentsForMessage(messageId).filterNot { it.quote }.sortedBy { it.displayOrder }
    if (attachments.isEmpty()) {
      Log.w(TAG, "Failed to find any attachments for the message! Sending a missing response.")
      MultiDeviceAttachmentBackfillMissingJob.enqueue(targetMessage, targetConversation)
      return Result.failure()
    }

    val attachmentDatas = attachments.map { attachment ->
      when {
        attachment.hasData && !attachment.isInProgress && attachment.withinUploadThreshold() -> {
          AttachmentData(attachment = attachment.toAttachmentPointer(context))
        }
        !attachment.hasData || attachment.isPermanentlyFailed -> {
          AttachmentData(status = AttachmentData.Status.TERMINAL_ERROR)
        }
        else -> {
          AttachmentData(status = AttachmentData.Status.PENDING)
        }
      }
    }

    val syncMessage = SignalServiceSyncMessage.forAttachmentBackfillResponse(
      SyncMessage.AttachmentBackfillResponse(
        targetMessage = targetMessage,
        targetConversation = targetConversation,
        attachments = SyncMessage.AttachmentBackfillResponse.AttachmentDataList(
          attachments = attachmentDatas
        )
      )
    )

    return try {
      val result = AppDependencies.signalServiceMessageSender.sendSyncMessage(syncMessage)
      if (result.isSuccess) {
        Log.i(TAG, "[${targetMessage.sentTimestamp}] Successfully sent backfill update message response.")
        Result.success()
      } else {
        Log.w(TAG, "[${targetMessage.sentTimestamp}] Non-successful result. Retrying.")
        Result.retry(defaultBackoff())
      }
    } catch (e: ServerRejectedException) {
      Log.w(TAG, e)
      Result.failure()
    } catch (e: IOException) {
      Log.w(TAG, e)
      Result.retry(defaultBackoff())
    } catch (e: UntrustedIdentityException) {
      Log.w(TAG, e)
      Result.failure()
    }
  }

  override fun onFailure() {
    if (isCascadingFailure) {
      Log.w(TAG, "The upload job failed! Enqueuing another instance of the job to notify of the failure.")
      MultiDeviceAttachmentBackfillUpdateJob.enqueue(targetMessage, targetConversation, messageId)
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
