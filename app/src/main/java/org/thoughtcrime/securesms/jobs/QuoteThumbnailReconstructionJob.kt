/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.mms.DecryptableUri
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.getQuote
import kotlin.time.Duration.Companion.milliseconds

/**
 * A job that should be enqueued after a free-tier backup restore completes.
 * Before enqueueing this job, be sure to call [AttachmentTable.markQuotesThatNeedReconstruction].
 */
class QuoteThumbnailReconstructionJob private constructor(params: Parameters) : Job(params) {

  companion object {
    private val TAG = Log.tag(QuoteThumbnailReconstructionJob::class)

    const val KEY = "QuoteThumbnailReconstructionJob"
  }

  private var activeQuoteAttachment: DatabaseAttachment? = null

  constructor() : this(
    Parameters.Builder()
      .setLifespan(Parameters.IMMORTAL)
      .setMaxInstancesForFactory(2)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey() = KEY

  override fun run(): Result {
    val quoteAttachment = SignalDatabase.attachments.getNewestQuotePendingReconstruction()
    if (quoteAttachment == null) {
      Log.i(TAG, "No remaining quotes to reconstruct. Done!")
      return Result.success()
    }

    activeQuoteAttachment = quoteAttachment

    val message = SignalDatabase.messages.getMessageRecordOrNull(quoteAttachment.mmsId)
    if (message == null) {
      Log.w(TAG, "Failed to find message for quote attachment. Possible race condition where it was just deleted. Marking as migrated and continuing.")
      SignalDatabase.attachments.clearQuotePendingReconstruction(quoteAttachment.attachmentId)
      AppDependencies.jobManager.add(QuoteThumbnailReconstructionJob())
      return Result.success()
    }

    if (message.getQuote() == null) {
      Log.w(TAG, "The target message has no quote data. Marking as migrated and continuing.")
      SignalDatabase.attachments.clearQuotePendingReconstruction(quoteAttachment.attachmentId)
      AppDependencies.jobManager.add(QuoteThumbnailReconstructionJob())
      return Result.success()
    }

    val messageAge = System.currentTimeMillis().milliseconds - message.dateReceived.milliseconds
    if (messageAge > RemoteConfig.messageQueueTime.milliseconds) {
      Log.w(TAG, "Target message is older than the message queue time. Clearing remaining pending quotes and ending the reconstruction process.")
      SignalDatabase.attachments.clearAllQuotesPendingReconstruction()
      return Result.success()
    }

    val targetMessage = SignalDatabase.messages.getMessageFor(message.getQuote()!!.id, message.getQuote()!!.author)
    if (targetMessage == null) {
      Log.w(TAG, "Failed to find the target message of the quote. Marking as migrated and continuing.")
      SignalDatabase.attachments.clearQuotePendingReconstruction(quoteAttachment.attachmentId)
      AppDependencies.jobManager.add(QuoteThumbnailReconstructionJob())
      return Result.success()
    }

    val targetAttachment = SignalDatabase.attachments.getAttachmentsForMessage(targetMessage.id).firstOrNull { MediaUtil.isImageOrVideoType(it.contentType) && it.uri != null }
    if (targetAttachment == null) {
      Log.w(TAG, "No applicable attachments found for the target message. Marking as migrated and continuing.")
      SignalDatabase.attachments.clearQuotePendingReconstruction(quoteAttachment.attachmentId)
      AppDependencies.jobManager.add(QuoteThumbnailReconstructionJob())
      return Result.success()
    }

    val thumbnailData = SignalDatabase.attachments.generateQuoteThumbnail(DecryptableUri(targetAttachment.uri!!), targetAttachment.contentType, quiet = true)
    if (thumbnailData == null) {
      Log.w(TAG, "Failed to generate a thumbnail for the attachment. Marking as migrated and continuing.")
      SignalDatabase.attachments.clearQuotePendingReconstruction(quoteAttachment.attachmentId)
      AppDependencies.jobManager.add(QuoteThumbnailReconstructionJob())
      return Result.success()
    }

    SignalDatabase.attachments.applyReconstructedQuoteData(quoteAttachment.attachmentId, thumbnailData)
    Log.d(TAG, "Successfully reconstructed quote attachment for ${quoteAttachment.attachmentId}")

    AppDependencies.jobManager.add(QuoteThumbnailReconstructionJob())
    return Result.success()
  }

  override fun onFailure() {
    activeQuoteAttachment?.let { attachment ->
      Log.w(TAG, "Failed during reconstruction. Marking as migrated and continuing.", true)
      SignalDatabase.attachments.clearQuotePendingReconstruction(attachment.attachmentId)
    } ?: Log.w(TAG, "Job failed, but no active file is set!")

    AppDependencies.jobManager.add(QuoteThumbnailReconstructionJob())
  }

  class Factory : Job.Factory<QuoteThumbnailReconstructionJob> {
    override fun create(params: Parameters, data: ByteArray?): QuoteThumbnailReconstructionJob {
      return QuoteThumbnailReconstructionJob(params)
    }
  }
}
