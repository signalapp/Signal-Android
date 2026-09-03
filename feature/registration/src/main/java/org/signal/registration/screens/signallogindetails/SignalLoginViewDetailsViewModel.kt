/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogindetails

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import org.signal.registration.screens.util.navigateBack
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreen
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreenEvents
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsState

/**
 * View model backing [SignalLoginViewDetailsScreen] within the registration flow.
 */
class SignalLoginViewDetailsViewModel(
  parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<SignalLoginViewDetailsScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(SignalLoginViewDetailsViewModel::class)
  }

  private val _state = MutableStateFlow(
    SignalLoginViewDetailsState(
      accountKey = parentState.value.aci?.toString()?.uppercase().orEmpty(),
      recoveryKey = parentState.value.accountEntropyPool?.displayValue.orEmpty()
    )
  )
  val state: StateFlow<SignalLoginViewDetailsState> = _state.asStateFlow()

  private val _actions = Channel<SignalLoginViewDetailsScreenActions>(Channel.BUFFERED)
  val actions: Flow<SignalLoginViewDetailsScreenActions> = _actions.receiveAsFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: SignalLoginViewDetailsScreenEvents) {
    applyEvent(event, parentEventEmitter)
  }

  @VisibleForTesting
  fun applyEvent(event: SignalLoginViewDetailsScreenEvents, parentEventEmitter: (RegistrationFlowEvent) -> Unit) {
    when (event) {
      is SignalLoginViewDetailsScreenEvents.BackClicked -> {
        parentEventEmitter.navigateBack()
      }

      is SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked -> {
        _actions.trySend(SignalLoginViewDetailsScreenActions.LaunchSaveToPasswordManager)
      }

      is SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked -> {
        _actions.trySend(SignalLoginViewDetailsScreenActions.LaunchSaveAsPdf)
      }

      is SignalLoginViewDetailsScreenEvents.AccountIdLongClicked -> {
        _actions.trySend(SignalLoginViewDetailsScreenActions.CopyTextToClipboard(event.aci))
      }

      is SignalLoginViewDetailsScreenEvents.RecoveryKeyLongClicked -> {
        _actions.trySend(SignalLoginViewDetailsScreenActions.CopyTextToClipboard(event.aep))
      }
    }
  }

  class Factory(
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return SignalLoginViewDetailsViewModel(parentState, parentEventEmitter) as T
    }
  }
}
