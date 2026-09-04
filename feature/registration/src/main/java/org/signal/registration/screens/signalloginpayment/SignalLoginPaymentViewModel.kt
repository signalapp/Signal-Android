/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

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
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.RegistrationApiV2.RegisterAccountError
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo

class SignalLoginPaymentViewModel(
  private val repository: RegistrationRepository,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<SignalLoginPaymentScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(SignalLoginPaymentViewModel::class)
  }

  private val _state = MutableStateFlow(SignalLoginPaymentState(showManualReceiptCredentialEntry = repository.isDebugBuild))
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

      is SignalLoginPaymentScreenEvents.ManualReceiptCredentialChanged -> {
        stateEmitter(state.copy(manualReceiptCredential = event.value))
      }

      is SignalLoginPaymentScreenEvents.ContinueClicked -> {
        if (state.manualReceiptCredential.isNotBlank) {
          var localState = state.copy(showSpinner = true)
          stateEmitter(localState)
          localState = applyManualReceiptCredentialSubmitted(localState, parentEventEmitter)
          stateEmitter(localState.copy(showSpinner = false))
        } else if (state.selectedOption == SignalLoginPaymentState.Option.ExistingLogin) {
          parentEventEmitter.navigateTo(RegistrationRoute.SignalLoginCredentialEntry())
        } else {
          // TODO [phonenumberless] Launch the purchase flow.
          Log.i(TAG, "Continue clicked for ${state.selectedOption}, but the purchase flow isn't implemented yet.")
        }
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

      is SignalLoginPaymentScreenEvents.InvalidReceiptCredentialDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(invalidReceiptCredential = false)))
      }
    }
  }

  /**
   * Redeems the manually-pasted receipt credential by building its presentation and registering a numberless account
   * with it, bypassing the (unfinished) purchase flow entirely.
   */
  private suspend fun applyManualReceiptCredentialSubmitted(
    state: SignalLoginPaymentState,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ): SignalLoginPaymentState {
    val presentation = try {
      repository.createReceiptCredentialPresentation(state.manualReceiptCredential.decode())
    } catch (e: Exception) {
      Log.w(TAG, "[ManualReceipt] The pasted value could not be parsed as a receipt credential.", e)
      return state.copy(dialogs = state.dialogs.copy(invalidReceiptCredential = true))
    }

    return when (val result = repository.registerAccountWithoutPhoneNumber(presentation)) {
      is RequestResult.Success -> {
        Log.i(TAG, "[ManualReceipt] Successfully registered without a phone number.")
        val (response, keyMaterial, aci) = result.result

        parentEventEmitter(RegistrationFlowEvent.Registered(aci, keyMaterial.accountEntropyPool, response.storageCapable, phoneNumberless = response.e164 == null))
        parentEventEmitter.navigateTo(RegistrationRoute.SignalLoginInfo)
        state
      }
      is RequestResult.NonSuccess -> {
        when (val error = result.error) {
          is RegisterAccountError.InvalidReceiptCredentialPresentation -> {
            Log.w(TAG, "[ManualReceipt] The service rejected the receipt credential presentation. Message: ${error.message}")
            state.copy(dialogs = state.dialogs.copy(invalidReceiptCredential = true))
          }
          is RegisterAccountError.DeviceTransferPossible -> {
            Log.w(TAG, "[ManualReceipt] Got told a device transfer is possible despite asking to skip it.")
            state.copy(dialogs = state.dialogs.copy(unknownError = true))
          }
          is RegisterAccountError.InvalidRequest -> {
            Log.w(TAG, "[ManualReceipt] Invalid request. Message: ${error.message}")
            state.copy(dialogs = state.dialogs.copy(unknownError = true))
          }
          is RegisterAccountError.RateLimited -> {
            Log.w(TAG, "[ManualReceipt] Rate limited (retryAfter: ${error.retryAfter}).")
            state.copy(dialogs = state.dialogs.copy(unknownError = true))
          }
          else -> {
            Log.w(TAG, "[ManualReceipt] Unexpected registration error for a numberless registration: $error")
            state.copy(dialogs = state.dialogs.copy(unknownError = true))
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[ManualReceipt] Network error.", result.networkError)
        state.copy(dialogs = state.dialogs.copy(networkError = true))
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[ManualReceipt] Application error.", result.cause)
        state.copy(dialogs = state.dialogs.copy(unknownError = true))
      }
    }
  }
}
