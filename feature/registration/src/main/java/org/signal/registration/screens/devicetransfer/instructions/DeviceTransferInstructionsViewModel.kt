/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.instructions

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
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo

class DeviceTransferInstructionsViewModel(
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<DeviceTransferInstructionsScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(DeviceTransferInstructionsViewModel::class)
  }

  private val _state = MutableStateFlow(DeviceTransferInstructionsState())
  val state: StateFlow<DeviceTransferInstructionsState> = _state

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: DeviceTransferInstructionsScreenEvents) {
    applyEvent(state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: DeviceTransferInstructionsState,
    event: DeviceTransferInstructionsScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (DeviceTransferInstructionsState) -> Unit
  ) {
    when (event) {
      DeviceTransferInstructionsScreenEvents.ContinueClicked -> {
        parentEventEmitter.navigateTo(RegistrationRoute.DeviceTransferSetup)
      }
      DeviceTransferInstructionsScreenEvents.BackClicked -> {
        parentEventEmitter.navigateBack()
      }
    }
  }

  class Factory(
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DeviceTransferInstructionsViewModel(parentEventEmitter) as T
    }
  }
}
