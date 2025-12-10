/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pinentry

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
 * ViewModel for the SVR restore PIN entry screen.
 *
 * This screen is shown after successful registration when the account has `storageCapable = true`, meaning the user has previously backed up data to SVR.
 * The user must enter their PIN to restore their master key and subsequently restore their data.
 */
class PinEntryForSvrRestoreViewModel(
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(PinEntryForSvrRestoreViewModel::class)
  }

  private val _state = MutableStateFlow(
    PinEntryState(
      mode = PinEntryState.Mode.SvrRestore
    )
  )

  val state: StateFlow<PinEntryState> = _state
    .onEach { Log.d(TAG, "[State] $it") }
    .stateIn(viewModelScope, SharingStarted.Eagerly, PinEntryState(showNeedHelp = true))

  fun onEvent(event: PinEntryScreenEvents) {
    viewModelScope.launch {
      val stateEmitter: (PinEntryState) -> Unit = { state ->
        _state.value = state
      }
      applyEvent(state.value, event, stateEmitter, parentEventEmitter)
    }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: PinEntryState,
    event: PinEntryScreenEvents,
    stateEmitter: (PinEntryState) -> Unit,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) {
    when (event) {
      is PinEntryScreenEvents.PinEntered -> {
        var localState = state.copy(loading = true)
        stateEmitter(localState)
        localState = applyPinEntered(localState, event, parentEventEmitter)
        stateEmitter(localState.copy(loading = false))
      }
      is PinEntryScreenEvents.Skip -> {
        handleSkip()
      }
      is PinEntryScreenEvents.ToggleKeyboard,
      is PinEntryScreenEvents.NeedHelp -> {
        stateEmitter(PinEntryScreenEventHandler.applyEvent(state, event))
      }
    }
  }

  private suspend fun applyPinEntered(
    state: PinEntryState,
    event: PinEntryScreenEvents.PinEntered,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ): PinEntryState {
    Log.d(TAG, "[PinEntered] Attempting to restore master key from SVR...")

    val svrCredentials = when (val result = repository.getSvrCredentials()) {
      is NetworkController.RegistrationNetworkResult.Success<NetworkController.SvrCredentials> -> {
        result.data
      }
      is NetworkController.RegistrationNetworkResult.Failure<NetworkController.GetSvrCredentialsError> -> {
        when (result.error) {
          NetworkController.GetSvrCredentialsError.NoServiceCredentialsAvailable -> {
            Log.w(TAG, "[PinEntered] No service credentials available when restoring from SVR. This should not happen. Resetting.")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            return state
          }
          NetworkController.GetSvrCredentialsError.Unauthorized -> {
            Log.w(TAG, "[PinEntered] Service does not think we're authorized. This should not happen. Resetting.")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            return state
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        return state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.NetworkError)
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        return state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.UnknownError)
      }
    }

    return when (val result = repository.restoreMasterKeyFromSvr(svrCredentials, event.pin, state.isAlphanumericKeyboard, forRegistrationLock = false)) {
      is NetworkController.RegistrationNetworkResult.Success -> {
        Log.i(TAG, "[PinEntered] Successfully restored master key from SVR.")
        parentEventEmitter(RegistrationFlowEvent.MasterKeyRestoredViaPostRegisterPinEntry(result.data.masterKey))
        parentEventEmitter.navigateTo(RegistrationRoute.FullyComplete)
        state
      }
      is NetworkController.RegistrationNetworkResult.Failure -> {
        when (result.error) {
          is NetworkController.RestoreMasterKeyError.WrongPin -> {
            Log.w(TAG, "[PinEntered] Wrong PIN. Tries remaining: ${result.error.triesRemaining}")
            state.copy(triesRemaining = result.error.triesRemaining)
          }
          is NetworkController.RestoreMasterKeyError.NoDataFound -> {
            Log.w(TAG, "[PinEntered] No SVR data found. Proceeding without restore.")
            state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.SvrDataMissing)
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        Log.w(TAG, "[PinEntered] Network error when restoring master key.", result.exception)
        state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.NetworkError)
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        Log.w(TAG, "[PinEntered] Application error when restoring master key.", result.exception)
        state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.UnknownError)
      }
    }
  }

  private fun handleSkip() {
    TODO("Handle skip")
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return PinEntryForSvrRestoreViewModel(
        repository,
        parentState,
        parentEventEmitter
      ) as T
    }
  }
}
