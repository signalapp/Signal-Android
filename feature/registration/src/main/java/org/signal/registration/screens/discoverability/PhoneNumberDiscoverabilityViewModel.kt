/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.discoverability

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.screens.EventDrivenViewModel
import org.signal.registration.screens.util.navigateBack

class PhoneNumberDiscoverabilityViewModel(
  initialDiscoverable: Boolean,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val resultBus: ResultEventBus,
  private val resultKey: String
) : EventDrivenViewModel<PhoneNumberDiscoverabilityScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(PhoneNumberDiscoverabilityViewModel::class)
  }

  private val _state = MutableStateFlow(PhoneNumberDiscoverabilityState(discoverable = initialDiscoverable))
  val state: StateFlow<PhoneNumberDiscoverabilityState> = _state

  override suspend fun processEvent(event: PhoneNumberDiscoverabilityScreenEvents) {
    applyEvent(state.value, event, parentEventEmitter, resultBus, resultKey) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: PhoneNumberDiscoverabilityState,
    event: PhoneNumberDiscoverabilityScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    resultBus: ResultEventBus,
    resultKey: String,
    stateEmitter: (PhoneNumberDiscoverabilityState) -> Unit
  ) {
    when (event) {
      PhoneNumberDiscoverabilityScreenEvents.EveryoneSelected -> {
        stateEmitter(state.copy(discoverable = true))
      }
      PhoneNumberDiscoverabilityScreenEvents.NobodySelected -> {
        stateEmitter(state.copy(showNobodyConfirmation = true))
      }
      PhoneNumberDiscoverabilityScreenEvents.NobodyConfirmed -> {
        stateEmitter(state.copy(discoverable = false, showNobodyConfirmation = false))
      }
      PhoneNumberDiscoverabilityScreenEvents.NobodyDismissed -> {
        stateEmitter(state.copy(showNobodyConfirmation = false))
      }
      PhoneNumberDiscoverabilityScreenEvents.SaveClicked -> {
        resultBus.sendResult(resultKey, state.discoverable)
        parentEventEmitter.navigateBack()
      }
      PhoneNumberDiscoverabilityScreenEvents.BackClicked -> {
        parentEventEmitter.navigateBack()
      }
    }
  }

  class Factory(
    private val initialDiscoverable: Boolean,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val resultBus: ResultEventBus,
    private val resultKey: String
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return PhoneNumberDiscoverabilityViewModel(initialDiscoverable, parentEventEmitter, resultBus, resultKey) as T
    }
  }
}
