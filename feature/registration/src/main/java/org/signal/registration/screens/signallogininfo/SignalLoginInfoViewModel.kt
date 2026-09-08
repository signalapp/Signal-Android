/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogininfo

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateTo

/**
 * View model for [SignalLoginInfoScreen].
 */
class SignalLoginInfoViewModel(
  private val repository: RegistrationRepository,
  parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  isPasswordManagerAvailable: Boolean
) : EventDrivenViewModel<SignalLoginInfoScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(SignalLoginInfoViewModel::class)
  }

  private val _state = MutableStateFlow(SignalLoginInfoState(isPasswordManagerAvailable = isPasswordManagerAvailable))
  val state: StateFlow<SignalLoginInfoState> = _state.asStateFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    parentState
      .onEach { onEvent(SignalLoginInfoScreenEvents.ParentStateChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: SignalLoginInfoScreenEvents) {
    applyEvent(_state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: SignalLoginInfoState,
    event: SignalLoginInfoScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (SignalLoginInfoState) -> Unit
  ) {
    when (event) {
      is SignalLoginInfoScreenEvents.ParentStateChanged -> {
        stateEmitter(state.copy(aci = event.parentState.aci, aep = event.parentState.accountEntropyPool))
      }

      is SignalLoginInfoScreenEvents.ViewDetailsClicked -> {
        parentEventEmitter.navigateTo(RegistrationRoute.SignalLoginViewDetails)
      }

      is SignalLoginInfoScreenEvents.SaveToPasswordManagerClicked -> {
        // TODO [phonenumberless] Store the credentials via the credential manager before advancing.
        parentEventEmitter.navigateTo(RegistrationRoute.AddUsername)
      }

      is SignalLoginInfoScreenEvents.SaveManuallyClicked -> {
        parentEventEmitter.navigateTo(RegistrationRoute.SignalLoginViewDetailsForManualSave)
      }

      is SignalLoginInfoScreenEvents.SaveFailedDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(saveFailed = false)))
      }

      is SignalLoginInfoScreenEvents.UnknownErrorDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(unknownError = false)))
      }
    }
  }
}
