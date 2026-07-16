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
import org.signal.registration.RegistrationRoute
import org.signal.registration.RestoreDecision
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
) : EventDrivenViewModel<PinEntryScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(PinEntryForSvrRestoreViewModel::class)
  }

  private val _state = MutableStateFlow(
    PinEntryState(
      mode = PinEntryState.Mode.SvrRestore
    )
  )

  val state: StateFlow<PinEntryState> = _state.asStateFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: PinEntryScreenEvents) {
    applyEvent(state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: PinEntryState,
    event: PinEntryScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (PinEntryState) -> Unit
  ) {
    when (event) {
      is PinEntryScreenEvents.PinEntered -> {
        val localState = state.copy(loading = true)
        stateEmitter(localState)
        stateEmitter(applyPinEntered(localState, event, parentEventEmitter))
      }
      is PinEntryScreenEvents.Skip -> {
        handleSkip()
      }
      is PinEntryScreenEvents.CreateNewPin -> {
        Log.i(TAG, "[CreateNewPin] User opted to create a new PIN after no data was found. Navigating to PIN creation.")
        stateEmitter(state.copy(showNoDataToRestoreDialog = false))
        parentEventEmitter.navigateTo(RegistrationRoute.PinCreate)
      }
      is PinEntryScreenEvents.ContactSupport -> {
        Log.i(TAG, "[ContactSupport] User opted to contact support after no data was found.")
        stateEmitter(state.copy(showNoDataToRestoreDialog = false))
      }
      is PinEntryScreenEvents.ToggleKeyboard,
      is PinEntryScreenEvents.NetworkErrorDialogDismissed,
      is PinEntryScreenEvents.RateLimitedDialogDismissed,
      is PinEntryScreenEvents.UnknownErrorDialogDismissed -> {
        stateEmitter(PinEntryScreenEventHandler.applyEvent(state, event))
      }
      is PinEntryScreenEvents.ParentStateChanged -> Unit
    }
  }

  private suspend fun applyPinEntered(
    state: PinEntryState,
    event: PinEntryScreenEvents.PinEntered,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ): PinEntryState {
    Log.d(TAG, "[PinEntered] Attempting to restore master key from SVR...")

    val svrCredentials = when (val result = repository.getSvrCredentials()) {
      is RequestResult.Success<NetworkController.SvrCredentials> -> {
        result.result
      }
      is RequestResult.NonSuccess<NetworkController.GetSvrCredentialsError> -> {
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
      is RequestResult.RetryableNetworkError -> {
        return state.copy(loading = false, dialogs = state.dialogs.copy(networkError = true))
      }
      is RequestResult.ApplicationError -> {
        return state.copy(loading = false, dialogs = state.dialogs.copy(unknownError = true))
      }
    }

    return when (val result = repository.restoreMasterKeyFromSvr(svrCredentials, event.pin, forRegistrationLock = false)) {
      is RequestResult.Success -> {
        Log.i(TAG, "[PinEntered] Successfully restored master key from SVR.")
        repository.enqueueSvrResetGuessCountJob()
        repository.setRestoreDecision(RestoreDecision.COMPLETED)
        parentEventEmitter(RegistrationFlowEvent.MasterKeyRestoredFromSvr(result.result.masterKey))
        repository.restoreAccountRecord()
        parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
        state
      }
      is RequestResult.NonSuccess -> {
        when (val error = result.error) {
          is NetworkController.RestoreMasterKeyError.WrongPin -> {
            Log.w(TAG, "[PinEntered] Wrong PIN. Tries remaining: ${error.triesRemaining}")
            state.copy(loading = false, triesRemaining = error.triesRemaining)
          }
          is NetworkController.RestoreMasterKeyError.NoDataFound -> {
            Log.w(TAG, "[PinEntered] No SVR data found. Prompting user to create a new PIN.")
            state.copy(loading = false, showNoDataToRestoreDialog = true)
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[PinEntered] Network error when restoring master key.", result.networkError)
        state.copy(loading = false, dialogs = state.dialogs.copy(networkError = true))
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[PinEntered] Application error when restoring master key.", result.cause)
        state.copy(loading = false, dialogs = state.dialogs.copy(unknownError = true))
      }
    }
  }

  private fun handleSkip() {
    Log.i(TAG, "[Skip] User opted to skip restoring their PIN. Navigating to PIN creation.")
    parentEventEmitter.navigateTo(RegistrationRoute.PinCreate)
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
