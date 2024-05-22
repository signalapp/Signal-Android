/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.greenrobot.eventbus.EventBus
import org.signal.core.util.logging.Log
import org.signal.protos.resumableuploads.ResumableUpload
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.AttachmentUploadUtil
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.BackupV2Event
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.ArchiveAttachmentBackfillJobData
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.archive.ArchiveMediaResponse
import org.whispersystems.signalservice.api.archive.ArchiveMediaUploadFormStatusCodes
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import java.io.IOException
import java.util.Optional
import kotlin.time.Duration.Companion.days

/**
 * When run, this will find the next attachment that needs to be uploaded to the archive service and upload it.
 * It will enqueue a copy of itself if it thinks there is more work to be done, and that copy will continue the upload process.
 */
class ArchiveAttachmentBackfillJob private constructor(
  parameters: Parameters,
  private var attachmentId: AttachmentId?,
  private var uploadSpec: ResumableUpload?,
  private var totalCount: Int?,
  private var progress: Int?
) : Job(parameters) {
  companion object {
    private val TAG = Log.tag(ArchiveAttachmentBackfillJob::class.java)

    const val KEY = "ArchiveAttachmentBackfillJob"
  }

  constructor(progress: Int? = null, totalCount: Int? = null) : this(
    parameters = Parameters.Builder()
      .setQueue("ArchiveAttachmentBackfillJob")
      .setMaxInstancesForQueue(2)
      .setLifespan(30.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .addConstraint(NetworkConstraint.KEY)
      .build(),
    attachmentId = null,
    uploadSpec = null,
    totalCount = totalCount,
    progress = progress
  )

  override fun serialize(): ByteArray {
    return ArchiveAttachmentBackfillJobData(
      attachmentId = attachmentId?.id,
      uploadSpec = uploadSpec
    ).encode()
  }

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    EventBus.getDefault().postSticky(BackupV2Event(BackupV2Event.Type.PROGRESS_ATTACHMENTS, progress?.toLong() ?: 0, totalCount?.toLong() ?: 0))
    var attachmentRecord: DatabaseAttachment? = if (attachmentId != null) {
      Log.i(TAG, "Retrying $attachmentId")
      SignalDatabase.attachments.getAttachment(attachmentId!!)
    } else {
      SignalDatabase.attachments.getNextAttachmentToArchiveAndMarkUploadInProgress()
    }

    if (attachmentRecord == null && attachmentId != null) {
      Log.w(TAG, "Attachment $attachmentId was not found! Was likely deleted during the process of archiving. Re-enqueuing job with no ID.")
      reenqueueWithIncrementedProgress()
      return Result.success()
    }

    // TODO [backup] If we ever wanted to allow multiple instances of this job to run in parallel, this would have to be done somewhere else
    if (attachmentRecord == null) {
      Log.i(TAG, "No more attachments to backfill! Ensuring there's no dangling state.")

      val resetCount = SignalDatabase.attachments.resetPendingArchiveBackfills()
      if (resetCount > 0) {
        Log.w(TAG, "We thought we were done, but $resetCount items were still in progress! Need to run again to retry.")
        AppDependencies.jobManager.add(
          ArchiveAttachmentBackfillJob(
            progress = (totalCount ?: resetCount) - resetCount,
            totalCount = totalCount ?: resetCount
          )
        )
      } else {
        Log.i(TAG, "All good! Should be done.")
      }
      EventBus.getDefault().postSticky(BackupV2Event(type = BackupV2Event.Type.FINISHED, count = totalCount?.toLong() ?: 0, estimatedTotalCount = totalCount?.toLong() ?: 0))
      return Result.success()
    }

    attachmentId = attachmentRecord.attachmentId

    val transferState: AttachmentTable.ArchiveTransferState? = SignalDatabase.attachments.getArchiveTransferState(attachmentRecord.attachmentId)
    if (transferState == null) {
      Log.w(TAG, "Attachment $attachmentId was not found when looking for the transfer state! Was likely just deleted. Re-enqueuing job with no ID.")
      reenqueueWithIncrementedProgress()
      return Result.success()
    }

    Log.i(TAG, "Current state: $transferState")

    if (transferState == AttachmentTable.ArchiveTransferState.FINISHED) {
      Log.i(TAG, "Attachment $attachmentId is already finished. Skipping.")
      reenqueueWithIncrementedProgress()
      return Result.success()
    }

    if (transferState == AttachmentTable.ArchiveTransferState.PERMANENT_FAILURE) {
      Log.i(TAG, "Attachment $attachmentId is already marked as a permanent failure. Skipping.")
      reenqueueWithIncrementedProgress()
      return Result.success()
    }

    if (transferState == AttachmentTable.ArchiveTransferState.ATTACHMENT_TRANSFER_PENDING) {
      Log.i(TAG, "Attachment $attachmentId is already marked as pending transfer, meaning it's a send attachment that will be uploaded on it's own. Skipping.")
      reenqueueWithIncrementedProgress()
      return Result.success()
    }

    if (transferState == AttachmentTable.ArchiveTransferState.BACKFILL_UPLOAD_IN_PROGRESS) {
      if (uploadSpec == null || System.currentTimeMillis() > uploadSpec!!.timeout) {
        Log.d(TAG, "Need an upload spec. Fetching...")

        val (spec, result) = fetchResumableUploadSpec()
        if (result != null) {
          return result
        }
        uploadSpec = spec
      } else {
        Log.d(TAG, "Already have an upload spec. Continuing...")
      }

      val attachmentStream = try {
        AttachmentUploadUtil.buildSignalServiceAttachmentStream(
          context = context,
          attachment = attachmentRecord,
          uploadSpec = uploadSpec!!,
          cancellationSignal = { this.isCanceled }
        )
      } catch (e: IOException) {
        Log.e(TAG, "Failed to get attachment stream for $attachmentId", e)
        return Result.retry(defaultBackoff())
      }

      Log.d(TAG, "Beginning upload...")
      val remoteAttachment: SignalServiceAttachmentPointer = try {
        AppDependencies.signalServiceMessageSender.uploadAttachment(attachmentStream)
      } catch (e: IOException) {
        Log.w(TAG, "Failed to upload $attachmentId", e)
        return Result.retry(defaultBackoff())
      }
      Log.d(TAG, "Upload complete!")

      val pointerAttachment: Attachment = PointerAttachment.forPointer(Optional.of(remoteAttachment), null, attachmentRecord.fastPreflightId).get()
      SignalDatabase.attachments.finalizeAttachmentAfterUpload(attachmentRecord.attachmentId, pointerAttachment, remoteAttachment.uploadTimestamp)
      SignalDatabase.attachments.setArchiveTransferState(attachmentRecord.attachmentId, AttachmentTable.ArchiveTransferState.BACKFILL_UPLOADED)

      attachmentRecord = SignalDatabase.attachments.getAttachment(attachmentRecord.attachmentId)
    }

    if (attachmentRecord == null) {
      Log.w(TAG, "$attachmentId was not found after uploading! Possibly deleted in a narrow race condition. Re-enqueuing job with no ID.")
      reenqueueWithIncrementedProgress()
      return Result.success()
    }

    Log.d(TAG, "Moving attachment to archive...")
    return when (val result = BackupRepository.archiveMedia(attachmentRecord)) {
      is NetworkResult.Success -> {
        Log.d(TAG, "Move complete!")

        SignalDatabase.attachments.setArchiveTransferState(attachmentRecord.attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)
        ArchiveThumbnailUploadJob.enqueueIfNecessary(attachmentRecord.attachmentId)
        reenqueueWithIncrementedProgress()
        Result.success()
      }

      is NetworkResult.ApplicationError -> {
        Log.w(TAG, "Failed to archive ${attachmentRecord.attachmentId} due to an application error. Retrying.", result.throwable)
        Result.retry(defaultBackoff())
      }

      is NetworkResult.NetworkError -> {
        Log.w(TAG, "Encountered a transient network error. Retrying.")
        Result.retry(defaultBackoff())
      }

      is NetworkResult.StatusCodeError -> {
        Log.w(TAG, "Failed request with status code ${result.code} for ${attachmentRecord.attachmentId}")

        when (ArchiveMediaResponse.StatusCodes.from(result.code)) {
          ArchiveMediaResponse.StatusCodes.BadArguments,
          ArchiveMediaResponse.StatusCodes.InvalidPresentationOrSignature,
          ArchiveMediaResponse.StatusCodes.InsufficientPermissions,
          ArchiveMediaResponse.StatusCodes.RateLimited -> {
            Result.retry(defaultBackoff())
          }

          ArchiveMediaResponse.StatusCodes.NoMediaSpaceRemaining -> {
            // TODO [backup] This will end the process right away. We need to integrate this with client-driven retry UX.
            Result.failure()
          }

          ArchiveMediaResponse.StatusCodes.Unknown -> {
            Result.retry(defaultBackoff())
          }
        }
      }
    }
  }

  private fun reenqueueWithIncrementedProgress() {
    AppDependencies.jobManager.add(
      ArchiveAttachmentBackfillJob(
        totalCount = totalCount,
        progress = progress?.inc()?.coerceAtMost(totalCount ?: 0)
      )
    )
  }

  override fun onFailure() {
    attachmentId?.let { id ->
      Log.w(TAG, "Failed to archive $id!")
    }
  }

  private fun fetchResumableUploadSpec(): Pair<ResumableUpload?, Result?> {
    return when (val spec = BackupRepository.getMediaUploadSpec()) {
      is NetworkResult.Success -> {
        Log.d(TAG, "Got an upload spec!")
        spec.result.toProto() to null
      }

      is NetworkResult.ApplicationError -> {
        Log.w(TAG, "Failed to get an upload spec due to an application error. Retrying.", spec.throwable)
        return null to Result.retry(defaultBackoff())
      }

      is NetworkResult.NetworkError -> {
        Log.w(TAG, "Encountered a transient network error. Retrying.")
        return null to Result.retry(defaultBackoff())
      }

      is NetworkResult.StatusCodeError -> {
        Log.w(TAG, "Failed request with status code ${spec.code}")

        when (ArchiveMediaUploadFormStatusCodes.from(spec.code)) {
          ArchiveMediaUploadFormStatusCodes.BadArguments,
          ArchiveMediaUploadFormStatusCodes.InvalidPresentationOrSignature,
          ArchiveMediaUploadFormStatusCodes.InsufficientPermissions,
          ArchiveMediaUploadFormStatusCodes.RateLimited -> {
            return null to Result.retry(defaultBackoff())
          }

          ArchiveMediaUploadFormStatusCodes.Unknown -> {
            return null to Result.retry(defaultBackoff())
          }
        }
      }
    }
  }

  class Factory : Job.Factory<ArchiveAttachmentBackfillJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ArchiveAttachmentBackfillJob {
      val data = serializedData?.let { ArchiveAttachmentBackfillJobData.ADAPTER.decode(it) }

      return ArchiveAttachmentBackfillJob(
        parameters = parameters,
        attachmentId = data?.attachmentId?.let { AttachmentId(it) },
        uploadSpec = data?.uploadSpec,
        totalCount = data?.totalCount,
        progress = data?.count
      )
    }
  }
}
