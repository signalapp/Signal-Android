package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.CopyAttachmentToArchiveJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit

/**
 * Copies and re-encrypts attachments from the attachment cdn to the archive cdn.
 * If it's discovered that the attachment no longer exists on the attachment cdn, this job will schedule a re-upload via [UploadAttachmentToArchiveJob].
 *
 * This job runs at high priority within its queue, which it shares with [UploadAttachmentToArchiveJob]. Therefore, copies are given priority over new uploads,
 * which allows the two-part archive upload process to finish quickly.
 */
class CopyAttachmentToArchiveJob private constructor(private val attachmentId: AttachmentId, parameters: Parameters) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(CopyAttachmentToArchiveJob::class.java)

    const val KEY = "CopyAttachmentToArchiveJob"

    /** CDNs that we can copy data from */
    val ALLOWED_SOURCE_CDNS = setOf(Cdn.CDN_2, Cdn.CDN_3)
  }

  constructor(attachmentId: AttachmentId) : this(
    attachmentId = attachmentId,
    parameters = Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .setQueue(UploadAttachmentToArchiveJob.buildQueueKey())
      .setQueuePriority(Parameters.PRIORITY_HIGH)
      .build()
  )

  override fun serialize(): ByteArray = CopyAttachmentToArchiveJobData(
    attachmentId = attachmentId.id
  ).encode()

  override fun getFactoryKey(): String = KEY

  override fun onAdded() {
    val transferStatus = SignalDatabase.attachments.getArchiveTransferState(attachmentId) ?: return

    if (transferStatus == AttachmentTable.ArchiveTransferState.NONE || transferStatus == AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS) {
      Log.d(TAG, "[$attachmentId] Updating archive transfer state to ${AttachmentTable.ArchiveTransferState.COPY_PENDING}")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.COPY_PENDING)
    }
  }

  override fun run(): Result {
    if (!SignalStore.backup.backsUpMedia) {
      Log.w(TAG, "[$attachmentId] This user does not back up media. Skipping.")
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

    if (attachment.archiveTransferState == AttachmentTable.ArchiveTransferState.NONE) {
      Log.i(TAG, "[$attachmentId] Not marked as pending copy. Enqueueing an upload job instead.")
      AppDependencies.jobManager.add(UploadAttachmentToArchiveJob(attachmentId))
      return Result.success()
    }

    val result = when (val archiveResult = BackupRepository.copyAttachmentToArchive(attachment)) {
      is NetworkResult.Success -> {
        Log.i(TAG, "[$attachmentId] Successfully copied the archive tier.")
        Result.success()
      }

      is NetworkResult.NetworkError -> {
        Log.w(TAG, "[$attachmentId] Encountered a retryable network error.", archiveResult.exception)
        Result.retry(defaultBackoff())
      }

      is NetworkResult.StatusCodeError -> {
        when (archiveResult.code) {
          403 -> {
            // TODO [backup] What is the best way to handle this UX-wise?
            Log.w(TAG, "[$attachmentId] Insufficient permissions to upload. Is the user no longer on media tier?")
            Result.success()
          }
          410 -> {
            Log.w(TAG, "[$attachmentId] The attachment no longer exists on the transit tier. Scheduling a re-upload.")
            SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
            AppDependencies.jobManager.add(UploadAttachmentToArchiveJob(attachmentId))
            Result.success()
          }
          413 -> {
            // TODO [backup] What is the best way to handle this UX-wise?
            Log.w(TAG, "[$attachmentId] Insufficient storage space! Can't upload!")
            Result.success()
          }
          else -> {
            Log.w(TAG, "[$attachmentId] Got back a non-2xx status code: ${archiveResult.code}. Retrying.")
            Result.retry(defaultBackoff())
          }
        }
      }

      is NetworkResult.ApplicationError -> {
        Log.w(TAG, "[$attachmentId] Encountered a fatal error when trying to upload!")
        Result.fatalFailure(RuntimeException(archiveResult.throwable))
      }
    }

    if (result.isSuccess) {
      Log.d(TAG, "[$attachmentId] Updating archive transfer state to ${AttachmentTable.ArchiveTransferState.FINISHED}")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)

      ArchiveThumbnailUploadJob.enqueueIfNecessary(attachmentId)
      SignalStore.backup.usedBackupMediaSpace += AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(attachment.size))

      ArchiveUploadProgress.onAttachmentFinished()
    }

    return result
  }

  override fun onFailure() {
    if (this.isCanceled) {
      Log.w(TAG, "[$attachmentId] Job was canceled, updating archive transfer state to ${AttachmentTable.ArchiveTransferState.COPY_PENDING}.")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.COPY_PENDING)
    } else {
      Log.w(TAG, "[$attachmentId] Job failed, updating archive transfer state to ${AttachmentTable.ArchiveTransferState.TEMPORARY_FAILURE}.")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.TEMPORARY_FAILURE)
    }

    ArchiveUploadProgress.onAttachmentFinished()
  }

  class Factory : Job.Factory<CopyAttachmentToArchiveJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CopyAttachmentToArchiveJob {
      val jobData = CopyAttachmentToArchiveJobData.ADAPTER.decode(serializedData!!)
      return CopyAttachmentToArchiveJob(
        attachmentId = AttachmentId(jobData.attachmentId),
        parameters = parameters
      )
    }
  }
}
