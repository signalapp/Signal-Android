/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.remotebackuprestore

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.screens.EventDrivenViewModel
import org.signal.registration.screens.util.navigateBack

class RemoteBackupRestoreViewModel(
  private val aep: AccountEntropyPool,
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<RemoteBackupRestoreScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(RemoteBackupRestoreViewModel::class)
  }

  private val _state = MutableStateFlow(RemoteBackupRestoreState(aep))

  val state: StateFlow<RemoteBackupRestoreState> = _state
    .onEach { Log.d(TAG, "[State] $it") }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RemoteBackupRestoreState(aep))

  init {
    loadBackupInfo()
  }

  override suspend fun processEvent(event: RemoteBackupRestoreScreenEvents) {
    applyEvent(state.value, event) {
      _state.value = it
    }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: RemoteBackupRestoreState, event: RemoteBackupRestoreScreenEvents, stateEmitter: (RemoteBackupRestoreState) -> Unit) {
    when (event) {
      is RemoteBackupRestoreScreenEvents.BackupRestoreBackup -> {
        stateEmitter(state.copy(restoreState = RemoteBackupRestoreState.RestoreState.InProgress))
        restoreBackup()
      }
      is RemoteBackupRestoreScreenEvents.Retry -> {
        loadBackupInfo()
        stateEmitter(state)
      }
      is RemoteBackupRestoreScreenEvents.Cancel -> {
        parentEventEmitter.navigateBack()
        stateEmitter(state)
      }
      is RemoteBackupRestoreScreenEvents.DismissError -> {
        stateEmitter(state.copy(restoreState = RemoteBackupRestoreState.RestoreState.None, restoreProgress = null))
      }
    }
  }

  private fun restoreBackup() {
    viewModelScope.launch {
      repository.restoreRemoteBackup(_state.value.aep).collect { progress ->
        when (progress) {
          is RemoteBackupRestoreProgress.Downloading -> {
            _state.value = _state.value.copy(
              restoreState = RemoteBackupRestoreState.RestoreState.InProgress,
              restoreProgress = RemoteBackupRestoreState.RestoreProgress(
                phase = RemoteBackupRestoreState.RestoreProgress.Phase.Downloading,
                bytesCompleted = progress.bytesDownloaded,
                totalBytes = progress.totalBytes
              )
            )
          }
          is RemoteBackupRestoreProgress.Restoring -> {
            _state.value = _state.value.copy(
              restoreState = RemoteBackupRestoreState.RestoreState.InProgress,
              restoreProgress = RemoteBackupRestoreState.RestoreProgress(
                phase = RemoteBackupRestoreState.RestoreProgress.Phase.Restoring,
                bytesCompleted = progress.bytesRead,
                totalBytes = progress.totalBytes
              )
            )
          }
          is RemoteBackupRestoreProgress.Finalizing -> {
            _state.value = _state.value.copy(
              restoreState = RemoteBackupRestoreState.RestoreState.InProgress,
              restoreProgress = RemoteBackupRestoreState.RestoreProgress(
                phase = RemoteBackupRestoreState.RestoreProgress.Phase.Finalizing,
                bytesCompleted = 0,
                totalBytes = 0
              )
            )
          }
          is RemoteBackupRestoreProgress.Complete -> {
            Log.i(TAG, "Remote restore completed successfully")
            _state.value = _state.value.copy(
              restoreState = RemoteBackupRestoreState.RestoreState.Restored,
              restoreProgress = null
            )
            parentEventEmitter(RegistrationFlowEvent.UserSuppliedAepVerified(aep))
            parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
          }
          is RemoteBackupRestoreProgress.NetworkError -> {
            Log.w(TAG, "Remote restore failed with network error", progress.cause)
            _state.value = _state.value.copy(
              restoreState = RemoteBackupRestoreState.RestoreState.NetworkFailure,
              restoreProgress = null
            )
          }
          is RemoteBackupRestoreProgress.InvalidBackupVersion -> {
            Log.w(TAG, "Remote restore failed: invalid backup version")
            _state.value = _state.value.copy(
              restoreState = RemoteBackupRestoreState.RestoreState.InvalidBackupVersion,
              restoreProgress = null
            )
          }
          is RemoteBackupRestoreProgress.PermanentSvrBFailure -> {
            Log.w(TAG, "Remote restore failed: permanent SVR-B failure")
            _state.value = _state.value.copy(
              restoreState = RemoteBackupRestoreState.RestoreState.PermanentSvrBFailure,
              restoreProgress = null
            )
          }
          is RemoteBackupRestoreProgress.Canceled -> {
            Log.w(TAG, "Remote restore was canceled")
            _state.value = _state.value.copy(
              restoreState = RemoteBackupRestoreState.RestoreState.Failed,
              restoreProgress = null
            )
          }
          is RemoteBackupRestoreProgress.GenericError -> {
            Log.w(TAG, "Remote restore failed", progress.cause)
            _state.value = _state.value.copy(
              restoreState = RemoteBackupRestoreState.RestoreState.Failed,
              restoreProgress = null
            )
          }
        }
      }
    }
  }

  private fun loadBackupInfo() {
    viewModelScope.launch {
      _state.value = _state.value.copy(loadState = RemoteBackupRestoreState.LoadState.Loading, loadAttempts = _state.value.loadAttempts + 1)

      val result = withContext(Dispatchers.IO) {
        repository.getRemoteBackupInfo(_state.value.aep)
      }

      when (result) {
        is RequestResult.Success -> {
          val info = result.result

//          parentEventEmitter(RegistrationFlowEvent)

          val lastModifiedResult = withContext(Dispatchers.IO) {
            repository.getBackupFileLastModified(_state.value.aep, info)
          }

          val backupTime = when (lastModifiedResult) {
            is RequestResult.Success -> lastModifiedResult.result
            else -> {
              Log.w(TAG, "Failed to get backup last modified time: $lastModifiedResult")
              -1L
            }
          }

          _state.value = _state.value.copy(
            loadState = RemoteBackupRestoreState.LoadState.Loaded,
            backupSize = info.usedSpace ?: 0,
            backupTime = backupTime
          )
        }
        is RequestResult.NonSuccess -> {
          _state.value = when (result.error) {
            is NetworkController.GetBackupInfoError.NoBackup -> _state.value.copy(loadState = RemoteBackupRestoreState.LoadState.NotFound)
            else -> _state.value.copy(loadState = RemoteBackupRestoreState.LoadState.Failure)
          }
        }
        is RequestResult.RetryableNetworkError -> {
          _state.value = _state.value.copy(loadState = RemoteBackupRestoreState.LoadState.Failure)
        }
        is RequestResult.ApplicationError -> {
          _state.value = _state.value.copy(loadState = RemoteBackupRestoreState.LoadState.Failure)
        }
      }
    }
  }

  class Factory(
    private val aep: AccountEntropyPool,
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return RemoteBackupRestoreViewModel(aep, repository, parentState, parentEventEmitter) as T
    }
  }
}
