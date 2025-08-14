package org.thoughtcrime.securesms.jobs

import kotlinx.coroutines.runBlocking
import org.signal.core.util.ByteSize
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.logW
import org.signal.libsignal.zkgroup.VerificationFailedException
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
import org.thoughtcrime.securesms.jobmanager.impl.NoRemoteArchiveGarbageCollectionPendingConstraint
import org.thoughtcrime.securesms.jobs.protos.CopyAttachmentToArchiveJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.NetworkResult
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
      .addConstraint(NoRemoteArchiveGarbageCollectionPendingConstraint.KEY)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .setQueue(UploadAttachmentToArchiveJob.QUEUES.random())
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
    if (SignalStore.account.isLinkedDevice) {
      Log.w(TAG, "[$attachmentId] Linked devices don't backup media. Skipping.")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      return Result.success()
    }

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

    if (SignalDatabase.messages.isStory(attachment.mmsId)) {
      Log.i(TAG, "[$attachmentId] Attachment is a story. Resetting transfer state to none and skipping.")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      return Result.success()
    }

    if (SignalDatabase.messages.isViewOnce(attachment.mmsId)) {
      Log.i(TAG, "[$attachmentId] Attachment is view-once. Resetting transfer state to none and skipping.")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      return Result.success()
    }

    if (SignalDatabase.messages.willMessageExpireBeforeCutoff(attachment.mmsId)) {
      Log.i(TAG, "[$attachmentId] Message will expire in less than 24 hours. Resetting transfer state to none and skipping.")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      return Result.success()
    }

    if (attachment.contentType == MediaUtil.LONG_TEXT) {
      Log.i(TAG, "[$attachmentId] Attachment is long text. Resetting transfer state to none and skipping.")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      return Result.success()
    }

    if (isCanceled) {
      Log.w(TAG, "[$attachmentId] Canceled. Refusing to proceed.")
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
            Log.w(TAG, "[$attachmentId] Insufficient permissions to upload. Handled in parent handler.")
            Result.success()
          }
          410 -> {
            Log.w(TAG, "[$attachmentId] The attachment no longer exists on the transit tier. Scheduling a re-upload.")
            SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
            AppDependencies.jobManager.add(UploadAttachmentToArchiveJob(attachmentId, canReuseUpload = false))
            Result.success()
          }
          413 -> {
            Log.w(TAG, "[$attachmentId] Insufficient storage space! Can't upload!")
            val remoteStorageQuota = getServerQuota() ?: return Result.retry(defaultBackoff()).logW(TAG, "[$attachmentId] Failed to fetch server quota! Retrying.")

            if (SignalDatabase.attachments.getEstimatedArchiveMediaSize() > remoteStorageQuota.inWholeBytes) {
              BackupRepository.markOutOfRemoteStorageSpaceError()
              return Result.failure()
            }

            Log.i(TAG, "[$attachmentId] Remote storage is full, but our local state indicates that once we reconcile our storage, we should have enough. Enqueuing the reconciliation job and retrying.")
            SignalStore.backup.remoteStorageGarbageCollectionPending = true
            AppDependencies.jobManager.add(ArchiveAttachmentReconciliationJob(forced = true))

            Result.retry(defaultBackoff())
          }
          else -> {
            Log.w(TAG, "[$attachmentId] Got back a non-2xx status code: ${archiveResult.code}. Retrying.")
            Result.retry(defaultBackoff())
          }
        }
      }

      is NetworkResult.ApplicationError -> {
        if (archiveResult.throwable is VerificationFailedException) {
          Log.w(TAG, "[$attachmentId] Encountered a verification failure when trying to upload! Retrying.")
          Result.retry(defaultBackoff())
        } else {
          Log.w(TAG, "[$attachmentId] Encountered a fatal error when trying to upload!")
          Result.fatalFailure(RuntimeException(archiveResult.throwable))
        }
      }
    }

    if (result.isSuccess) {
      Log.d(TAG, "[$attachmentId] Updating archive transfer state to ${AttachmentTable.ArchiveTransferState.FINISHED}")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)

      if (!isCanceled) {
        ArchiveThumbnailUploadJob.enqueueIfNecessary(attachmentId)
      } else {
        Log.d(TAG, "[$attachmentId] Refusing to enqueue thumb for canceled upload.")
      }

      ArchiveUploadProgress.onAttachmentFinished(attachmentId)
    }

    return result
  }

  private fun getServerQuota(): ByteSize? {
    return runBlocking {
      BackupRepository.getPaidType().successOrThrow()?.storageAllowanceBytes?.bytes
    }
  }

  override fun onFailure() {
    if (this.isCanceled) {
      Log.w(TAG, "[$attachmentId] Job was canceled, updating archive transfer state to ${AttachmentTable.ArchiveTransferState.COPY_PENDING}.")
      SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.COPY_PENDING)
    } else {
      Log.w(TAG, "[$attachmentId] Job failed, updating archive transfer state to ${AttachmentTable.ArchiveTransferState.TEMPORARY_FAILURE}.")
      SignalDatabase.attachments.setArchiveTransferStateFailure(attachmentId, AttachmentTable.ArchiveTransferState.TEMPORARY_FAILURE)
    }

    ArchiveUploadProgress.onAttachmentFinished(attachmentId)
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
