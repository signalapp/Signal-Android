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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.signal.core.models.MasterKey
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.util.Hex
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo
import org.signal.registration.util.SensitiveLog

/**
 * ViewModel for the SMS-bypass PIN entry screen.
 *
 * This screen is shown when we have a known-valid SVR credential for the entered phone number,
 * allowing the user to restore their master key and bypass SMS verification.
 */
class PinEntryForSmsBypassViewModel(
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val svrCredentials: NetworkController.SvrCredentials
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(PinEntryForSmsBypassViewModel::class)
  }

  private val _state = MutableStateFlow(
    PinEntryState(
      mode = PinEntryState.Mode.SmsBypass
    )
  )

  val state: StateFlow<PinEntryState> = _state
    .combine(parentState) { state, parentState -> applyParentState(state, parentState) }
    .onEach { Log.d(TAG, "[State] $it") }
    .stateIn(viewModelScope, SharingStarted.Eagerly, PinEntryState(showNeedHelp = true))

  fun onEvent(event: PinEntryScreenEvents) {
    viewModelScope.launch {
      val stateEmitter: (PinEntryState) -> Unit = { _state.value = it }
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

  fun applyParentState(state: PinEntryState, parentState: RegistrationFlowState): PinEntryState {
    return state.copy(e164 = parentState.sessionE164)
  }

  private suspend fun applyPinEntered(
    state: PinEntryState,
    event: PinEntryScreenEvents.PinEntered,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ): PinEntryState {
    Log.d(TAG, "[PinEntered] Attempting to restore master key from SVR...")

    if (state.e164 == null) {
      Log.w(TAG, "[PinEntered] No e164 available! Shouldn't be in this state. Resetting.")
      parentEventEmitter(RegistrationFlowEvent.ResetState)
      return state
    }

    return when (val result = repository.restoreMasterKeyFromSvr(svrCredentials, event.pin, state.isAlphanumericKeyboard, forRegistrationLock = false)) {
      is NetworkController.RegistrationNetworkResult.Success -> {
        Log.i(TAG, "[PinEntered] Successfully restored master key from SVR.")
        parentEventEmitter(RegistrationFlowEvent.MasterKeyRestoredFromSvr(result.data.masterKey))
        attemptToRegister(state, state.e164, result.data.masterKey, provideRegistrationLock = false, parentEventEmitter)
      }
      is NetworkController.RegistrationNetworkResult.Failure -> {
        when (result.error) {
          is NetworkController.RestoreMasterKeyError.WrongPin -> {
            Log.w(TAG, "[PinEntered] Wrong PIN. Tries remaining: ${result.error.triesRemaining}")
            state.copy(triesRemaining = result.error.triesRemaining)
          }
          is NetworkController.RestoreMasterKeyError.NoDataFound -> {
            Log.w(TAG, "[PinEntered] No SVR data found for sms-bypass credential. Marking RRP as invalid and navigating back.")
            parentEventEmitter(RegistrationFlowEvent.RecoveryPasswordInvalid)
            parentEventEmitter.navigateBack()
            state
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        Log.w(TAG, "[PinEntered] Network error when restoring master key (sms-bypass).", result.exception)
        state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.NetworkError)
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        Log.w(TAG, "[PinEntered] Application error when restoring master key (sms-bypass).", result.exception)
        state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.UnknownError)
      }
    }
  }

  private fun handleSkip() {
    // TODO: Decide desired behavior (likely return to phone number entry).
    Log.d(TAG, "[Skip] Not yet implemented.")
  }

  private suspend fun attemptToRegister(
    state: PinEntryState,
    e164: String,
    masterKey: MasterKey,
    provideRegistrationLock: Boolean,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ): PinEntryState {
    val recoveryPassword = masterKey.deriveRegistrationRecoveryPassword()
    val registrationLock = masterKey.deriveRegistrationLock().takeIf { provideRegistrationLock }

    SensitiveLog.d(TAG, "Attempting registration using master key [${Hex.toStringCondensed(masterKey.serialize())}] and RRP [$recoveryPassword]")

    return when (val result = repository.registerAccountWithRecoveryPassword(e164, recoveryPassword, registrationLock, skipDeviceTransfer = true)) {
      is NetworkController.RegistrationNetworkResult.Success -> {
        parentEventEmitter.navigateTo(RegistrationRoute.FullyComplete)
        repository.enqueueSvrResetGuessCountJob()
        state
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.NetworkError)
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.UnknownError)
      }
      is NetworkController.RegistrationNetworkResult.Failure -> {
        when (result.error) {
          NetworkController.RegisterAccountError.DeviceTransferPossible -> {
            Log.w(TAG, "[Register] Got told a device transfer is possible. We should never get into this state. Resetting.")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RegisterAccountError.InvalidRequest -> {
            Log.w(TAG, "[Register] Invalid request when registering account with RRP. Marking RRP as invalid and navigating back. Message: ${result.error.message}")
            parentEventEmitter(RegistrationFlowEvent.RecoveryPasswordInvalid)
            parentEventEmitter.navigateBack()
            state
          }
          is NetworkController.RegisterAccountError.RateLimited -> {
            Log.w(TAG, "[Register] Rate limited (retryAfter: ${result.error.retryAfter}).")
            state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.RateLimited(result.error.retryAfter))
          }
          is NetworkController.RegisterAccountError.RegistrationLock -> {
            if (provideRegistrationLock) {
              Log.w(TAG, "[Register] Hit reglock error when supplying RRP with reglock. This shouldn't happen and implies that the RRP is likely invalid. Marking RRP as invalid and navigating back.")
              parentEventEmitter(RegistrationFlowEvent.RecoveryPasswordInvalid)
              parentEventEmitter.navigateBack()
              state
            } else {
              Log.w(TAG, "[Register] Hit reglock error when supplying RRP without reglock. Attempting again with reglock.")
              attemptToRegister(state, e164, masterKey, provideRegistrationLock = true, parentEventEmitter)
            }
          }
          is NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect -> {
            Log.w(TAG, "[Register] Told that RRP is incorrect. Marking RRP as invalid and navigating back.")
            parentEventEmitter(RegistrationFlowEvent.RecoveryPasswordInvalid)
            parentEventEmitter.navigateBack()
            state
          }
          is NetworkController.RegisterAccountError.SessionNotFoundOrNotVerified -> {
            Log.w(TAG, "[Register] Got told our session wasn't found when trying to use RRP. We should never get into this state. Resetting.")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
        }
      }
    }
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val svrCredentials: NetworkController.SvrCredentials
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return PinEntryForSmsBypassViewModel(
        repository = repository,
        parentState = parentState,
        parentEventEmitter = parentEventEmitter,
        svrCredentials = svrCredentials
      ) as T
    }
  }
}
