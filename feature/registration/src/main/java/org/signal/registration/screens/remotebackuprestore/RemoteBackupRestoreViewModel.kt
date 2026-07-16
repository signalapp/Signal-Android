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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.RestoreDecision
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo
import kotlin.coroutines.CoroutineContext

class RemoteBackupRestoreViewModel(
  private val aep: AccountEntropyPool,
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val ioDispatcher: CoroutineContext = Dispatchers.IO
) : EventDrivenViewModel<RemoteBackupRestoreScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(RemoteBackupRestoreViewModel::class)
  }

  private val _state = MutableStateFlow(RemoteBackupRestoreState(aep))
  val state: StateFlow<RemoteBackupRestoreState> = _state.asStateFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

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
            Log.i(TAG, "[restoreBackup] Restoring...")
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
            Log.i(TAG, "[restoreBackup] Restoring...")
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
            Log.i(TAG, "[restoreBackup] Finalizing...")
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
            Log.i(TAG, "[restoreBackup] Remote restore completed successfully.")
            _state.value = _state.value.copy(
              restoreState = RemoteBackupRestoreState.RestoreState.Restored,
              restoreProgress = null
            )
            repository.persistRestoredBackupState(progress.restoredSvrPin, progress.restoredProfileKey)
            repository.setRestoreDecision(RestoreDecision.COMPLETED)

            when {
              repository.hasKnownPin() -> {
                repository.restoreAccountRecord()
                parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
              }
              parentState.value.storageCapable -> {
                Log.i(TAG, "[restoreBackup] No PIN is known and the account is storage capable. Navigating to PIN entry to restore the existing PIN.")
                parentEventEmitter.navigateTo(RegistrationRoute.PinEntryForSvrRestore)
              }
              else -> {
                Log.i(TAG, "[restoreBackup] No PIN is known and the account is not storage capable. Navigating to PIN creation.")
                parentEventEmitter.navigateTo(RegistrationRoute.PinCreate)
              }
            }
          }
          is RemoteBackupRestoreProgress.NetworkError -> {
            Log.w(TAG, "[restoreBackup] Remote restore failed with network error.", progress.cause)
            _state.value = _state.value.copy(
              restoreState = RemoteBackupRestoreState.RestoreState.NetworkFailure,
              restoreProgress = null
            )
          }
          is RemoteBackupRestoreProgress.InvalidBackupVersion -> {
            Log.w(TAG, "[restoreBackup] Remote restore failed: invalid backup version.")
            _state.value = _state.value.copy(
              restoreState = RemoteBackupRestoreState.RestoreState.InvalidBackupVersion,
              restoreProgress = null
            )
          }
          is RemoteBackupRestoreProgress.PermanentSvrBFailure -> {
            Log.w(TAG, "[restoreBackup] Remote restore failed: permanent SVRB failure.")
            _state.value = _state.value.copy(
              restoreState = RemoteBackupRestoreState.RestoreState.PermanentSvrBFailure,
              restoreProgress = null
            )
          }
          is RemoteBackupRestoreProgress.Canceled -> {
            Log.w(TAG, "[restoreBackup] Remote restore was canceled.")
            _state.value = _state.value.copy(
              restoreState = RemoteBackupRestoreState.RestoreState.Failed,
              restoreProgress = null
            )
          }
          is RemoteBackupRestoreProgress.GenericError -> {
            Log.w(TAG, "[restoreBackup] Remote restore failed.", progress.cause)
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

      val result = withContext(ioDispatcher) {
        repository.getRemoteBackupInfo(_state.value.aep)
      }

      when (result) {
        is RequestResult.Success -> {
          Log.i(TAG, "[loadBackupInfo] Successfully fetched backup info.")
          parentEventEmitter(RegistrationFlowEvent.UserSuppliedAepVerified(aep))
          val info = result.result

          val lastModifiedResult = withContext(ioDispatcher) {
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
          _state.value = when (val error = result.error) {
            is NetworkController.GetBackupInfoError.NoBackup -> {
              Log.w(TAG, "[loadBackupInfo] No backup found.")
              _state.value.copy(loadState = RemoteBackupRestoreState.LoadState.NotFound)
            }
            is NetworkController.GetBackupInfoError.BadArguments -> {
              Log.w(TAG, "[loadBackupInfo] Failed with bad arguments. Body: ${error.body}")
              _state.value.copy(loadState = RemoteBackupRestoreState.LoadState.Failure)
            }
            is NetworkController.GetBackupInfoError.BadAuthCredential -> {
              Log.w(TAG, "[loadBackupInfo] Bad auth credential. Body: ${error.body}")
              _state.value.copy(loadState = RemoteBackupRestoreState.LoadState.Failure)
            }
            is NetworkController.GetBackupInfoError.Forbidden -> {
              Log.w(TAG, "[loadBackupInfo] Forbidden. Body: ${error.body}")
              _state.value.copy(loadState = RemoteBackupRestoreState.LoadState.Failure)
            }
            is NetworkController.GetBackupInfoError.RateLimited -> {
              Log.w(TAG, "[loadBackupInfo] Rate limited. Try again in: ${error.retryAfter}")
              _state.value.copy(loadState = RemoteBackupRestoreState.LoadState.Failure)
            }
          }
        }
        is RequestResult.RetryableNetworkError -> {
          Log.w(TAG, "[loadBackupInfo] Hit network error.", result.networkError)
          _state.value = _state.value.copy(loadState = RemoteBackupRestoreState.LoadState.Failure)
        }
        is RequestResult.ApplicationError -> {
          Log.w(TAG, "[loadBackupInfo] Hit unexpected error.", result.cause)
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
