/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pincreation

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RestoreDecision
import kotlin.time.toKotlinDuration

/**
 * ViewModel for the PIN creation screen.
 *
 * Shown post-registration to allow the user to create a PIN.
 */
class PinCreationViewModel(
  private val repository: RegistrationRepository,
  parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<PinCreationScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(PinCreationViewModel::class)
  }

  private val _state = MutableStateFlow(PinCreationState())
  val state: StateFlow<PinCreationState> = _state.asStateFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    parentState
      .onEach { onEvent(PinCreationScreenEvents.ParentStateChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: PinCreationScreenEvents) {
    applyEvent(_state.value, event)
  }

  @VisibleForTesting
  suspend fun applyEvent(state: PinCreationState, event: PinCreationScreenEvents) {
    when (event) {
      is PinCreationScreenEvents.ParentStateChanged -> {
        _state.value = applyParentState(state, event.parentState)
      }
      is PinCreationScreenEvents.PinSubmitted -> {
        when {
          !state.isConfirmEnabled -> {
            Log.d(TAG, "[PinSubmitted] First PIN entered. Asking the user to confirm it.")
            _state.value = state.copy(firstPin = event.pin, isConfirmEnabled = true, pinMismatch = false)
          }

          event.pin != state.firstPin -> {
            Log.w(TAG, "[PinSubmitted] Confirmation PIN did not match. Returning to PIN creation.")
            _state.value = state.copy(isConfirmEnabled = false, firstPin = null, pinMismatch = true)
          }

          else -> {
            Log.d(TAG, "[PinSubmitted] Confirmation PIN matched.")
            val loadingState = state.copy(pinMismatch = false, loading = true)
            _state.value = loadingState
            _state.value = applyPinSubmitted(loadingState, event.pin)
          }
        }
      }

      is PinCreationScreenEvents.ToggleKeyboard -> {
        _state.value = state.copy(
          isAlphanumericKeyboard = !state.isAlphanumericKeyboard
        )
      }

      is PinCreationScreenEvents.LearnMore -> {
        // Handled by the navigation layer, which opens the help URL directly.
      }

      is PinCreationScreenEvents.BackToPinEntry -> {
        _state.value = state.copy(isConfirmEnabled = false, firstPin = null, pinMismatch = false)
      }
      is PinCreationScreenEvents.OptOut -> {
        _state.value = state.copy(isConfirmEnabled = false)
        applyOptOut()
      }
      is PinCreationScreenEvents.ServiceErrorDialogDismissed -> {
        _state.value = state.copy(dialogs = state.dialogs.copy(serviceError = false))
      }
      is PinCreationScreenEvents.NetworkErrorDialogDismissed -> {
        _state.value = state.copy(dialogs = state.dialogs.copy(networkError = null))
      }
    }
  }

  private suspend fun applyOptOut() {
    Log.i(TAG, "[OptOut] User opted out of creating a PIN. Recording choice and completing registration.")
    repository.setPinOptedOut()
    repository.setRestoreDecision(RestoreDecision.NEW_ACCOUNT)
    parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
  }

  private fun applyParentState(state: PinCreationState, parentState: RegistrationFlowState): PinCreationState {
    return state.copy(accountEntropyPool = parentState.accountEntropyPool)
  }

  private suspend fun applyPinSubmitted(state: PinCreationState, pin: String): PinCreationState {
    Log.d(TAG, "[PinSubmitted] Creating PIN and backing up master key to SVR...")

    if (state.accountEntropyPool == null) {
      Log.w(TAG, "[PinSubmitted] Missing account entropy pool. This should not be possible. Resetting.")
      parentEventEmitter(RegistrationFlowEvent.ResetState)
      return state
    }

    val masterKey = state.accountEntropyPool.deriveMasterKey()

    return when (val result = repository.setNewlyCreatedPin(pin, state.isAlphanumericKeyboard, masterKey)) {
      is RequestResult.Success -> {
        Log.i(TAG, "[PinSubmitted] Successfully backed up master key to SVR.")
        repository.setRestoreDecision(RestoreDecision.NEW_ACCOUNT)
        repository.restoreAccountRecord()
        parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
        state
      }

      is RequestResult.NonSuccess -> {
        when (val error = result.error) {
          is NetworkController.BackupMasterKeyError.EnclaveNotFound -> {
            Log.w(TAG, "[PinSubmitted] SVR enclave not found.")
            state.copy(loading = false, dialogs = state.dialogs.copy(serviceError = true))
          }

          is NetworkController.BackupMasterKeyError.NotRegistered -> {
            Log.w(TAG, "[PinSubmitted] Account not registered. This should not happen. Resetting.")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
        }
      }

      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[PinSubmitted] Network error when backing up master key.", result.networkError)
        state.copy(loading = false, dialogs = state.dialogs.copy(networkError = PinCreationState.Dialogs.NetworkError(result.retryAfter?.toKotlinDuration())))
      }

      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[PinSubmitted] Application error when backing up master key.", result.cause)
        state.copy(loading = false, dialogs = state.dialogs.copy(serviceError = true))
      }
    }
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return PinCreationViewModel(
        repository,
        parentState,
        parentEventEmitter
      ) as T
    }
  }
}
