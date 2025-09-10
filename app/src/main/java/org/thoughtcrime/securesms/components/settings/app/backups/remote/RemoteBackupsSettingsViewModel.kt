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
import kotlinx.coroutines.withContext
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.signal.core.util.mebiBytes
import org.signal.core.util.throttleLatest
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgress
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgressState.RestoreStatus
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.components.settings.app.backups.BackupStateObserver
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.attachmentUpdates
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.impl.BackupMessagesConstraint
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState
import org.thoughtcrime.securesms.util.Environment
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
      backupState = BackupStateObserver.getNonIOBackupState(),
      backupsEnabled = SignalStore.backup.areBackupsEnabled,
      canBackupMessagesJobRun = BackupMessagesConstraint.isMet(AppDependencies.application),
      canViewBackupKey = !TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application),
      lastBackupTimestamp = SignalStore.backup.lastBackupTime,
      canBackUpUsingCellular = SignalStore.backup.backupWithCellular,
      canRestoreUsingCellular = SignalStore.backup.restoreWithCellular,
      includeDebuglog = SignalStore.internal.includeDebuglogInBackup.takeIf { RemoteConfig.internalUser },
      showBackupCreateFailedError = BackupRepository.shouldDisplayBackupFailedSettingsRow(),
      showBackupCreateCouldNotCompleteError = BackupRepository.shouldDisplayCouldNotCompleteBackupSettingsRow()
    )
  )

  private val _restoreState: MutableStateFlow<BackupRestoreState> = MutableStateFlow(BackupRestoreState.None)
  private val latestPurchaseId = MutableSharedFlow<InAppPaymentTable.InAppPaymentId>()

  val state: StateFlow<RemoteBackupsSettingsState> = _state
  val restoreState: StateFlow<BackupRestoreState> = _restoreState

  init {
    viewModelScope.launch(Dispatchers.IO) {
      val isBillingApiAvailable = AppDependencies.billingApi.isApiAvailable()
      if (isBillingApiAvailable) {
        _state.update {
          it.copy(isPaidTierPricingAvailable = true)
        }
      } else {
        val paidType = BackupRepository.getPaidType()
        _state.update {
          it.copy(isPaidTierPricingAvailable = paidType is NetworkResult.Success)
        }
      }
    }

    viewModelScope.launch(Dispatchers.IO) {
      refreshBackupMediaSizeState()
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
          refreshBackupMediaSizeState()
        }
    }

    viewModelScope.launch(Dispatchers.IO) {
      var optimizedRemainingBytes = 0L
      while (isActive) {
        if (ArchiveRestoreProgress.state.let { it.restoreState.isMediaRestoreOperation || it.restoreStatus == RestoreStatus.FINISHED }) {
          Log.d(TAG, "Backup is being restored. Collecting updates.")
          ArchiveRestoreProgress
            .stateFlow
            .takeWhile { it.restoreState.isMediaRestoreOperation || it.restoreStatus == RestoreStatus.FINISHED }
            .onEach { latest -> _restoreState.update { BackupRestoreState.Restoring(latest) } }
            .collect()
        } else if (
          !SignalStore.backup.optimizeStorage &&
          SignalStore.backup.userManuallySkippedMediaRestore &&
          SignalDatabase.attachments.getOptimizedMediaAttachmentSize().also { optimizedRemainingBytes = it } > 0
        ) {
          _restoreState.update { BackupRestoreState.Ready(optimizedRemainingBytes.bytes.toUnitString()) }
        } else if (SignalStore.backup.totalRestorableAttachmentSize > 0L) {
          _restoreState.update { BackupRestoreState.Ready(SignalStore.backup.totalRestorableAttachmentSize.bytes.toUnitString()) }
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

    viewModelScope.launch {
      BackupStateObserver(viewModelScope).backupState.collect { state ->
        _state.update {
          it.copy(backupState = state)
        }
      }
    }

    viewModelScope.launch(Dispatchers.IO) {
      BackupRepository.maybeFixAnyDanglingUploadProgress()
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

  fun beginMediaRestore() {
    BackupRepository.resumeMediaRestore()
  }

  fun cancelMediaRestore() {
    if (ArchiveRestoreProgress.state.restoreStatus == RestoreStatus.FINISHED) {
      ArchiveRestoreProgress.clearFinishedStatus()
    } else {
      requestDialog(RemoteBackupsSettingsState.Dialog.CANCEL_MEDIA_RESTORE_PROTECTION)
    }
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
    viewModelScope.launch {
      requestDialog(RemoteBackupsSettingsState.Dialog.PROGRESS_SPINNER)

      withContext(Dispatchers.IO) {
        BackupRepository.turnOffAndDisableBackups()
      }

      requestDialog(RemoteBackupsSettingsState.Dialog.NONE)
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

  private fun refreshBackupMediaSizeState() {
    _state.update {
      it.copy(
        backupMediaSize = getBackupMediaSize(),
        backupMediaDetails = if (RemoteConfig.internalUser || Environment.IS_STAGING) {
          RemoteBackupsSettingsState.BackupMediaDetails(
            awaitingRestore = SignalDatabase.attachments.getRemainingRestorableAttachmentSize().bytes,
            offloaded = SignalDatabase.attachments.getOptimizedMediaAttachmentSize().bytes,
            protoFileSize = SignalStore.backup.lastBackupProtoSize.bytes
          )
        } else null
      )
    }
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
    if (BackupRepository.shouldDisplayOutOfRemoteStorageSpaceUx()) {
      val paidType = BackupRepository.getPaidType()

      if (paidType is NetworkResult.Success) {
        val remoteStorageAllowance = paidType.result.storageAllowanceBytes.bytes
        val estimatedSize = SignalDatabase.attachments.getEstimatedArchiveMediaSize().bytes

        if (estimatedSize + 300.mebiBytes <= remoteStorageAllowance) {
          BackupRepository.clearOutOfRemoteStorageSpaceError()
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
        backupMediaSize = getBackupMediaSize(),
        canBackUpUsingCellular = SignalStore.backup.backupWithCellular,
        canRestoreUsingCellular = SignalStore.backup.restoreWithCellular,
        isOutOfStorageSpace = BackupRepository.shouldDisplayOutOfRemoteStorageSpaceUx(),
        hasRedemptionError = lastPurchase?.data?.error?.data_ == "409",
        showBackupCreateFailedError = BackupRepository.shouldDisplayBackupFailedSettingsRow(),
        showBackupCreateCouldNotCompleteError = BackupRepository.shouldDisplayCouldNotCompleteBackupSettingsRow()
      )
    }
  }

  private fun getBackupMediaSize(): Long {
    return if (SignalStore.backup.hasBackupBeenUploaded || SignalStore.backup.lastBackupTime > 0L) {
      SignalDatabase.attachments.getEstimatedArchiveMediaSize()
    } else {
      0L
    }
  }
}
