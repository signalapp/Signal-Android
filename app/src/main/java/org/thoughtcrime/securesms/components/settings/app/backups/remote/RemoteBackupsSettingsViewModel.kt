/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.signal.core.util.mebiBytes
import org.signal.core.util.throttleLatest
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.v2.BackupFrequency
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.ui.status.BackupStatusData
import org.thoughtcrime.securesms.banner.banners.MediaRestoreProgressBanner
import org.thoughtcrime.securesms.components.settings.app.backups.BackupStateRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.attachmentUpdates
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.impl.BackupMessagesConstraint
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState
import org.thoughtcrime.securesms.service.MessageBackupListener
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.NetworkResult
import kotlin.time.Duration.Companion.seconds

/**
 * ViewModel for state management of RemoteBackupsSettingsFragment
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteBackupsSettingsViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(RemoteBackupsSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(
    RemoteBackupsSettingsState(
      tier = SignalStore.backup.backupTier,
      backupsEnabled = SignalStore.backup.areBackupsEnabled,
      canBackupMessagesJobRun = BackupMessagesConstraint.isMet(AppDependencies.application),
      canViewBackupKey = !TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application),
      lastBackupTimestamp = SignalStore.backup.lastBackupTime,
      backupsFrequency = SignalStore.backup.backupFrequency,
      canBackUpUsingCellular = SignalStore.backup.backupWithCellular,
      canRestoreUsingCellular = SignalStore.backup.restoreWithCellular,
      includeDebuglog = SignalStore.internal.includeDebuglogInBackup.takeIf { RemoteConfig.internalUser }
    )
  )

  private val _restoreState: MutableStateFlow<BackupRestoreState> = MutableStateFlow(BackupRestoreState.None)
  private val latestPurchaseId = MutableSharedFlow<InAppPaymentTable.InAppPaymentId>()

  val state: StateFlow<RemoteBackupsSettingsState> = _state
  val restoreState: StateFlow<BackupRestoreState> = _restoreState

  init {
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(backupMediaSize = SignalDatabase.attachments.getEstimatedArchiveMediaSize()) }
    }

    viewModelScope.launch(Dispatchers.IO) {
      SignalStore.backup.deletionStateFlow.collectLatest {
        refresh()
      }
    }

    viewModelScope.launch(Dispatchers.IO) {
      latestPurchaseId
        .flatMapLatest { id -> InAppPaymentsRepository.observeUpdates(id).asFlow() }
        .collectLatest { purchase ->
          Log.d(TAG, "Refreshing state after archive IAP update.")
          refreshState(purchase)
        }
    }

    viewModelScope.launch(Dispatchers.IO) {
      AppDependencies
        .databaseObserver
        .attachmentUpdates()
        .throttleLatest(5.seconds)
        .collectLatest {
          _state.update { it.copy(backupMediaSize = SignalDatabase.attachments.getEstimatedArchiveMediaSize()) }
        }
    }

    viewModelScope.launch(Dispatchers.IO) {
      val restoreProgress = MediaRestoreProgressBanner()

      var optimizedRemainingBytes = 0L
      while (isActive) {
        if (restoreProgress.enabled) {
          Log.d(TAG, "Backup is being restored. Collecting updates.")
          restoreProgress
            .dataFlow
            .onEach { latest -> _restoreState.update { BackupRestoreState.FromBackupStatusData(latest) } }
            .takeWhile { it !is BackupStatusData.RestoringMedia || it.restoreStatus != BackupStatusData.RestoreStatus.FINISHED }
            .collect()
        } else if (
          !SignalStore.backup.optimizeStorage &&
          SignalStore.backup.userManuallySkippedMediaRestore &&
          SignalDatabase.attachments.getOptimizedMediaAttachmentSize().also { optimizedRemainingBytes = it } > 0
        ) {
          _restoreState.update { BackupRestoreState.Ready(optimizedRemainingBytes.bytes.toUnitString()) }
        } else if (SignalStore.backup.totalRestorableAttachmentSize > 0L) {
          _restoreState.update { BackupRestoreState.Ready(SignalStore.backup.totalRestorableAttachmentSize.bytes.toUnitString()) }
        } else if (BackupRepository.shouldDisplayBackupFailedSettingsRow()) {
          _restoreState.update { BackupRestoreState.FromBackupStatusData(BackupStatusData.BackupFailed) }
        } else if (BackupRepository.shouldDisplayCouldNotCompleteBackupSettingsRow()) {
          _restoreState.update { BackupRestoreState.FromBackupStatusData(BackupStatusData.CouldNotCompleteBackup) }
        } else {
          _restoreState.update { BackupRestoreState.None }
        }

        delay(1.seconds)
      }
    }

    viewModelScope.launch {
      var previous: ArchiveUploadProgressState.State? = null
      ArchiveUploadProgress.progress
        .collect { current ->
          if (previous != null && previous != current.state && current.state == ArchiveUploadProgressState.State.None) {
            Log.d(TAG, "Refreshing state after archive upload.")
            refreshState(null)
          }
          previous = current.state
        }
    }
  }

  fun setCanBackUpUsingCellular(canBackUpUsingCellular: Boolean) {
    SignalStore.backup.backupWithCellular = canBackUpUsingCellular
    _state.update {
      it.copy(
        canBackupMessagesJobRun = BackupMessagesConstraint.isMet(AppDependencies.application),
        canBackUpUsingCellular = canBackUpUsingCellular
      )
    }
  }

  fun setCanRestoreUsingCellular() {
    SignalStore.backup.restoreWithCellular = true
    _state.update { it.copy(canRestoreUsingCellular = true) }
  }

  fun setBackupsFrequency(backupsFrequency: BackupFrequency) {
    SignalStore.backup.backupFrequency = backupsFrequency
    _state.update { it.copy(backupsFrequency = backupsFrequency) }
    MessageBackupListener.setNextBackupTimeToIntervalFromNow()
    MessageBackupListener.schedule(AppDependencies.application)
  }

  fun beginMediaRestore() {
    BackupRepository.resumeMediaRestore()
  }

  fun skipMediaRestore() {
    BackupRepository.skipMediaRestore()

    if (SignalStore.backup.deletionState == DeletionState.AWAITING_MEDIA_DOWNLOAD) {
      BackupRepository.continueTurningOffAndDisablingBackups()
    }
  }

  fun requestDialog(dialog: RemoteBackupsSettingsState.Dialog) {
    _state.update { it.copy(dialog = dialog) }
  }

  fun requestSnackbar(snackbar: RemoteBackupsSettingsState.Snackbar) {
    _state.update { it.copy(snackbar = snackbar) }
  }

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      val id = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_BACKUP)?.id

      if (id != null) {
        latestPurchaseId.emit(id)
      } else {
        refreshState(null)
      }
    }
  }

  fun turnOffAndDeleteBackups() {
    requestDialog(RemoteBackupsSettingsState.Dialog.PROGRESS_SPINNER)

    viewModelScope.launch(Dispatchers.IO) {
      BackupRepository.turnOffAndDisableBackups()
    }
  }

  fun onBackupNowClick() {
    BackupMessagesJob.enqueue()
  }

  fun cancelUpload() {
    ArchiveUploadProgress.cancel()
  }

  fun setIncludeDebuglog(includeDebuglog: Boolean) {
    SignalStore.internal.includeDebuglogInBackup = includeDebuglog
    _state.update { it.copy(includeDebuglog = includeDebuglog) }
  }

  private suspend fun refreshState(lastPurchase: InAppPaymentTable.InAppPayment?) {
    try {
      Log.i(TAG, "Performing a state refresh.")
      performStateRefresh(lastPurchase)
    } catch (e: Exception) {
      Log.w(TAG, "State refresh failed", e)
      throw e
    }
  }

  private suspend fun performStateRefresh(lastPurchase: InAppPaymentTable.InAppPayment?) {
    if (BackupRepository.shouldDisplayOutOfStorageSpaceUx()) {
      val paidType = BackupRepository.getPaidType()

      if (paidType is NetworkResult.Success) {
        val remoteStorageAllowance = paidType.result.storageAllowanceBytes.bytes
        val estimatedSize = SignalDatabase.attachments.getEstimatedArchiveMediaSize().bytes

        if (estimatedSize + 300.mebiBytes <= remoteStorageAllowance) {
          BackupRepository.clearOutOfRemoteStorageError()
        }

        _state.update {
          it.copy(
            totalAllowedStorageSpace = estimatedSize.toUnitString()
          )
        }
      } else {
        Log.w(TAG, "Failed to load PAID type.", paidType.getCause())
      }
    }

    _state.update {
      it.copy(
        tier = SignalStore.backup.backupTier,
        backupsEnabled = SignalStore.backup.areBackupsEnabled,
        lastBackupTimestamp = SignalStore.backup.lastBackupTime,
        canBackupMessagesJobRun = BackupMessagesConstraint.isMet(AppDependencies.application),
        backupMediaSize = SignalDatabase.attachments.getEstimatedArchiveMediaSize(),
        backupsFrequency = SignalStore.backup.backupFrequency,
        canBackUpUsingCellular = SignalStore.backup.backupWithCellular,
        canRestoreUsingCellular = SignalStore.backup.restoreWithCellular,
        isOutOfStorageSpace = BackupRepository.shouldDisplayOutOfStorageSpaceUx(),
        hasRedemptionError = lastPurchase?.data?.error?.data_ == "409"
      )
    }

    val state = BackupStateRepository.resolveBackupState(lastPurchase)
    _state.update {
      it.copy(backupState = state)
    }
  }
}
