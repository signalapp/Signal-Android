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
import org.signal.registration.ReceiptCredentialResult
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.SignalLoginPriceResult
import org.signal.registration.SignalLoginPurchaseResult
import org.signal.registration.SignalLoginPurchaseStep
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
        val isPurchaseSupported = repository.isGooglePlayBillingAvailable
        val hasUnredeemedPurchase = repository.hasUnredeemedSignalLoginPurchase()

        if (hasUnredeemedPurchase) {
          Log.i(TAG, "[Initialize] The user already has a Signal Login purchase that was never redeemed.")
        }

        val price = if (isPurchaseSupported) {
          loadPrice()
        } else {
          Log.i(TAG, "[Initialize] Google Play billing is unavailable, so a Signal Login cannot be bought here. Offering an existing login only.")
          SignalLoginPaymentState.Price.Unavailable
        }

        val updated = state.copy(
          price = price,
          hasUnredeemedPurchase = hasUnredeemedPurchase,
          isPurchaseSupported = isPurchaseSupported
        )

        stateEmitter(
          if (updated.isPurchaseOptionEnabled) {
            updated
          } else {
            updated.copy(selectedOption = SignalLoginPaymentState.Option.ExistingLogin)
          }
        )
      }

      is SignalLoginPaymentScreenEvents.PriceRetryClicked -> {
        val localState = state.copy(price = SignalLoginPaymentState.Price.Loading)
        stateEmitter(localState)
        stateEmitter(localState.copy(price = loadPrice()))
      }

      is SignalLoginPaymentScreenEvents.BackClicked -> {
        parentEventEmitter.navigateBack()
      }

      is SignalLoginPaymentScreenEvents.LearnMoreClicked -> {
        _actions.trySend(SignalLoginPaymentScreenActions.OpenLearnMoreArticle)
      }

      is SignalLoginPaymentScreenEvents.OptionSelected -> {
        if (event.option == SignalLoginPaymentState.Option.Purchase && !state.isPurchaseOptionEnabled) {
          Log.w(TAG, "[OptionSelected] Ignoring a purchase selection that cannot be acted on.")
        } else {
          stateEmitter(state.copy(selectedOption = event.option))
        }
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
          val localState = state.copy(showSpinner = true)
          stateEmitter(localState)

          when (val step = repository.startOrCompleteSignalLoginPurchase()) {
            is SignalLoginPurchaseStep.LaunchRequired -> {
              // The spinner stays up: PurchaseFlowCompleted clears it once the sheet the UI layer opens resolves.
              _actions.trySend(SignalLoginPaymentScreenActions.LaunchPurchaseFlow(step.launcher))
            }
            is SignalLoginPurchaseStep.Finished -> {
              stateEmitter(applyPurchaseResult(localState, step.result, parentEventEmitter).copy(showSpinner = false))
            }
          }
        }
      }

      is SignalLoginPaymentScreenEvents.PurchaseFlowCompleted -> {
        val result = repository.completeSignalLoginPurchase(event.result)
        stateEmitter(applyPurchaseResult(state, result, parentEventEmitter).copy(showSpinner = false))
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

      is SignalLoginPaymentScreenEvents.PurchaseUnavailableDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(purchaseUnavailable = false)))
      }

      is SignalLoginPaymentScreenEvents.PurchasePendingDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(purchasePending = false)))
      }

      is SignalLoginPaymentScreenEvents.InvalidReceiptCredentialDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(invalidReceiptCredential = false)))
      }
    }
  }

  private suspend fun loadPrice(): SignalLoginPaymentState.Price {
    return when (val result = repository.getSignalLoginPrice()) {
      is SignalLoginPriceResult.Available -> SignalLoginPaymentState.Price.Available(result.formattedPrice)
      SignalLoginPriceResult.Unavailable -> {
        Log.w(TAG, "[loadPrice] A Signal Login cannot be bought here. An earlier log says why.")
        SignalLoginPaymentState.Price.Unavailable
      }
      SignalLoginPriceResult.TransientError -> {
        Log.w(TAG, "[loadPrice] Could not determine a Signal Login price. Offering a retry.")
        SignalLoginPaymentState.Price.TransientError
      }
    }
  }

  /** Folds the outcome of a Signal Login purchase into the state, navigating onward when it registered an account. */
  private fun applyPurchaseResult(
    state: SignalLoginPaymentState,
    result: SignalLoginPurchaseResult,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ): SignalLoginPaymentState {
    return when (result) {
      is SignalLoginPurchaseResult.Registered -> {
        Log.i(TAG, "[Purchase] Successfully registered without a phone number.")
        val (response, keyMaterial, aci) = result.account

        parentEventEmitter(RegistrationFlowEvent.Registered(aci, keyMaterial.accountEntropyPool, response.storageCapable, phoneNumberless = response.e164 == null))
        parentEventEmitter.navigateTo(RegistrationRoute.SignalLoginInfo)
        state.copy(hasUnredeemedPurchase = false)
      }
      SignalLoginPurchaseResult.PurchasePending -> {
        Log.i(TAG, "[Purchase] The payment has not settled yet.")
        state.copy(hasUnredeemedPurchase = true, dialogs = state.dialogs.copy(purchasePending = true))
      }
      SignalLoginPurchaseResult.Cancelled -> {
        Log.i(TAG, "[Purchase] The user cancelled.")
        state
      }
      SignalLoginPurchaseResult.PurchaseUnavailable -> {
        Log.w(TAG, "[Purchase] Google Play cannot sell the product on this device.")
        state.copy(dialogs = state.dialogs.copy(purchaseUnavailable = true))
      }
      SignalLoginPurchaseResult.PurchaseFailed -> {
        Log.w(TAG, "[Purchase] Google Play could not complete the purchase.")
        state.copy(dialogs = state.dialogs.copy(purchaseFailed = true))
      }
      is SignalLoginPurchaseResult.RedemptionFailed -> {
        Log.w(TAG, "[Purchase] The service would not redeem the purchase: ${result.error}")
        state.copy(hasUnredeemedPurchase = true, dialogs = state.dialogs.copy(purchaseFailed = true))
      }
      is SignalLoginPurchaseResult.RegistrationFailed -> {
        Log.w(TAG, "[Purchase] Failed to register with the purchase: ${result.error}")
        state.copy(hasUnredeemedPurchase = true, dialogs = state.dialogs.copy(unknownError = true))
      }
      SignalLoginPurchaseResult.NetworkError -> {
        Log.w(TAG, "[Purchase] Network error during the purchase.")
        state.copy(dialogs = state.dialogs.copy(networkError = true))
      }
      SignalLoginPurchaseResult.UnknownError -> {
        Log.w(TAG, "[Purchase] Unknown error during the purchase.")
        state.copy(dialogs = state.dialogs.copy(unknownError = true))
      }
    }
  }

  /**
   * Redeems the manually-pasted receipt credential by building its presentation and registering a numberless account
   * with it, bypassing the purchase flow entirely.
   */
  private suspend fun applyManualReceiptCredentialSubmitted(
    state: SignalLoginPaymentState,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ): SignalLoginPaymentState {
    val credential = state.manualReceiptCredential.decodeOrNull()
    if (credential == null) {
      Log.w(TAG, "[ManualReceipt] The pasted value could not be parsed as a receipt credential.")
      return state.copy(dialogs = state.dialogs.copy(invalidReceiptCredential = true))
    }

    val presentation = when (val built = repository.createReceiptCredentialPresentation(credential)) {
      is ReceiptCredentialResult.Success -> built.value
      ReceiptCredentialResult.VerificationFailed -> {
        Log.w(TAG, "[ManualReceipt] The pasted credential was not issued by this environment's service.")
        return state.copy(dialogs = state.dialogs.copy(invalidReceiptCredential = true))
      }
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
