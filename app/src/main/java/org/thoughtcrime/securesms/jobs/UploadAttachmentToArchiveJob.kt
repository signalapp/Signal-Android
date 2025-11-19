/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.Base64
import org.signal.core.util.inRoundedDays
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.core.util.readLength
import org.signal.protos.resumableuploads.ResumableUpload
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.AttachmentUploadUtil
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.backup.v2.ArchiveDatabaseExecutor
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.BackupMessagesConstraint
import org.thoughtcrime.securesms.jobs.protos.UploadAttachmentToArchiveJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.service.AttachmentProgressService
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.archive.ArchiveMediaUploadFormStatusCodes
import org.whispersystems.signalservice.api.attachment.AttachmentUploadResult
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ProtocolException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Given an attachmentId, this will upload the corresponding attachment to the archive cdn.
 * To do this, it must first upload it to the attachment cdn, and then copy it to the archive cdn.
 */
class UploadAttachmentToArchiveJob private constructor(
  private val attachmentId: AttachmentId,
  private var uploadSpec: ResumableUpload?,
  private val canReuseUpload: Boolean,
  parameters: Parameters
) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(UploadAttachmentToArchiveJob::class)
    const val KEY = "UploadAttachmentToArchiveJob"

    /** A set of possible queues this job may use. The number of queues determines the parallelism. */
    val QUEUES = setOf(
      "ArchiveAttachmentJobs_01",
      "ArchiveAttachmentJobs_02",
      "ArchiveAttachmentJobs_03",
      "ArchiveAttachmentJobs_04",
      "ArchiveAttachmentJobs_05",
      "ArchiveAttachmentJobs_06",
      "ArchiveAttachmentJobs_07",
      "ArchiveAttachmentJobs_08",
      "ArchiveAttachmentJobs_09",
      "ArchiveAttachmentJobs_10",
      "ArchiveAttachmentJobs_11",
      "ArchiveAttachmentJobs_12"
    )
  }

  constructor(attachmentId: AttachmentId, canReuseUpload: Boolean = true) : this(
    attachmentId = attachmentId,
    uploadSpec = null,
    canReuseUpload = canReuseUpload,
    parameters = Parameters.Builder()
      .addConstraint(BackupMessagesConstraint.KEY)
      .setLifespan(30.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setQueue(QUEUES.random())
      .setGlobalPriority(Parameters.PRIORITY_LOW)
      .build()
  )

  override fun serialize(): ByteArray = UploadAttachmentToArchiveJobData(
    attachmentId = attachmentId.id,
    uploadSpec = uploadSpec,
    canReuseUpload = canReuseUpload
  ).encode()

  override fun getFactoryKey(): String = KEY

  override fun onAdded() {
    val transferStatus = SignalDatabase.attachments.getArchiveTransferState(attachmentId) ?: return

    if (transferStatus == AttachmentTable.ArchiveTransferState.NONE) {
      Log.d(TAG, "[$attachmentId] Updating archive transfer state to ${AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS}")
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.setArchiveTransferStateUnlessPermanentFailure(attachmentId, AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS)
      }
    }
  }

  override fun run(): Result {
    // TODO [cody] Remove after a few releases as we migrate to the correct constraint
    if (!BackupMessagesConstraint.isMet(context)) {
      return Result.failure()
    }

    if (SignalStore.account.isLinkedDevice) {
      Log.w(TAG, "[$attachmentId] Linked devices don't backup media. Skipping.")
      ArchiveDatabaseExecutor.runBlocking {
        setArchiveTransferStateWithDelayedNotification(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      }
      return Result.success()
    }

    if (!SignalStore.backup.backsUpMedia) {
      Log.w(TAG, "[$attachmentId] This user does not back up media. Skipping.")
      ArchiveDatabaseExecutor.runBlocking {
        setArchiveTransferStateWithDelayedNotification(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      }
      return Result.success()
    }

    val attachment: DatabaseAttachment? = SignalDatabase.attachments.getAttachment(attachmentId)

    if (attachment == null) {
      Log.w(TAG, "[$attachmentId] Attachment no longer exists! Skipping.")
      return Result.failure()
    }

    if (attachment.uri == null) {
      Log.w(TAG, "[$attachmentId] Attachment has no uri! Cannot upload.")
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

    if (SignalDatabase.messages.isStory(attachment.mmsId)) {
      Log.i(TAG, "[$attachmentId] Attachment is a story. Resetting transfer state to none and skipping.")
      ArchiveDatabaseExecutor.runBlocking {
        setArchiveTransferStateWithDelayedNotification(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      }
      return Result.success()
    }

    if (SignalDatabase.messages.isViewOnce(attachment.mmsId)) {
      Log.i(TAG, "[$attachmentId] Attachment is a view-once. Resetting transfer state to none and skipping.")
      ArchiveDatabaseExecutor.runBlocking {
        setArchiveTransferStateWithDelayedNotification(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      }
      return Result.success()
    }

    if (SignalDatabase.messages.willMessageExpireBeforeCutoff(attachment.mmsId)) {
      Log.i(TAG, "[$attachmentId] Message will expire within 24 hours. Resetting transfer state to none and skipping.")
      ArchiveDatabaseExecutor.runBlocking {
        setArchiveTransferStateWithDelayedNotification(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      }
      return Result.success()
    }

    if (attachment.contentType == MediaUtil.LONG_TEXT) {
      Log.i(TAG, "[$attachmentId] Attachment is long text. Resetting transfer state to none and skipping.")
      ArchiveDatabaseExecutor.runBlocking {
        setArchiveTransferStateWithDelayedNotification(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      }
      return Result.success()
    }

    if (attachment.remoteKey == null || attachment.remoteKey.isBlank()) {
      Log.w(TAG, "[$attachmentId] Attachment is missing remote key! Cannot upload.")
      return Result.failure()
    }

    val timeSinceUpload = System.currentTimeMillis() - attachment.uploadTimestamp
    if (canReuseUpload && timeSinceUpload > 0 && timeSinceUpload < AttachmentUploadJob.UPLOAD_REUSE_THRESHOLD && attachment.remoteLocation.isNotNullOrBlank()) {
      Log.i(TAG, "We can copy an already-uploaded file. It was uploaded $timeSinceUpload ms (${timeSinceUpload.milliseconds.inRoundedDays()} days) ago. Skipping.")
      AppDependencies.jobManager.add(CopyAttachmentToArchiveJob(attachment.attachmentId))
      return Result.success()
    }

    if (uploadSpec != null && System.currentTimeMillis() > uploadSpec!!.timeout) {
      Log.w(TAG, "[$attachmentId] Upload spec expired! Clearing.")
      uploadSpec = null
    }

    if (uploadSpec == null) {
      Log.d(TAG, "[$attachmentId] Need an upload spec. Fetching...")

      val (spec, result) = fetchResumableUploadSpec(key = Base64.decode(attachment.remoteKey), iv = Util.getSecretBytes(16))
      if (result != null) {
        return result
      }

      uploadSpec = spec
    } else {
      Log.d(TAG, "[$attachmentId] Already have an upload spec. Continuing...")
    }

    val progressServiceController = if (attachment.size >= AttachmentUploadUtil.FOREGROUND_LIMIT_BYTES) {
      AttachmentProgressService.start(context, context.getString(R.string.UploadAttachmentToArchiveJob_uploading_media))
    } else {
      null
    }

    ArchiveUploadProgress.onAttachmentStarted(attachmentId, attachment.size)

    val attachmentStream = try {
      AttachmentUploadUtil.buildSignalServiceAttachmentStream(
        context = context,
        attachment = attachment,
        uploadSpec = uploadSpec!!,
        cancellationSignal = { this.isCanceled },
        progressListener = object : SignalServiceAttachment.ProgressListener {
          override fun onAttachmentProgress(progress: AttachmentTransferProgress) {
            ArchiveUploadProgress.onAttachmentProgress(attachmentId, progress.transmitted.inWholeBytes)
            progressServiceController?.updateProgress(progress.value)
          }

          override fun shouldCancel() = this@UploadAttachmentToArchiveJob.isCanceled
        }
      )
    } catch (e: FileNotFoundException) {
      Log.w(TAG, "[$attachmentId] No file exists for this attachment! Marking as a permanent failure.", e)
      ArchiveDatabaseExecutor.runBlocking {
        setArchiveTransferStateWithDelayedNotification(attachmentId, AttachmentTable.ArchiveTransferState.PERMANENT_FAILURE)
      }
      return Result.failure()
    } catch (e: IOException) {
      Log.w(TAG, "[$attachmentId] Failed while reading the stream.", e)
      return Result.retry(defaultBackoff())
    }

    Log.d(TAG, "[$attachmentId] Beginning upload...")
    progressServiceController.use {
      val uploadResult: AttachmentUploadResult = attachmentStream.use { managedAttachmentStream ->
        when (val result = SignalNetwork.attachments.uploadAttachmentV4(managedAttachmentStream)) {
          is NetworkResult.Success -> result.result
          is NetworkResult.ApplicationError -> throw result.throwable
          is NetworkResult.NetworkError -> {
            Log.w(TAG, "[$attachmentId] Failed to upload due to network error.", result.exception)

            if (result.exception.cause is ProtocolException) {
              Log.w(TAG, "[$attachmentId] Length may be incorrect. Recalculating.", result.exception)

              val actualLength = SignalDatabase.attachments.getAttachmentStream(attachmentId, 0)
                .use { it.readLength() }
              if (actualLength != attachment.size) {
                Log.w(TAG, "[$attachmentId] Length was incorrect! Will update. Previous: ${attachment.size}, Newly-Calculated: $actualLength", result.exception)
                ArchiveDatabaseExecutor.runBlocking {
                  SignalDatabase.attachments.updateAttachmentLength(attachmentId, actualLength)
                }
              } else {
                Log.i(TAG, "[$attachmentId] Length was correct. No action needed. Will retry.")
              }
            }

            return Result.retry(defaultBackoff())
          }

          is NetworkResult.StatusCodeError -> {
            Log.w(TAG, "[$attachmentId] Failed to upload due to status code error. Code: ${result.code}", result.exception)
            when (result.code) {
              400 -> {
                Log.w(TAG, "[$attachmentId] 400 likely means bad resumable state. Clearing upload spec before retrying.")
                uploadSpec = null
              }
            }
            return Result.retry(defaultBackoff())
          }
        }
      }

      Log.d(TAG, "[$attachmentId] Upload complete!")
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.finalizeAttachmentAfterUpload(attachment.attachmentId, uploadResult)
      }
    }

    if (!isCanceled) {
      AppDependencies.jobManager.add(CopyAttachmentToArchiveJob(attachment.attachmentId))
    } else {
      Log.d(TAG, "[$attachmentId] Job was canceled. Skipping copy job.")
    }

    return Result.success()
  }

  override fun onFailure() {
    if (this.isCanceled) {
      Log.w(TAG, "[$attachmentId] Job was canceled, updating archive transfer state to ${AttachmentTable.ArchiveTransferState.NONE}.")
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.setArchiveTransferStateFailure(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      }
    } else {
      Log.w(TAG, "[$attachmentId] Job failed, updating archive transfer state to ${AttachmentTable.ArchiveTransferState.TEMPORARY_FAILURE} (if not already a permanent failure).")
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.setArchiveTransferStateFailure(attachmentId, AttachmentTable.ArchiveTransferState.TEMPORARY_FAILURE)
      }
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

  private fun setArchiveTransferStateWithDelayedNotification(attachmentId: AttachmentId, transferState: AttachmentTable.ArchiveTransferState) {
    ArchiveDatabaseExecutor.runBlocking {
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, transferState, notify = false)
      ArchiveDatabaseExecutor.throttledNotifyAttachmentObservers()
    }
  }

  class Factory : Job.Factory<UploadAttachmentToArchiveJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): UploadAttachmentToArchiveJob {
      val data = UploadAttachmentToArchiveJobData.ADAPTER.decode(serializedData!!)
      return UploadAttachmentToArchiveJob(
        attachmentId = AttachmentId(data.attachmentId),
        uploadSpec = data.uploadSpec,
        canReuseUpload = data.canReuseUpload == true,
        parameters = parameters
      )
    }
  }
}
