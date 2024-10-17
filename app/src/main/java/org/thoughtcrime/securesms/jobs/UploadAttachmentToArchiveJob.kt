/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.core.util.readLength
import org.signal.protos.resumableuploads.ResumableUpload
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.AttachmentUploadUtil
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.UploadAttachmentToArchiveJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.archive.ArchiveMediaUploadFormStatusCodes
import org.whispersystems.signalservice.api.attachment.AttachmentUploadResult
import java.io.IOException
import java.net.ProtocolException
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

/**
 * Given an attachmentId, this will upload the corresponding attachment to the archive cdn.
 * To do this, it must first upload it to the attachment cdn, and then copy it to the archive cdn.
 */
class UploadAttachmentToArchiveJob private constructor(
  private val attachmentId: AttachmentId,
  private var uploadSpec: ResumableUpload?,
  parameters: Parameters
) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(UploadAttachmentToArchiveJob::class)
    const val KEY = "UploadAttachmentToArchiveJob"

    /**
     * This randomly selects between one of two queues. It's a fun way of limiting the concurrency of the upload jobs to
     * take up at most two job runners.
     */
    fun buildQueueKey() = "ArchiveAttachmentJobs_${Random.nextInt(0, 2)}"
  }

  constructor(attachmentId: AttachmentId) : this(
    attachmentId = attachmentId,
    uploadSpec = null,
    parameters = Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setLifespan(30.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setQueue(buildQueueKey())
      .build()
  )

  override fun serialize(): ByteArray = UploadAttachmentToArchiveJobData(
    attachmentId = attachmentId.id
  ).encode()

  override fun getFactoryKey(): String = KEY

  override fun onAdded() {
    val transferStatus = SignalDatabase.attachments.getArchiveTransferState(attachmentId) ?: return

    if (transferStatus == AttachmentTable.ArchiveTransferState.NONE) {
      Log.d(TAG, "[$attachmentId] Updating archive transfer state to ${AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS}")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS)
    }
  }

  override fun run(): Result {
    if (!SignalStore.backup.backsUpMedia) {
      Log.w(TAG, "[$attachmentId] This user does not back up media. Skipping.")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      return Result.success()
    }

    val attachment: DatabaseAttachment? = SignalDatabase.attachments.getAttachment(attachmentId)

    if (attachment == null) {
      Log.w(TAG, "[$attachmentId] Attachment no longer exists! Skipping.")
      return Result.failure()
    }

    if (attachment.archiveTransferState == AttachmentTable.ArchiveTransferState.FINISHED) {
      Log.i(TAG, "[$attachmentId] Already finished. Skipping.")
      return Result.success()
    }

    if (attachment.archiveTransferState == AttachmentTable.ArchiveTransferState.PERMANENT_FAILURE) {
      Log.i(TAG, "[$attachmentId] Already marked as a permanent failure. Skipping.")
      return Result.failure()
    }

    if (attachment.archiveTransferState == AttachmentTable.ArchiveTransferState.COPY_PENDING) {
      Log.i(TAG, "[$attachmentId] Already marked as pending transfer. Enqueueing a copy job just in case.")
      AppDependencies.jobManager.add(CopyAttachmentToArchiveJob(attachment.attachmentId))
      return Result.success()
    }

    if (attachment.remoteKey == null || attachment.remoteIv == null) {
      Log.w(TAG, "[$attachmentId] Attachment is missing remote key or IV! Cannot upload.")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      return Result.failure()
    }

    if (uploadSpec != null && System.currentTimeMillis() > uploadSpec!!.timeout) {
      Log.w(TAG, "[$attachmentId] Upload spec expired! Clearing.")
      uploadSpec = null
    }

    if (uploadSpec == null) {
      Log.d(TAG, "[$attachmentId] Need an upload spec. Fetching...")

      val (spec, result) = fetchResumableUploadSpec(key = Base64.decode(attachment.remoteKey), iv = attachment.remoteIv)
      if (result != null) {
        return result
      }

      uploadSpec = spec
    } else {
      Log.d(TAG, "[$attachmentId] Already have an upload spec. Continuing...")
    }

    val attachmentStream = try {
      AttachmentUploadUtil.buildSignalServiceAttachmentStream(
        context = context,
        attachment = attachment,
        uploadSpec = uploadSpec!!,
        cancellationSignal = { this.isCanceled }
      )
    } catch (e: IOException) {
      Log.e(TAG, "[$attachmentId] Failed to get attachment stream.", e)
      return Result.retry(defaultBackoff())
    }

    Log.d(TAG, "[$attachmentId] Beginning upload...")
    val uploadResult: AttachmentUploadResult = when (val result = SignalNetwork.attachments.uploadAttachmentV4(attachmentStream)) {
      is NetworkResult.Success -> result.result
      is NetworkResult.ApplicationError -> throw result.throwable
      is NetworkResult.NetworkError -> {
        Log.w(TAG, "[$attachmentId] Failed to upload due to network error.", result.exception)

        if (result.exception.cause is ProtocolException) {
          Log.w(TAG, "[$attachmentId] Length may be incorrect. Recalculating.", result.exception)

          val actualLength = SignalDatabase.attachments.getAttachmentStream(attachmentId, 0).readLength()
          if (actualLength != attachment.size) {
            Log.w(TAG, "[$attachmentId] Length was incorrect! Will update. Previous: ${attachment.size}, Newly-Calculated: $actualLength", result.exception)
            SignalDatabase.attachments.updateAttachmentLength(attachmentId, actualLength)
          } else {
            Log.i(TAG, "[$attachmentId] Length was correct. No action needed. Will retry.")
          }
        }

        return Result.retry(defaultBackoff())
      }
      is NetworkResult.StatusCodeError -> {
        Log.w(TAG, "[$attachmentId] Failed to upload due to status code error. Code: ${result.code}", result.exception)
        return Result.retry(defaultBackoff())
      }
    }
    Log.d(TAG, "[$attachmentId] Upload complete!")

    SignalDatabase.attachments.finalizeAttachmentAfterUpload(attachment.attachmentId, uploadResult)

    AppDependencies.jobManager.add(CopyAttachmentToArchiveJob(attachment.attachmentId))

    return Result.success()
  }

  override fun onFailure() {
    if (this.isCanceled) {
      Log.w(TAG, "[$attachmentId] Job was canceled, updating archive transfer state to ${AttachmentTable.ArchiveTransferState.NONE}.")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
    } else {
      Log.w(TAG, "[$attachmentId] Job failed, updating archive transfer state to ${AttachmentTable.ArchiveTransferState.TEMPORARY_FAILURE}.")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.TEMPORARY_FAILURE)
    }
  }

  private fun fetchResumableUploadSpec(key: ByteArray, iv: ByteArray): Pair<ResumableUpload?, Result?> {
    val uploadSpec = BackupRepository
      .getAttachmentUploadForm()
      .then { form -> SignalNetwork.attachments.getResumableUploadSpec(key, iv, form) }

    return when (uploadSpec) {
      is NetworkResult.Success -> {
        Log.d(TAG, "[$attachmentId] Got an upload spec!")
        uploadSpec.result.toProto() to null
      }

      is NetworkResult.ApplicationError -> {
        Log.w(TAG, "[$attachmentId] Failed to get an upload spec due to an application error. Retrying.", uploadSpec.throwable)
        return null to Result.retry(defaultBackoff())
      }

      is NetworkResult.NetworkError -> {
        Log.w(TAG, "[$attachmentId] Encountered a transient network error. Retrying.")
        return null to Result.retry(defaultBackoff())
      }

      is NetworkResult.StatusCodeError -> {
        Log.w(TAG, "[$attachmentId] Failed request with status code ${uploadSpec.code}")

        when (ArchiveMediaUploadFormStatusCodes.from(uploadSpec.code)) {
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

  class Factory : Job.Factory<UploadAttachmentToArchiveJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): UploadAttachmentToArchiveJob {
      val data = UploadAttachmentToArchiveJobData.ADAPTER.decode(serializedData!!)
      return UploadAttachmentToArchiveJob(
        attachmentId = AttachmentId(data.attachmentId),
        uploadSpec = data.uploadSpec,
        parameters = parameters
      )
    }
  }
}
