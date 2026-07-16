/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restoreselection

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.PendingRestoreOption
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.RestoreDecision
import org.signal.registration.screens.util.navigateTo

/**
 * A view model to be used with [ArchiveRestoreSelectionScreen] after a quick restore.
 * To avoid spinners, we'll have the quick restore screen determine if a remote backup
 * is available and tell us.
 */
class ArchiveRestoreSelectionViewModel(
  private val restoreOptions: List<ArchiveRestoreOption>,
  private val registeredState: RegisteredState,
  private val repository: RegistrationRepository,
  parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val knownAep: AccountEntropyPool? = null
) : EventDrivenViewModel<ArchiveRestoreSelectionScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(ArchiveRestoreSelectionViewModel::class)
  }

  private val _state = MutableStateFlow(
    ArchiveRestoreSelectionState(
      restoreOptions = restoreOptions
    )
  )
  val state: StateFlow<ArchiveRestoreSelectionState> = _state.asStateFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    parentState
      .onEach { onEvent(ArchiveRestoreSelectionScreenEvents.ParentStateChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: ArchiveRestoreSelectionScreenEvents) {
    applyEvent(_state.value, event) { _state.value = it }
  }

  private fun applyParentState(state: ArchiveRestoreSelectionState, parentState: RegistrationFlowState): ArchiveRestoreSelectionState {
    return state.copy(restoreMethodToken = parentState.restoreMethodToken, storageCapable = parentState.storageCapable)
  }

  @VisibleForTesting
  suspend fun applyEvent(state: ArchiveRestoreSelectionState, event: ArchiveRestoreSelectionScreenEvents, stateEmitter: (ArchiveRestoreSelectionState) -> Unit) {
    val result = when (event) {
      is ArchiveRestoreSelectionScreenEvents.ParentStateChanged -> {
        applyParentState(state, event.parentState)
      }
      is ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected -> {
        when (event.option) {
          ArchiveRestoreOption.SignalSecureBackup -> {
            notifyOldDevice(state.restoreMethodToken, NetworkController.RestoreMethod.REMOTE_BACKUP)
            when {
              registeredState == RegisteredState.NotRegistered -> {
                parentEventEmitter(RegistrationFlowEvent.PendingRestoreOptionSelected(PendingRestoreOption.RemoteBackup))
                parentEventEmitter.navigateTo(RegistrationRoute.PhoneNumberEntry)
              }
              knownAep != null -> {
                parentEventEmitter.navigateTo(RegistrationRoute.RemoteRestore(knownAep))
              }
              else -> {
                parentEventEmitter.navigateTo(RegistrationRoute.EnterAepForRemoteBackupPostRegistration)
              }
            }
            state
          }
          ArchiveRestoreOption.LocalBackup -> {
            notifyOldDevice(state.restoreMethodToken, NetworkController.RestoreMethod.LOCAL_BACKUP)
            when (registeredState) {
              RegisteredState.NotRegistered -> {
                parentEventEmitter(RegistrationFlowEvent.PendingRestoreOptionSelected(PendingRestoreOption.LocalBackup))
                parentEventEmitter.navigateTo(RegistrationRoute.PhoneNumberEntry)
              }
              else -> {
                parentEventEmitter.navigateTo(RegistrationRoute.LocalBackupRestore(isPreRegistration = false, aep = knownAep))
              }
            }
            state
          }
          ArchiveRestoreOption.DeviceTransfer -> {
            notifyOldDevice(state.restoreMethodToken, NetworkController.RestoreMethod.DEVICE_TRANSFER)
            parentEventEmitter.navigateTo(RegistrationRoute.DeviceTransferInstructions)
            state
          }
          ArchiveRestoreOption.None -> {
            state.copy(showSkipWarningDialog = true)
          }
        }
      }
      is ArchiveRestoreSelectionScreenEvents.ConfirmSkip -> {
        when (registeredState) {
          RegisteredState.NotRegistered -> {
            parentEventEmitter.navigateTo(RegistrationRoute.PhoneNumberEntry)
            state.copy(showSkipWarningDialog = false)
          }
          RegisteredState.RegisteredAndPinUnknown -> {
            notifyOldDevice(state.restoreMethodToken, NetworkController.RestoreMethod.DECLINE)
            repository.setRestoreDecision(RestoreDecision.SKIPPED)
            if (state.storageCapable) {
              Log.i(TAG, "[ConfirmSkip] Account is storage capable. Navigating to PIN entry to restore the existing PIN.")
              parentEventEmitter.navigateTo(RegistrationRoute.PinEntryForSvrRestore)
            } else {
              Log.i(TAG, "[ConfirmSkip] Account is not storage capable. Navigating to PIN creation.")
              parentEventEmitter.navigateTo(RegistrationRoute.PinCreate)
            }
            state.copy(showSkipWarningDialog = false)
          }
          RegisteredState.RegisteredAndPinKnown -> {
            notifyOldDevice(state.restoreMethodToken, NetworkController.RestoreMethod.DECLINE)
            repository.setRestoreDecision(RestoreDecision.SKIPPED)
            parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
            state.copy(showSkipWarningDialog = false)
          }
        }
      }
      is ArchiveRestoreSelectionScreenEvents.DismissSkipWarning -> {
        state.copy(showSkipWarningDialog = false)
      }
    }
    stateEmitter(result)
  }

  /**
   * If a quick-restore [token] is set, fire-and-forget a network call to update the old device's UI
   * with the user's [method] selection. The old device is long-polling and will pick up the change.
   */
  private fun notifyOldDevice(token: String?, method: NetworkController.RestoreMethod) {
    if (token == null) return
    viewModelScope.launch {
      Log.i(TAG, "[notifyOldDevice] Notifying old device of restore method: $method")
      val result = repository.setRestoreMethod(token, method)
      if (result is RequestResult.Success) {
        Log.i(TAG, "[notifyOldDevice] Successfully notified old device.")
      } else {
        Log.w(TAG, "[notifyOldDevice] Failed to notify old device: $result")
      }
    }
  }

  class Factory(
    private val restoreOptions: List<ArchiveRestoreOption>,
    private val registeredState: RegisteredState,
    private val knownAep: AccountEntropyPool?,
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return ArchiveRestoreSelectionViewModel(restoreOptions, registeredState, repository, parentState, parentEventEmitter, knownAep) as T
    }
  }
}
