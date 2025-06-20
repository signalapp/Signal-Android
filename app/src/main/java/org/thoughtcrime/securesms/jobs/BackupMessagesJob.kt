/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.Stopwatch
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.protos.resumableuploads.ResumableUpload
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.backup.RestoreState
import org.thoughtcrime.securesms.backup.v2.ArchiveMediaItemIterator
import org.thoughtcrime.securesms.backup.v2.ArchiveValidator
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.ResumableMessagesBackupUploadSpec
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.WifiConstraint
import org.thoughtcrime.securesms.jobs.protos.BackupMessagesJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.isDecisionPending
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * Job that is responsible for exporting the DB as a backup proto and
 * also uploading the resulting proto.
 */
class BackupMessagesJob private constructor(
  private var syncTime: Long,
  private var dataFile: String,
  private var resumableMessagesBackupUploadSpec: ResumableMessagesBackupUploadSpec?,
  parameters: Parameters
) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(BackupMessagesJob::class.java)
    private val FILE_REUSE_TIMEOUT = 1.hours

    const val KEY = "BackupMessagesJob"

    private fun isBackupAllowed(): Boolean {
      return when {
        SignalStore.registration.restoreDecisionState.isDecisionPending -> {
          Log.i(TAG, "Backup not allowed: a restore decision is pending.")
          false
        }

        SignalStore.backup.restoreState == RestoreState.PENDING -> {
          Log.i(TAG, "Backup not allowed: a restore is pending.")
          false
        }

        SignalStore.backup.restoreState == RestoreState.RESTORING_DB -> {
          Log.i(TAG, "Backup not allowed: a restore is in progress.")
          false
        }

        else -> true
      }
    }

    fun enqueue() {
      if (!isBackupAllowed()) {
        Log.d(TAG, "Skip enqueueing BackupMessagesJob.")
        return
      }

      val jobManager = AppDependencies.jobManager

      val chain = jobManager.startChain(BackupMessagesJob())

      if (SignalStore.backup.optimizeStorage && SignalStore.backup.backsUpMedia) {
        chain.then(OptimizeMediaJob())
      }

      chain.enqueue()
    }
  }

  constructor() : this(
    syncTime = 0L,
    dataFile = "",
    resumableMessagesBackupUploadSpec = null,
    parameters = Parameters.Builder()
      .addConstraint(if (SignalStore.backup.backupWithCellular) NetworkConstraint.KEY else WifiConstraint.KEY)
      .setMaxAttempts(3)
      .setMaxInstancesForFactory(1)
      .setQueue(BackfillDigestJob.QUEUE) // We want to ensure digests have been backfilled before this runs. Could eventually remove this constraint.
      .build()
  )

  override fun serialize(): ByteArray = BackupMessagesJobData(
    syncTime = syncTime,
    dataFile = dataFile,
    resumableUri = resumableMessagesBackupUploadSpec?.resumableUri ?: "",
    uploadSpec = resumableMessagesBackupUploadSpec?.attachmentUploadForm?.toUploadSpec()
  ).encode()

  override fun getFactoryKey(): String = KEY

  override fun onAdded() {
    ArchiveUploadProgress.begin()
  }

  override fun onFailure() {
    if (!isCanceled) {
      Log.w(TAG, "Failed to backup user messages. Marking failure state.")
      BackupRepository.markBackupFailure()
    }
  }

  override fun run(): Result {
    if (!isBackupAllowed()) {
      Log.d(TAG, "Skip running BackupMessagesJob.")
      return Result.success()
    }

    val stopwatch = Stopwatch("BackupMessagesJob")

    SignalDatabase.attachments.createKeyIvDigestForAttachmentsThatNeedArchiveUpload().takeIf { it > 0 }?.let { count -> Log.w(TAG, "Needed to create $count key/iv/digests.") }
    stopwatch.split("key-iv-digest")

    if (isCanceled) {
      return Result.failure()
    }

    val (tempBackupFile, currentTime) = when (val generateBackupFileResult = getOrCreateBackupFile(stopwatch)) {
      is BackupFileResult.Success -> generateBackupFileResult
      BackupFileResult.Failure -> return Result.failure()
      BackupFileResult.Retry -> return Result.retry(defaultBackoff())
    }

    ArchiveUploadProgress.onMessageBackupCreated(tempBackupFile.length())

    this.syncTime = currentTime
    this.dataFile = tempBackupFile.path

    val backupSpec: ResumableMessagesBackupUploadSpec = resumableMessagesBackupUploadSpec ?: when (val result = BackupRepository.getResumableMessagesBackupUploadSpec()) {
      is NetworkResult.Success -> {
        Log.i(TAG, "Successfully generated a new upload spec.")

        val spec = result.result
        resumableMessagesBackupUploadSpec = spec
        spec
      }

      is NetworkResult.NetworkError -> {
        Log.i(TAG, "Network failure", result.getCause())
        return Result.retry(defaultBackoff())
      }

      is NetworkResult.StatusCodeError -> {
        Log.i(TAG, "Status code failure", result.getCause())
        return Result.retry(defaultBackoff())
      }

      is NetworkResult.ApplicationError -> throw result.throwable
    }

    val progressListener = object : SignalServiceAttachment.ProgressListener {
      override fun onAttachmentProgress(progress: AttachmentTransferProgress) {
        ArchiveUploadProgress.onMessageBackupUploadProgress(progress)
      }

      override fun shouldCancel(): Boolean = isCanceled
    }

    FileInputStream(tempBackupFile).use {
      when (val result = BackupRepository.uploadBackupFile(backupSpec, it, tempBackupFile.length(), progressListener)) {
        is NetworkResult.Success -> {
          Log.i(TAG, "Successfully uploaded backup file.")
          if (!SignalStore.backup.hasBackupBeenUploaded) {
            Log.i(TAG, "First time making a backup - scheduling a storage sync.")
            SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
            StorageSyncHelper.scheduleSyncForDataChange()
          }
          SignalStore.backup.hasBackupBeenUploaded = true
        }

        is NetworkResult.NetworkError -> {
          Log.i(TAG, "Network failure", result.getCause())
          return if (isCanceled) {
            Result.failure()
          } else {
            Result.retry(defaultBackoff())
          }
        }

        is NetworkResult.StatusCodeError -> {
          Log.i(TAG, "Status code failure", result.getCause())
          return Result.retry(defaultBackoff())
        }

        is NetworkResult.ApplicationError -> throw result.throwable
      }
    }
    stopwatch.split("upload")

    SignalStore.backup.lastBackupProtoSize = tempBackupFile.length()
    if (!tempBackupFile.delete()) {
      Log.e(TAG, "Failed to delete temp backup file")
    }

    SignalStore.backup.lastBackupTime = System.currentTimeMillis()
    stopwatch.split("save-meta")
    stopwatch.stop(TAG)

    if (isCanceled) {
      return Result.failure()
    }

    if (SignalStore.backup.backsUpMedia && SignalDatabase.attachments.doAnyAttachmentsNeedArchiveUpload()) {
      Log.i(TAG, "Enqueuing attachment backfill job.")
      AppDependencies.jobManager.add(ArchiveAttachmentBackfillJob())
    } else {
      Log.i(TAG, "No attachments need to be uploaded, we can finish. Tier: ${SignalStore.backup.backupTier}")
      ArchiveUploadProgress.onMessageBackupFinishedEarly()
    }

    BackupRepository.clearBackupFailure()
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    AppDependencies.jobManager.add(ArchiveCommitAttachmentDeletesJob())
    AppDependencies.jobManager.add(ArchiveAttachmentReconciliationJob())

    return Result.success()
  }

  private fun getOrCreateBackupFile(
    stopwatch: Stopwatch
  ): BackupFileResult {
    if (System.currentTimeMillis() > syncTime && syncTime > 0L && dataFile.isNotNullOrBlank()) {
      val file = File(dataFile)
      val elapsed = (System.currentTimeMillis() - syncTime).milliseconds

      if (file.exists() && file.canRead() && elapsed < FILE_REUSE_TIMEOUT) {
        Log.d(TAG, "File exists and is new enough to utilize.")
        return BackupFileResult.Success(file, syncTime)
      }
    }

    BlobProvider.getInstance().clearTemporaryBackupsDirectory(AppDependencies.application)

    val tempBackupFile = BlobProvider.getInstance().forTemporaryBackup(AppDependencies.application)

    val outputStream = FileOutputStream(tempBackupFile)
    val backupKey = SignalStore.backup.messageBackupKey
    val currentTime = System.currentTimeMillis()
    BackupRepository.export(outputStream = outputStream, messageBackupKey = backupKey, progressEmitter = ArchiveUploadProgress.ArchiveBackupProgressListener, append = { tempBackupFile.appendBytes(it) }, plaintext = false, cancellationSignal = { this.isCanceled }, currentTime = currentTime) {
      writeMediaCursorToTemporaryTable(it, currentTime = currentTime, mediaBackupEnabled = SignalStore.backup.backsUpMedia)
    }

    if (isCanceled) {
      return BackupFileResult.Failure
    }

    stopwatch.split("export")

    when (val result = ArchiveValidator.validate(tempBackupFile, backupKey, forTransfer = false)) {
      ArchiveValidator.ValidationResult.Success -> {
        Log.d(TAG, "Successfully passed validation.")
      }

      is ArchiveValidator.ValidationResult.ReadError -> {
        Log.w(TAG, "Failed to read the file during validation!", result.exception)
        return BackupFileResult.Retry
      }

      is ArchiveValidator.ValidationResult.MessageValidationError -> {
        Log.w(TAG, "The backup file fails validation! Message: ${result.exception.message}, Details: ${result.messageDetails}")
        ArchiveUploadProgress.onValidationFailure()
        return BackupFileResult.Failure
      }

      is ArchiveValidator.ValidationResult.RecipientDuplicateE164Error -> {
        Log.w(TAG, "The backup file fails validation with a duplicate recipient! Message: ${result.exception.message}, Details: ${result.details}")
        ArchiveUploadProgress.onValidationFailure()
        return BackupFileResult.Failure
      }
    }
    stopwatch.split("validate")

    if (isCanceled) {
      return BackupFileResult.Failure
    }

    return BackupFileResult.Success(tempBackupFile, currentTime)
  }

  private fun AttachmentUploadForm.toUploadSpec(): ResumableUpload {
    return ResumableUpload(
      cdnNumber = cdn,
      cdnKey = key,
      location = signedUploadLocation,
      headers = headers.map { (key, value) -> ResumableUpload.Header(key, value) }
    )
  }

  private fun writeMediaCursorToTemporaryTable(db: SignalDatabase, mediaBackupEnabled: Boolean, currentTime: Long) {
    if (mediaBackupEnabled) {
      db.attachmentTable.getAttachmentsEligibleForArchiveUpload().use {
        SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(
          mediaObjects = ArchiveMediaItemIterator(it).asSequence()
        )
      }
    }
  }

  class Factory : Job.Factory<BackupMessagesJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackupMessagesJob {
      val jobData = if (serializedData != null) {
        BackupMessagesJobData.ADAPTER.decode(serializedData)
      } else {
        BackupMessagesJobData()
      }

      return BackupMessagesJob(
        syncTime = jobData.syncTime,
        dataFile = jobData.dataFile,
        resumableMessagesBackupUploadSpec = uploadSpecFromJobData(jobData),
        parameters = parameters
      )
    }

    private fun uploadSpecFromJobData(backupMessagesJobData: BackupMessagesJobData): ResumableMessagesBackupUploadSpec? {
      if (backupMessagesJobData.resumableUri.isBlank() || backupMessagesJobData.uploadSpec == null) {
        return null
      }

      return ResumableMessagesBackupUploadSpec(
        resumableUri = backupMessagesJobData.resumableUri,
        attachmentUploadForm = AttachmentUploadForm(
          cdn = backupMessagesJobData.uploadSpec.cdnNumber,
          key = backupMessagesJobData.uploadSpec.cdnKey,
          headers = backupMessagesJobData.uploadSpec.headers.associate { it.key to it.value_ },
          signedUploadLocation = backupMessagesJobData.uploadSpec.location
        )
      )
    }
  }

  private sealed interface BackupFileResult {
    data class Success(
      val tempBackupFile: File,
      val currentTime: Long
    ) : BackupFileResult

    data object Failure : BackupFileResult
    data object Retry : BackupFileResult
  }
}
