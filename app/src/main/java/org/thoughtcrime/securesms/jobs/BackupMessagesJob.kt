/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.Stopwatch
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.logW
import org.signal.libsignal.messagebackup.BackupForwardSecrecyToken
import org.signal.libsignal.net.SvrBStoreResponse
import org.signal.protos.resumableuploads.ResumableUpload
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.backup.v2.ArchiveMediaItemIterator
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgress
import org.thoughtcrime.securesms.backup.v2.ArchiveValidator
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.ResumableMessagesBackupUploadSpec
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.BackupMessagesConstraint
import org.thoughtcrime.securesms.jobs.protos.BackupMessagesJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.isDecisionPending
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.svr.SvrBApi
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
        !RemoteConfig.messageBackups -> {
          Log.i(TAG, "Remote config for backups is disabled.", true)
          false
        }

        SignalStore.registration.restoreDecisionState.isDecisionPending -> {
          Log.i(TAG, "Backup not allowed: a restore decision is pending.", true)
          false
        }

        ArchiveRestoreProgress.state.activelyRestoring() -> {
          Log.i(TAG, "Backup not allowed: a restore is in progress.", true)
          false
        }

        SignalStore.account.isLinkedDevice -> {
          Log.i(TAG, "Backup not allowed: linked device.", true)
          false
        }

        else -> true
      }
    }

    fun enqueue() {
      if (!isBackupAllowed()) {
        Log.d(TAG, "Skip enqueueing BackupMessagesJob.", true)
        return
      }

      val jobManager = AppDependencies.jobManager

      val chain = jobManager.startChain(BackupMessagesJob())

      if (SignalStore.backup.optimizeStorage && SignalStore.backup.backsUpMedia) {
        chain.then(OptimizeMediaJob())
      }

      chain.enqueue()
    }

    fun cancel() {
      AppDependencies.jobManager.find { it.factoryKey == KEY }.forEach { AppDependencies.jobManager.cancel(it.id) }
    }
  }

  constructor() : this(
    syncTime = 0L,
    dataFile = "",
    resumableMessagesBackupUploadSpec = null,
    parameters = Parameters.Builder()
      .addConstraint(BackupMessagesConstraint.KEY)
      .setMaxAttempts(3)
      .setMaxInstancesForFactory(1)
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
      Log.w(TAG, "Failed to backup user messages. Marking failure state.", true)
      BackupRepository.markBackupFailure()
    }
  }

  override fun run(): Result {
    if (!isBackupAllowed()) {
      Log.d(TAG, "Skip running BackupMessagesJob.", true)
      return Result.success()
    }

    val stopwatch = Stopwatch("BackupMessagesJob")

    val auth = when (val result = BackupRepository.getSvrBAuth()) {
      is NetworkResult.Success -> result.result
      is NetworkResult.NetworkError -> return Result.retry(defaultBackoff()).logW(TAG, "Network error when getting SVRB auth.", result.getCause(), true)
      is NetworkResult.StatusCodeError -> return Result.retry(defaultBackoff()).logW(TAG, "Status code error when getting SVRB auth.", result.getCause(), true)
      is NetworkResult.ApplicationError -> throw result.throwable
    }

    val backupSecretData = SignalStore.backup.nextBackupSecretData ?: run {
      Log.i(TAG, "First SVRB backup! Creating new backup chain.", true)
      val secretData = SignalNetwork.svrB.createNewBackupChain(auth, SignalStore.backup.messageBackupKey)
      SignalStore.backup.nextBackupSecretData = secretData
      secretData
    }

    val svrBMetadata: SvrBStoreResponse = when (val result = SignalNetwork.svrB.store(auth, SignalStore.backup.messageBackupKey, backupSecretData)) {
      is SvrBApi.StoreResult.Success -> result.data
      is SvrBApi.StoreResult.NetworkError -> return Result.retry(result.retryAfter?.inWholeMilliseconds ?: defaultBackoff()).logW(TAG, "SVRB transient network error.", result.exception, true)
      is SvrBApi.StoreResult.SvrError -> return Result.retry(defaultBackoff()).logW(TAG, "SVRB error.", result.throwable, true)
      SvrBApi.StoreResult.InvalidDataError -> {
        Log.w(TAG, "Invalid SVRB data on the server! Clearing backup secret data and retrying.", true)
        SignalStore.backup.nextBackupSecretData = null
        return Result.retry(defaultBackoff())
      }
      is SvrBApi.StoreResult.UnknownError -> return Result.fatalFailure(RuntimeException(result.throwable))
    }

    Log.i(TAG, "Successfully stored data on SVRB.", true)
    stopwatch.split("svrb")

    val createKeyResult = SignalDatabase.attachments.createRemoteKeyForAttachmentsThatNeedArchiveUpload()
    if (createKeyResult.totalCount > 0) {
      Log.w(TAG, "Needed to create remote keys. $createKeyResult", true)
      if (createKeyResult.unexpectedKeyCreation) {
        maybePostRemoteKeyMissingNotification()
      }
    }
    stopwatch.split("keygen")

    SignalDatabase.attachments.clearIncrementalMacsForAttachmentsThatNeedArchiveUpload().takeIf { it > 0 }?.let { count -> Log.w(TAG, "Needed to clear $count incrementalMacs.", true) }
    stopwatch.split("clear-incmac")

    if (isCanceled) {
      return Result.failure()
    }

    val (tempBackupFile, currentTime) = when (val generateBackupFileResult = getOrCreateBackupFile(stopwatch, svrBMetadata.forwardSecrecyToken, svrBMetadata.metadata)) {
      is BackupFileResult.Success -> generateBackupFileResult
      BackupFileResult.Failure -> return Result.failure()
      BackupFileResult.Retry -> return Result.retry(defaultBackoff())
    }

    ArchiveUploadProgress.onMessageBackupCreated(tempBackupFile.length())
    SignalStore.backup.lastBackupProtoVersion = BackupRepository.VERSION

    this.syncTime = currentTime
    this.dataFile = tempBackupFile.path

    val backupSpec: ResumableMessagesBackupUploadSpec = resumableMessagesBackupUploadSpec ?: when (val result = BackupRepository.getResumableMessagesBackupUploadSpec(tempBackupFile.length())) {
      is NetworkResult.Success -> {
        Log.i(TAG, "Successfully generated a new upload spec.", true)

        val spec = result.result
        resumableMessagesBackupUploadSpec = spec
        spec
      }

      is NetworkResult.NetworkError -> {
        Log.i(TAG, "Network failure", result.getCause(), true)
        return Result.retry(defaultBackoff())
      }

      is NetworkResult.StatusCodeError -> {
        when (result.code) {
          413 -> {
            Log.i(TAG, "Backup file is too large! Size: ${tempBackupFile.length()} bytes", result.getCause(), true)
            // TODO [backup] Need to show the user an error
          }
          else -> {
            Log.i(TAG, "Status code failure", result.getCause(), true)
          }
        }
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

    FileInputStream(tempBackupFile).use { fileStream ->
      val uploadResult = SignalNetwork.archive.uploadBackupFile(
        uploadForm = backupSpec.attachmentUploadForm,
        resumableUploadUrl = backupSpec.resumableUri,
        data = fileStream,
        dataLength = tempBackupFile.length(),
        progressListener = progressListener
      )

      when (uploadResult) {
        is NetworkResult.Success -> {
          Log.i(TAG, "Successfully uploaded backup file.", true)
          if (!SignalStore.backup.hasBackupBeenUploaded) {
            Log.i(TAG, "First time making a backup - scheduling a storage sync.", true)
            SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
            StorageSyncHelper.scheduleSyncForDataChange()
          }
          SignalStore.backup.hasBackupBeenUploaded = true
        }

        is NetworkResult.NetworkError -> {
          Log.i(TAG, "Network failure", uploadResult.getCause(), true)
          return if (isCanceled) {
            Result.failure()
          } else {
            Result.retry(defaultBackoff())
          }
        }

        is NetworkResult.StatusCodeError -> {
          Log.i(TAG, "Status code failure", uploadResult.getCause(), true)
          when (uploadResult.code) {
            400 -> {
              Log.w(TAG, "400 likely means bad resumable state. Resetting the upload spec before retrying.", true)
              resumableMessagesBackupUploadSpec = null
            }
          }
          return Result.retry(defaultBackoff())
        }

        is NetworkResult.ApplicationError -> throw uploadResult.throwable
      }
    }
    stopwatch.split("upload")

    SignalStore.backup.nextBackupSecretData = svrBMetadata.nextBackupSecretData

    SignalStore.backup.lastBackupProtoSize = tempBackupFile.length()
    if (!tempBackupFile.delete()) {
      Log.e(TAG, "Failed to delete temp backup file", true)
    }

    SignalStore.backup.lastBackupTime = System.currentTimeMillis()
    stopwatch.split("save-meta")
    stopwatch.stop(TAG)

    if (isCanceled) {
      return Result.failure()
    }

    if (SignalStore.backup.backsUpMedia && SignalDatabase.attachments.doAnyAttachmentsNeedArchiveUpload()) {
      Log.i(TAG, "Enqueuing attachment backfill job.", true)
      AppDependencies.jobManager.add(ArchiveAttachmentBackfillJob())
    } else {
      Log.i(TAG, "No attachments need to be uploaded, we can finish. Tier: ${SignalStore.backup.backupTier}", true)
      ArchiveUploadProgress.onMessageBackupFinishedEarly()
    }

    if (SignalStore.backup.backsUpMedia && SignalDatabase.attachments.doAnyThumbnailsNeedArchiveUpload()) {
      Log.i(TAG, "Enqueuing thumbnail backfill job.", true)
      AppDependencies.jobManager.add(ArchiveThumbnailBackfillJob())
    } else {
      Log.i(TAG, "No thumbnails need to be uploaded: ${SignalStore.backup.backupTier}", true)
    }

    BackupRepository.clearBackupFailure()
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    if (SignalStore.backup.backsUpMedia) {
      AppDependencies.jobManager.add(ArchiveCommitAttachmentDeletesJob())
      AppDependencies.jobManager.add(ArchiveAttachmentReconciliationJob())
    }

    return Result.success()
  }

  private fun getOrCreateBackupFile(
    stopwatch: Stopwatch,
    forwardSecrecyToken: BackupForwardSecrecyToken,
    forwardSecrecyMetadata: ByteArray
  ): BackupFileResult {
    if (System.currentTimeMillis() > syncTime && syncTime > 0L && dataFile.isNotNullOrBlank()) {
      val file = File(dataFile)
      val elapsed = (System.currentTimeMillis() - syncTime).milliseconds

      if (file.exists() && file.canRead() && elapsed < FILE_REUSE_TIMEOUT) {
        Log.d(TAG, "File exists and is new enough to utilize.", true)
        return BackupFileResult.Success(file, syncTime)
      }
    }

    BlobProvider.getInstance().clearTemporaryBackupsDirectory(AppDependencies.application)

    val tempBackupFile = BlobProvider.getInstance().forTemporaryBackup(AppDependencies.application)

    val outputStream = FileOutputStream(tempBackupFile)
    val backupKey = SignalStore.backup.messageBackupKey
    val currentTime = System.currentTimeMillis()

    BackupRepository.exportForSignalBackup(
      outputStream = outputStream,
      messageBackupKey = backupKey,
      forwardSecrecyMetadata = forwardSecrecyMetadata,
      forwardSecrecyToken = forwardSecrecyToken,
      progressEmitter = ArchiveUploadProgress.ArchiveBackupProgressListener,
      append = { tempBackupFile.appendBytes(it) },
      cancellationSignal = { this.isCanceled },
      currentTime = currentTime
    ) {
      writeMediaCursorToTemporaryTable(it, mediaBackupEnabled = SignalStore.backup.backsUpMedia)
    }

    if (isCanceled) {
      return BackupFileResult.Failure
    }

    stopwatch.split("export")

    when (val result = ArchiveValidator.validateSignalBackup(tempBackupFile, backupKey, forwardSecrecyToken)) {
      ArchiveValidator.ValidationResult.Success -> {
        Log.d(TAG, "Successfully passed validation.", true)
      }

      is ArchiveValidator.ValidationResult.ReadError -> {
        Log.w(TAG, "Failed to read the file during validation!", result.exception, true)
        return BackupFileResult.Retry
      }

      is ArchiveValidator.ValidationResult.MessageValidationError -> {
        Log.w(TAG, "The backup file fails validation! Message: ${result.exception.message}, Details: ${result.messageDetails}", true)
        ArchiveUploadProgress.onValidationFailure()
        return BackupFileResult.Failure
      }

      is ArchiveValidator.ValidationResult.RecipientDuplicateE164Error -> {
        Log.w(TAG, "The backup file fails validation with a duplicate recipient! Message: ${result.exception.message}, Details: ${result.details}", true)
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

  private fun writeMediaCursorToTemporaryTable(db: SignalDatabase, mediaBackupEnabled: Boolean) {
    if (mediaBackupEnabled) {
      db.attachmentTable.getFullSizeAttachmentsThatWillBeIncludedInArchive().use {
        SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(
          mediaObjects = ArchiveMediaItemIterator(it).asSequence()
        )
      }

      db.attachmentTable.getThumbnailAttachmentsThatWillBeIncludedInArchive().use {
        SignalDatabase.backupMediaSnapshots.writeThumbnailPendingMediaObjects(
          mediaObjects = ArchiveMediaItemIterator(it).asSequence()
        )
      }
    }
  }

  private fun maybePostRemoteKeyMissingNotification() {
    if (!RemoteConfig.internalUser || !SignalStore.backup.backsUpMedia) {
      return
    }

    val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("[Internal-only] Unexpected remote key missing!")
      .setContentText("Tap to send a debug log")
      .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, SubmitDebugLogActivity::class.java), PendingIntentFlags.mutable()))
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.INTERNAL_ERROR, notification)
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
