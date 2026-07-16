/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.complete

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RestoreDecision

class DeviceTransferCompleteViewModel(
  private val repository: RegistrationRepository,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<DeviceTransferCompleteScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(DeviceTransferCompleteViewModel::class)
  }

  private val _state = MutableStateFlow(DeviceTransferCompleteState())
  val state: StateFlow<DeviceTransferCompleteState> = _state

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: DeviceTransferCompleteScreenEvents) {
    applyEvent(state.value, event, parentEventEmitter, repository) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: DeviceTransferCompleteState,
    event: DeviceTransferCompleteScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    repository: RegistrationRepository,
    stateEmitter: (DeviceTransferCompleteState) -> Unit
  ) {
    when (event) {
      DeviceTransferCompleteScreenEvents.ContinueClicked -> {
        repository.setRestoreDecision(RestoreDecision.COMPLETED)
        repository.restoreAccountRecord()
        parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
      }
    }
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DeviceTransferCompleteViewModel(repository, parentEventEmitter) as T
    }
  }
}
