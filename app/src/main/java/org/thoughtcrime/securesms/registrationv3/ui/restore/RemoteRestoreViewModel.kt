/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.ByteSize
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.RestoreV2Event
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.jobs.BackupRestoreJob
import org.thoughtcrime.securesms.jobs.BackupRestoreMediaJob
import org.thoughtcrime.securesms.jobs.SyncArchivedMediaJob
import org.thoughtcrime.securesms.keyvalue.Completed
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.Skipped
import org.thoughtcrime.securesms.registrationv3.data.QuickRegistrationRepository
import org.whispersystems.signalservice.api.registration.RestoreMethod

class RemoteRestoreViewModel(isOnlyRestoreOption: Boolean) : ViewModel() {

  companion object {
    private val TAG = Log.tag(RemoteRestoreViewModel::class)
  }

  private val store: MutableStateFlow<ScreenState> = MutableStateFlow(
    ScreenState(
      isRemoteRestoreOnlyOption = isOnlyRestoreOption,
      backupTier = SignalStore.backup.backupTier,
      backupTime = SignalStore.backup.lastBackupTime,
      backupSize = SignalStore.backup.totalBackupSize.bytes
    )
  )

  val state: StateFlow<ScreenState> = store.asStateFlow()

  init {
    viewModelScope.launch(Dispatchers.IO) {
      val tier: MessageBackupTier? = BackupRepository.restoreBackupTier(SignalStore.account.requireAci())
      store.update {
        if (tier != null && SignalStore.backup.lastBackupTime > 0) {
          it.copy(
            loadState = ScreenState.LoadState.LOADED,
            backupTier = SignalStore.backup.backupTier,
            backupTime = SignalStore.backup.lastBackupTime,
            backupSize = SignalStore.backup.totalBackupSize.bytes
          )
        } else {
          if (SignalStore.backup.isBackupTierRestored || SignalStore.backup.lastBackupTime == 0L) {
            it.copy(loadState = ScreenState.LoadState.NOT_FOUND)
          } else if (it.loadState == ScreenState.LoadState.LOADING) {
            it.copy(loadState = ScreenState.LoadState.FAILURE)
          } else {
            it
          }
        }
      }
    }
  }

  fun restore() {
    viewModelScope.launch {
      store.update { it.copy(importState = ImportState.InProgress) }

      withContext(Dispatchers.IO) {
        QuickRegistrationRepository.setRestoreMethodForOldDevice(RestoreMethod.REMOTE_BACKUP)

        val jobStateFlow = callbackFlow {
          val listener = JobTracker.JobListener { _, jobState ->
            trySend(jobState)
          }

          AppDependencies
            .jobManager
            .startChain(BackupRestoreJob())
            .then(SyncArchivedMediaJob())
            .then(BackupRestoreMediaJob())
            .enqueue(listener)

          awaitClose {
            AppDependencies.jobManager.removeListener(listener)
          }
        }

        jobStateFlow.collect { state ->
          when (state) {
            JobTracker.JobState.SUCCESS -> {
              Log.i(TAG, "Restore successful")
              SignalStore.registration.restoreDecisionState = RestoreDecisionState.Completed

              StorageServiceRestore.restore()

              store.update { it.copy(importState = ImportState.Restored) }
            }

            JobTracker.JobState.PENDING,
            JobTracker.JobState.RUNNING -> {
              Log.i(TAG, "Restore job states updated: $state")
            }

            JobTracker.JobState.FAILURE,
            JobTracker.JobState.IGNORED -> {
              Log.w(TAG, "Restore failed with $state")

              store.update { it.copy(importState = ImportState.Failed) }
            }
          }
        }
      }
    }
  }

  fun updateRestoreProgress(restoreEvent: RestoreV2Event) {
    store.update { it.copy(restoreProgress = restoreEvent) }
  }

  fun cancel() {
    SignalStore.registration.restoreDecisionState = RestoreDecisionState.Skipped
  }

  fun clearError() {
    store.update { it.copy(importState = ImportState.None, restoreProgress = null) }
  }

  fun skipRestore() {
    SignalStore.registration.restoreDecisionState = RestoreDecisionState.Skipped

    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        QuickRegistrationRepository.setRestoreMethodForOldDevice(RestoreMethod.DECLINE)
      }
    }
  }

  suspend fun performStorageServiceAccountRestoreIfNeeded() {
    if (SignalStore.account.restoredAccountEntropyPool || SignalStore.svr.masterKeyForInitialDataRestore != null) {
      store.update { it.copy(loadState = ScreenState.LoadState.STORAGE_SERVICE_RESTORE) }
      StorageServiceRestore.restore()
    }
  }

  data class ScreenState(
    val isRemoteRestoreOnlyOption: Boolean = false,
    val backupTier: MessageBackupTier? = null,
    val backupTime: Long = -1,
    val backupSize: ByteSize = 0.bytes,
    val importState: ImportState = ImportState.None,
    val restoreProgress: RestoreV2Event? = null,
    val loadState: LoadState = if (backupTier != null) LoadState.LOADED else LoadState.LOADING
  ) {

    fun isLoaded(): Boolean {
      return loadState == LoadState.LOADED
    }

    enum class LoadState {
      LOADING, LOADED, NOT_FOUND, FAILURE, STORAGE_SERVICE_RESTORE
    }
  }

  sealed interface ImportState {
    data object None : ImportState
    data object InProgress : ImportState
    data object Restored : ImportState
    data object Failed : ImportState
  }
}
