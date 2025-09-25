/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.DeletionNotAwaitingMediaDownloadConstraint
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.BackupDeleteJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.NetworkResult
import kotlin.time.Duration.Companion.seconds

/**
 * Handles deleting user backup and unsubscribing them from backups.
 */
class BackupDeleteJob private constructor(
  private var backupDeleteJobData: BackupDeleteJobData,
  parameters: Parameters
) : Job(parameters) {

  companion object {
    const val KEY = "BackupDeleteJob"
    private val TAG = Log.tag(BackupDeleteJob::class)
  }

  constructor(backupDeleteJobData: BackupDeleteJobData = BackupDeleteJobData()) : this(
    backupDeleteJobData,
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(DeletionNotAwaitingMediaDownloadConstraint.KEY)
      .setMaxInstancesForFactory(1)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build()
  )

  override fun serialize(): ByteArray = backupDeleteJobData.encode()

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    if (!RemoteConfig.messageBackups) {
      Log.w(TAG, "Message backups are not available on this device. Exiting without local cleanup.")
      return Result.failure()
    }

    if (!SignalStore.account.isRegistered) {
      Log.w(TAG, "User not registered. Exiting without local cleanup.")
      return Result.failure()
    }

    if (SignalStore.account.isLinkedDevice) {
      Log.w(TAG, "User is on a linked device. Exiting without local cleanup.")
      return Result.failure()
    }

    if (SignalStore.backup.deletionState.isIdle()) {
      Log.w(TAG, "Invalid state ${SignalStore.backup.deletionState}. Exiting without local cleanup.")
      return Result.failure()
    }

    val result = doRun()

    if (result.isFailure) {
      clearLocalBackupStateOnFailure()
      BackupRepository.resetInitializedStateAndAuthCredentials()
    }

    return result
  }

  private fun doRun(): Result {
    if (SignalStore.backup.deletionState == DeletionState.AWAITING_MEDIA_DOWNLOAD) {
      Log.i(TAG, "Awaiting media download. Scheduling retry.")
      return Result.retry(5.seconds.inWholeMilliseconds)
    }

    val clearLocalStateResult = if (SignalStore.backup.deletionState == DeletionState.CLEAR_LOCAL_STATE) {
      val results = listOf(
        deleteLocalState(),
        cancelActiveSubscription()
      )

      checkResults(results)
    } else {
      Result.success()
    }

    if (!clearLocalStateResult.isSuccess) {
      Log.w(TAG, "Failed to clear local state and subscriber.")
      return clearLocalStateResult
    }

    if (isMediaRestoreRequired()) {
      Log.i(TAG, "Moving to AWAITING_MEDIA_DOWNLOAD state and scheduling retry.")
      SignalStore.backup.deletionState = DeletionState.AWAITING_MEDIA_DOWNLOAD
      AppDependencies.jobManager
        .startChain(BackupRestoreMediaJob())
        .then(RestoreOptimizedMediaJob())
        .enqueue()

      return Result.retry(5.seconds.inWholeMilliseconds)
    }

    Log.i(TAG, "Moving to DELETE_BACKUPS state")
    SignalStore.backup.deletionState = DeletionState.DELETE_BACKUPS

    val results = listOf(
      deleteMessageBackup(),
      deleteMediaBackup()
    )

    val result = checkResults(results)
    if (result.isSuccess) {
      Log.i(TAG, "Backup deletion was successful.")
      BackupRepository.resetInitializedStateAndAuthCredentials()
      SignalStore.backup.deletionState = DeletionState.COMPLETE
    }

    return result
  }

  override fun onFailure() {
    if (SignalStore.backup.deletionState.isIdle()) {
      Log.w(TAG, "Backup is idle. Not marking a deletion.")
    } else if (SignalStore.backup.deletionState == DeletionState.AWAITING_MEDIA_DOWNLOAD) {
      Log.w(TAG, "BackupDeleteFailure occurred while awaiting media download, ignoring.")
    } else {
      SignalStore.backup.deletionState = DeletionState.FAILED
    }
  }

  private fun checkResults(results: List<Result>): Result {
    val isAllSuccess = results.all { it.isSuccess }
    val hasRetries = results.any { it.isRetry }

    return when {
      isAllSuccess -> {
        Log.d(TAG, "${results.size} stages completed successfully.")
        Result.success()
      }
      hasRetries -> {
        Log.d(TAG, "Retries were detected. Scheduling.")
        Result.retry(defaultBackoff())
      }
      else -> {
        Log.d(TAG, "Not all stages completed and no retries were present.")
        Result.failure()
      }
    }
  }

  private fun isMediaRestoreRequired(): Boolean {
    if (backupDeleteJobData.tier != BackupDeleteJobData.Tier.PAID) {
      Log.i(TAG, "User is not on the PAID tier so there's nothing we can download.")
      return false
    }

    val requiresMediaRestore = SignalDatabase.attachments.getRemainingRestorableAttachmentSize() > 0L
    val hasOffloadedMedia = SignalDatabase.attachments.getOptimizedMediaAttachmentSize() > 0L

    if ((requiresMediaRestore || hasOffloadedMedia) && !SignalStore.backup.userManuallySkippedMediaRestore) {
      Log.i(TAG, "User has undownloaded media. Enqueuing download now.")
      return true
    } else {
      Log.i(TAG, "User does not have undownloaded media or has opted to skip restoration.")
      return false
    }
  }

  private fun cancelActiveSubscription(): Result {
    if (backupDeleteJobData.completed.contains(BackupDeleteJobData.Stage.CANCEL_SUBSCRIBER)) {
      Log.d(TAG, "Already canceled active subscription.")
      return Result.success()
    }

    Log.d(TAG, "Checking for an active backups subscription.")
    val subscriberId = InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP)
    if (subscriberId != null) {
      Log.d(TAG, "Found a subscriber. Canceling subscription.")
      try {
        RecurringInAppPaymentRepository.cancelActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to cancel active backups subscription. Failing.", e)
        return Result.failure()
      }
      Log.d(TAG, "Finished canceling subscription.")
    } else {
      Log.d(TAG, "No subscriber found. Skipping subscription cancellation.")
    }

    addStageToCompletions(BackupDeleteJobData.Stage.CANCEL_SUBSCRIBER)
    return Result.success()
  }

  private fun deleteMessageBackup(): Result {
    if (backupDeleteJobData.completed.contains(BackupDeleteJobData.Stage.DELETE_MESSAGES)) {
      Log.d(TAG, "Already deleted messages.")
      return Result.success()
    }

    val deleteMessageBackupResult: NetworkResult<Unit> = BackupRepository.deleteBackup()
    if (deleteMessageBackupResult.getCause() != null) {
      Log.w(TAG, "Failed to delete message backup", deleteMessageBackupResult.getCause())
      return handleNetworkError(deleteMessageBackupResult)
    } else {
      Log.d(TAG, "Deleted message backup.")
    }

    addStageToCompletions(BackupDeleteJobData.Stage.DELETE_MESSAGES)
    return Result.success()
  }

  private fun deleteMediaBackup(): Result {
    if (backupDeleteJobData.completed.contains(BackupDeleteJobData.Stage.DELETE_MEDIA)) {
      Log.d(TAG, "Already deleted media.")
      return Result.success()
    }

    if (backupDeleteJobData.tier == BackupDeleteJobData.Tier.PAID) {
      val deleteMediaBackupResult: NetworkResult<Unit> = BackupRepository.deleteMediaBackup()
      if (deleteMediaBackupResult.getCause() != null) {
        Log.w(TAG, "Failed to delete media backup", deleteMediaBackupResult.getCause())
        return handleNetworkError(deleteMediaBackupResult)
      } else {
        Log.d(TAG, "Deleted media backup.")
      }
    }

    addStageToCompletions(BackupDeleteJobData.Stage.DELETE_MEDIA)
    return Result.success()
  }

  private fun deleteLocalState(): Result {
    if (backupDeleteJobData.completed.contains(BackupDeleteJobData.Stage.CLEAR_LOCAL_STATE)) {
      Log.d(TAG, "Already cleared local backup state.")
      return Result.success()
    }

    Log.d(TAG, "Loading backup tier from service.")
    val backupTierResult: NetworkResult<MessageBackupTier> = BackupRepository.getBackupTier()
    if (backupTierResult.getCause() != null) {
      return handleNetworkError(backupTierResult)
    }

    val backupTier: MessageBackupTier = backupTierResult.successOrThrow()
    Log.d(TAG, "Network request returned $backupTier")
    backupDeleteJobData = backupDeleteJobData.newBuilder().tier(
      when (backupTier) {
        MessageBackupTier.FREE -> BackupDeleteJobData.Tier.FREE
        MessageBackupTier.PAID -> BackupDeleteJobData.Tier.PAID
      }
    ).build()

    Log.d(TAG, "Clearing local backup state.")
    clearLocalBackupState()
    addStageToCompletions(BackupDeleteJobData.Stage.CLEAR_LOCAL_STATE)
    return Result.success()
  }

  private fun clearLocalBackupStateOnFailure() {
    if (backupDeleteJobData.completed.contains(BackupDeleteJobData.Stage.CLEAR_LOCAL_STATE)) {
      Log.d(TAG, "[onFailure] Already cleared local backup state.")
      return
    }

    Log.d(TAG, "[onFailure] Clearing local backup state.")
    clearLocalBackupState()
  }

  private fun clearLocalBackupState() {
    SignalStore.backup.disableBackups()
    SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
    SignalDatabase.attachments.clearAllArchiveData()
    SignalStore.backup.optimizeStorage = false
  }

  private fun addStageToCompletions(stage: BackupDeleteJobData.Stage) {
    backupDeleteJobData = backupDeleteJobData.newBuilder()
      .completed(backupDeleteJobData.completed + stage)
      .build()
  }

  private fun <T> handleNetworkError(networkResult: NetworkResult<T>): Result {
    Log.d(TAG, "An error occurred.", networkResult.getCause())

    if (networkResult.getCause() is org.signal.libsignal.zkgroup.VerificationFailedException) {
      Log.i(TAG, "ZK Verification failed. Retrying.")
      return Result.retry(defaultBackoff())
    }

    return when (networkResult) {
      is NetworkResult.ApplicationError<*> -> (networkResult.getCause() as? RuntimeException)?.let { Result.fatalFailure(it) } ?: Result.failure()
      is NetworkResult.NetworkError<*> -> Result.retry(defaultBackoff())
      is NetworkResult.StatusCodeError<*> -> handleStatusCodeError(networkResult)
      is NetworkResult.Success<*> -> error("Success.")
    }
  }

  private fun handleStatusCodeError(statusCodeError: NetworkResult.StatusCodeError<*>): Result {
    Log.d(TAG, "Status code error: ${statusCodeError.code}")

    return when (statusCodeError.code) {
      429 -> Result.retry(statusCodeError.retryAfter()?.inWholeMilliseconds ?: defaultBackoff())
      else -> Result.failure()
    }
  }

  class Factory : Job.Factory<BackupDeleteJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackupDeleteJob {
      val data = BackupDeleteJobData.ADAPTER.decode(serializedData!!)

      return BackupDeleteJob(data, parameters)
    }
  }
}
