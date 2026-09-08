/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginmanualsave

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo

/**
 * View model for [SignalLoginViewDetailsForManualSaveScreen].
 */
class SignalLoginViewDetailsForManualSaveViewModel(
  parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<SignalLoginViewDetailsForManualSaveScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(SignalLoginViewDetailsForManualSaveViewModel::class)
  }

  private val _state = MutableStateFlow(SignalLoginViewDetailsForManualSaveState())
  val state: StateFlow<SignalLoginViewDetailsForManualSaveState> = _state.asStateFlow()

  private val _actions = Channel<SignalLoginViewDetailsForManualSaveScreenActions>(Channel.BUFFERED)
  val actions: Flow<SignalLoginViewDetailsForManualSaveScreenActions> = _actions.receiveAsFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    parentState
      .onEach { onEvent(SignalLoginViewDetailsForManualSaveScreenEvents.ParentStateChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: SignalLoginViewDetailsForManualSaveScreenEvents) {
    applyEvent(_state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  fun applyEvent(
    state: SignalLoginViewDetailsForManualSaveState,
    event: SignalLoginViewDetailsForManualSaveScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (SignalLoginViewDetailsForManualSaveState) -> Unit
  ) {
    when (event) {
      is SignalLoginViewDetailsForManualSaveScreenEvents.ParentStateChanged -> {
        stateEmitter(
          state.copy(
            accountId = event.parentState.aci?.toString()?.uppercase().orEmpty(),
            recoveryKey = event.parentState.accountEntropyPool?.displayValue.orEmpty()
          )
        )
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.BackClicked -> {
        parentEventEmitter.navigateBack()
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.CopyAccountIdClicked -> {
        _actions.trySend(SignalLoginViewDetailsForManualSaveScreenActions.CopyTextToClipboard(event.accountId))
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.CopyRecoveryKeyClicked -> {
        _actions.trySend(SignalLoginViewDetailsForManualSaveScreenActions.CopyTextToClipboard(event.recoveryKey))
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.SaveAsPdfClicked -> {
        _actions.trySend(SignalLoginViewDetailsForManualSaveScreenActions.LaunchSaveAsPdf)
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.ContinueClicked -> {
        stateEmitter(state.copy(showConfirmSavedSheet = true))
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.ConfirmSavedContinueClicked -> {
        stateEmitter(state.copy(showConfirmSavedSheet = false))
        parentEventEmitter.navigateTo(RegistrationRoute.SignalLoginManualSaveConfirmation)
      }

      is SignalLoginViewDetailsForManualSaveScreenEvents.ShowLoginInfoAgainClicked,
      is SignalLoginViewDetailsForManualSaveScreenEvents.ConfirmSavedSheetDismissed -> {
        stateEmitter(state.copy(showConfirmSavedSheet = false))
      }
    }
  }
}
