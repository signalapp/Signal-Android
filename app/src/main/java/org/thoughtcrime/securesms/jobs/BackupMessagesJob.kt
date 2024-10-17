/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.WifiConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.providers.BlobProvider
import org.whispersystems.signalservice.api.NetworkResult
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Job that is responsible for exporting the DB as a backup proto and
 * also uploading the resulting proto.
 */
class BackupMessagesJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(BackupMessagesJob::class.java)

    const val KEY = "BackupMessagesJob"

    /**
     * Pruning abandoned remote media is relatively expensive, so we should
     * not do this every time we backup.
     */
    fun enqueue(pruneAbandonedRemoteMedia: Boolean = false) {
      val jobManager = AppDependencies.jobManager

      val chain = jobManager.startChain(BackupMessagesJob())

      if (pruneAbandonedRemoteMedia) {
        chain.then(SyncArchivedMediaJob())
      }

      if (SignalStore.backup.optimizeStorage && SignalStore.backup.backsUpMedia) {
        chain.then(OptimizeMediaJob())
      }

      chain.enqueue()
    }
  }

  constructor() : this(
    Parameters.Builder()
      .addConstraint(if (SignalStore.backup.backupWithCellular) NetworkConstraint.KEY else WifiConstraint.KEY)
      .setMaxAttempts(3)
      .setMaxInstancesForFactory(1)
      .setQueue(BackfillDigestJob.QUEUE) // We want to ensure digests have been backfilled before this runs. Could eventually remove this constraint.
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun run(): Result {
    val stopwatch = Stopwatch("BackupMessagesJob")

    SignalDatabase.attachments.createKeyIvDigestForAttachmentsThatNeedArchiveUpload().takeIf { it > 0 }?.let { count -> Log.w(TAG, "Needed to create $count key/iv/digests.") }
    stopwatch.split("key-iv-digest")

    ArchiveUploadProgress.begin()
    val tempBackupFile = BlobProvider.getInstance().forNonAutoEncryptingSingleSessionOnDisk(AppDependencies.application)

    val outputStream = FileOutputStream(tempBackupFile)
    BackupRepository.export(outputStream = outputStream, append = { tempBackupFile.appendBytes(it) }, plaintext = false, cancellationSignal = { this.isCanceled })
    stopwatch.split("export")

    if (isCanceled) {
      return Result.failure()
    }

    ArchiveUploadProgress.onMessageBackupCreated()

    FileInputStream(tempBackupFile).use {
      when (val result = BackupRepository.uploadBackupFile(it, tempBackupFile.length())) {
        is NetworkResult.Success -> {
          Log.i(TAG, "Successfully uploaded backup file.")
          SignalStore.backup.hasBackupBeenUploaded = true
        }
        is NetworkResult.NetworkError -> return Result.retry(defaultBackoff())
        is NetworkResult.StatusCodeError -> return Result.retry(defaultBackoff())
        is NetworkResult.ApplicationError -> throw result.throwable
      }
    }
    stopwatch.split("upload")

    SignalStore.backup.lastBackupProtoSize = tempBackupFile.length()
    if (!tempBackupFile.delete()) {
      Log.e(TAG, "Failed to delete temp backup file")
    }

    SignalStore.backup.lastBackupTime = System.currentTimeMillis()
    SignalStore.backup.usedBackupMediaSpace = when (val result = BackupRepository.getRemoteBackupUsedSpace()) {
      is NetworkResult.Success -> result.result ?: 0
      is NetworkResult.NetworkError -> SignalStore.backup.usedBackupMediaSpace // TODO [backup] enqueue a secondary job to fetch the latest number -- no need to fail this one
      is NetworkResult.StatusCodeError -> {
        Log.w(TAG, "Failed to get used space: ${result.code}")
        SignalStore.backup.usedBackupMediaSpace
      }
      is NetworkResult.ApplicationError -> throw result.throwable
    }
    stopwatch.split("used-space")
    stopwatch.stop(TAG)

    if (SignalStore.backup.backsUpMedia && SignalDatabase.attachments.doAnyAttachmentsNeedArchiveUpload()) {
      Log.i(TAG, "Enqueuing attachment backfill job.")
      AppDependencies.jobManager.add(ArchiveAttachmentBackfillJob())
    } else {
      Log.i(TAG, "No attachments need to be uploaded, we can finish. Tier: ${SignalStore.backup.backupTier}")
      ArchiveUploadProgress.onMessageBackupFinishedEarly()
    }

    return Result.success()
  }

  class Factory : Job.Factory<BackupMessagesJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackupMessagesJob {
      return BackupMessagesJob(parameters)
    }
  }
}
