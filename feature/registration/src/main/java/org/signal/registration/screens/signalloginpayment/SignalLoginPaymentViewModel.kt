/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

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
import org.signal.registration.RegistrationRepository
import org.signal.registration.screens.util.navigateBack

class SignalLoginPaymentViewModel(
  private val repository: RegistrationRepository,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<SignalLoginPaymentScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(SignalLoginPaymentViewModel::class)
  }

  private val _state = MutableStateFlow(SignalLoginPaymentState())
  val state: StateFlow<SignalLoginPaymentState> = _state.asStateFlow()

  private val _actions = Channel<SignalLoginPaymentScreenActions>(Channel.BUFFERED)
  val actions: Flow<SignalLoginPaymentScreenActions> = _actions.receiveAsFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    onEvent(SignalLoginPaymentScreenEvents.Initialize)
  }

  override suspend fun processEvent(event: SignalLoginPaymentScreenEvents) {
    applyEvent(_state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: SignalLoginPaymentState,
    event: SignalLoginPaymentScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (SignalLoginPaymentState) -> Unit
  ) {
    when (event) {
      is SignalLoginPaymentScreenEvents.Initialize -> {
        // TODO [phonenumberless] Load the price from the billing library and populate SignalLoginPaymentState.formattedPrice.
      }

      is SignalLoginPaymentScreenEvents.BackClicked -> {
        parentEventEmitter.navigateBack()
      }

      is SignalLoginPaymentScreenEvents.LearnMoreClicked -> {
        _actions.trySend(SignalLoginPaymentScreenActions.OpenLearnMoreArticle)
      }

      is SignalLoginPaymentScreenEvents.OptionSelected -> {
        stateEmitter(state.copy(selectedOption = event.option))
      }

      is SignalLoginPaymentScreenEvents.ContinueClicked -> {
        // TODO [phonenumberless] Launch the purchase flow, or navigate to account key entry for an existing login.
        Log.i(TAG, "Continue clicked for ${state.selectedOption}, but the flow isn't implemented yet.")
      }

      is SignalLoginPaymentScreenEvents.NetworkErrorDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(networkError = false)))
      }

      is SignalLoginPaymentScreenEvents.UnknownErrorDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(unknownError = false)))
      }

      is SignalLoginPaymentScreenEvents.PurchaseFailedDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(purchaseFailed = false)))
      }
    }
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return SignalLoginPaymentViewModel(repository, parentEventEmitter) as T
    }
  }
}
