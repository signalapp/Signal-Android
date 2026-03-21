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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.signal.registration.PendingRestoreOption
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.EventDrivenViewModel
import org.signal.registration.screens.util.navigateTo

class ArchiveRestoreSelectionViewModel(
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val isPreRegistration: Boolean
) : EventDrivenViewModel<ArchiveRestoreSelectionScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(ArchiveRestoreSelectionViewModel::class)
  }

  private val _localState = MutableStateFlow(ArchiveRestoreSelectionState())
  val state = combine(_localState, parentState) { state, parentState -> applyParentState(state, parentState) }
    .onEach { Log.d(TAG, "[State] $it") }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArchiveRestoreSelectionState())

  init {
    viewModelScope.launch {
      val options = repository.getAvailableRestoreOptions()
      _localState.value = _localState.value.copy(restoreOptions = options.toList())
    }
  }

  override suspend fun processEvent(event: ArchiveRestoreSelectionScreenEvents) {
    applyEvent(state.value, event) { _localState.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: ArchiveRestoreSelectionState, event: ArchiveRestoreSelectionScreenEvents, stateEmitter: (ArchiveRestoreSelectionState) -> Unit) {
    val result = when (event) {
      is ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected -> {
        when (event.option) {
          ArchiveRestoreOption.LocalBackup -> {
            if (isPreRegistration) {
              parentEventEmitter(RegistrationFlowEvent.PendingRestoreOptionSelected(PendingRestoreOption.LocalBackup))
              parentEventEmitter.navigateTo(RegistrationRoute.PhoneNumberEntry)
            } else {
              parentEventEmitter.navigateTo(RegistrationRoute.LocalBackupRestore(isPreRegistration = false))
            }
            state
          }
          ArchiveRestoreOption.SignalSecureBackup -> {
            if (isPreRegistration) {
              parentEventEmitter(RegistrationFlowEvent.PendingRestoreOptionSelected(PendingRestoreOption.RemoteBackup))
              parentEventEmitter.navigateTo(RegistrationRoute.PhoneNumberEntry)
            } else {
              Log.w(TAG, "Signal secure backup restore not yet implemented")
            }
            state
          }
          ArchiveRestoreOption.DeviceTransfer -> {
            Log.w(TAG, "Device transfer not yet implemented")
            state
          }
          ArchiveRestoreOption.None -> {
            if (isPreRegistration) {
              parentEventEmitter.navigateTo(RegistrationRoute.PhoneNumberEntry)
              state
            } else {
              state.copy(showSkipRestoreWarning = true)
            }
          }
        }
      }
      is ArchiveRestoreSelectionScreenEvents.Skip -> {
        if (isPreRegistration) {
          parentEventEmitter.navigateTo(RegistrationRoute.PhoneNumberEntry)
          state
        } else {
          state.copy(showSkipRestoreWarning = true)
        }
      }
      is ArchiveRestoreSelectionScreenEvents.ConfirmSkip -> {
        parentEventEmitter.navigateTo(RegistrationRoute.PinCreate)
        state.copy(showSkipRestoreWarning = false)
      }
      is ArchiveRestoreSelectionScreenEvents.DismissSkipWarning -> {
        state.copy(showSkipRestoreWarning = false)
      }
    }
    stateEmitter(result)
  }

  @VisibleForTesting
  fun applyParentState(state: ArchiveRestoreSelectionState, parentState: RegistrationFlowState): ArchiveRestoreSelectionState {
    return state
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val isPreRegistration: Boolean
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return ArchiveRestoreSelectionViewModel(repository, parentState, parentEventEmitter, isPreRegistration) as T
    }
  }
}
