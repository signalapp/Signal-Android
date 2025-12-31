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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateTo

/**
 * ViewModel for the PIN creation screen.
 *
 * Shown post-registration to allow the user to create a PIN.
 */
class PinCreationViewModel(
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(PinCreationViewModel::class)
  }

  private val _state = MutableStateFlow(
    PinCreationState(
      inputLabel = "PIN must be at least 4 digits"
    )
  )

  val state: StateFlow<PinCreationState> = _state
    .combine(parentState) { state, parentState -> applyParentState(state, parentState) }
    .onEach { Log.d(TAG, "[State] $it") }
    .stateIn(viewModelScope, SharingStarted.Eagerly, PinCreationState(inputLabel = "PIN must be at least 4 digits"))

  fun onEvent(event: PinCreationScreenEvents) {
    viewModelScope.launch {
      applyEvent(state.value, event)
    }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: PinCreationState, event: PinCreationScreenEvents) {
    when (event) {
      is PinCreationScreenEvents.PinSubmitted -> {
        _state.value = state.copy(isConfirmEnabled = false)
        val result = applyPinSubmitted(state, event.pin)
        _state.value = result
      }
      is PinCreationScreenEvents.ToggleKeyboard -> {
        val newValue = !state.isAlphanumericKeyboard
        _state.value = state.copy(
          isAlphanumericKeyboard = newValue,
          inputLabel = if (newValue) "PIN must be at least 4 digits" else "PIN must be at least 4 characters"
        )
      }
      is PinCreationScreenEvents.LearnMore -> {
        TODO("Show learn more dialog or navigate to help screen")
      }
    }
  }

  @VisibleForTesting
  fun applyParentState(state: PinCreationState, parentState: RegistrationFlowState): PinCreationState {
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
      is NetworkController.RegistrationNetworkResult.Success -> {
        Log.i(TAG, "[PinSubmitted] Successfully backed up master key to SVR.")
        // TODO profile creation
        parentEventEmitter.navigateTo(RegistrationRoute.FullyComplete)
        state
      }
      is NetworkController.RegistrationNetworkResult.Failure -> {
        when (result.error) {
          is NetworkController.BackupMasterKeyError.EnclaveNotFound -> {
            Log.w(TAG, "[PinSubmitted] SVR enclave not found.")
            TODO("Report to UI and indicate to library user that pin could not be created")
          }
          is NetworkController.BackupMasterKeyError.NotRegistered -> {
            Log.w(TAG, "[PinSubmitted] Account not registered. This should not happen. Resetting.")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        Log.w(TAG, "[PinSubmitted] Network error when backing up master key.", result.exception)
        TODO("Report to UI and indicate to library user that pin could not be created")
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        Log.w(TAG, "[PinSubmitted] Application error when backing up master key.", result.exception)
        TODO("Report to UI and indicate to library user that pin could not be created")
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
