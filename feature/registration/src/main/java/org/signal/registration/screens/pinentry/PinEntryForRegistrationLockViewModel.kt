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
import org.signal.core.models.MasterKey
import org.signal.core.util.logging.Log
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateTo

/**
 * ViewModel for the registration lock PIN entry screen.
 *
 * This screen is shown when the user attempts to register and their account is protected by a registration lock (PIN).
 * The user must enter their PIN to proceed with registration.
 */
class PinEntryForRegistrationLockViewModel(
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val timeRemaining: Long,
  private val svrCredentials: NetworkController.SvrCredentials
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(PinEntryForRegistrationLockViewModel::class)
  }

  private val _state = MutableStateFlow(
    PinEntryState(
      mode = PinEntryState.Mode.RegistrationLock
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
  suspend fun applyEvent(state: PinEntryState, event: PinEntryScreenEvents, stateEmitter: (PinEntryState) -> Unit, parentEventEmitter: (RegistrationFlowEvent) -> Unit) {
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

  private suspend fun applyPinEntered(state: PinEntryState, event: PinEntryScreenEvents.PinEntered, parentEventEmitter: (RegistrationFlowEvent) -> Unit): PinEntryState {
    Log.d(TAG, "[PinEntered] Attempting to restore master key from SVR...")

    val restoreResult = repository.restoreMasterKeyFromSvr(svrCredentials, event.pin, state.isAlphanumericKeyboard, forRegistrationLock = true)

    val masterKey: MasterKey = when (restoreResult) {
      is NetworkController.RegistrationNetworkResult.Success -> {
        Log.i(TAG, "[PinEntered] Successfully restored master key from SVR.")
        restoreResult.data.masterKey
      }
      is NetworkController.RegistrationNetworkResult.Failure -> {
        return when (restoreResult.error) {
          is NetworkController.RestoreMasterKeyError.WrongPin -> {
            Log.w(TAG, "[PinEntered] Wrong PIN. Tries remaining: ${restoreResult.error.triesRemaining}")
            state.copy(triesRemaining = restoreResult.error.triesRemaining)
          }
          is NetworkController.RestoreMasterKeyError.NoDataFound -> {
            Log.w(TAG, "[PinEntered] No SVR data found. Account is locked.")
            parentEventEmitter.navigateTo(RegistrationRoute.AccountLocked(timeRemainingMs = timeRemaining))
            state
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        Log.w(TAG, "[PinEntered] Network error when restoring master key.", restoreResult.exception)
        return state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.NetworkError)
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        Log.w(TAG, "[PinEntered] Application error when restoring master key.", restoreResult.exception)
        return state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.UnknownError)
      }
    }

    parentEventEmitter(RegistrationFlowEvent.MasterKeyRestoredViaRegistrationLock(masterKey))

    val registrationLockToken = masterKey.deriveRegistrationLock()

    val e164 = parentState.value.sessionE164
    val sessionId = parentState.value.sessionMetadata?.id

    if (e164 == null || sessionId == null) {
      Log.w(TAG, "[PinEntered] Missing e164 or sessionId. Resetting state.")
      parentEventEmitter(RegistrationFlowEvent.ResetState)
      return state
    }

    Log.d(TAG, "[PinEntered] Attempting to register with registration lock token...")
    val registerResult = repository.registerAccount(
      e164 = e164,
      sessionId = sessionId,
      registrationLock = registrationLockToken,
      skipDeviceTransfer = true
    )

    return when (registerResult) {
      is NetworkController.RegistrationNetworkResult.Success -> {
        Log.i(TAG, "[PinEntered] Successfully registered!")
        val (response, keyMaterial) = registerResult.data
        parentEventEmitter(RegistrationFlowEvent.Registered(keyMaterial.accountEntropyPool))
        // TODO storage service restore + profile screen
        parentEventEmitter.navigateTo(RegistrationRoute.FullyComplete)
        state
      }
      is NetworkController.RegistrationNetworkResult.Failure -> {
        when (registerResult.error) {
          is NetworkController.RegisterAccountError.SessionNotFoundOrNotVerified -> {
            Log.w(TAG, "[PinEntered] Session not found or verified: ${registerResult.error.message}")
            TODO()
          }
          is NetworkController.RegisterAccountError.RegistrationLock -> {
            Log.w(TAG, "[PinEntered] Still getting registration lock error after providing token. This shouldn't happen. Resetting state.")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RegisterAccountError.RateLimited -> {
            Log.w(TAG, "[PinEntered] Rate limited when registering. Retry After: ${registerResult.error.retryAfter}")
            state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.RateLimited(registerResult.error.retryAfter))
          }
          is NetworkController.RegisterAccountError.InvalidRequest -> {
            Log.w(TAG, "[PinEntered] Invalid request when registering: ${registerResult.error.message}")
            state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.UnknownError)
          }
          is NetworkController.RegisterAccountError.DeviceTransferPossible -> {
            Log.w(TAG, "[PinEntered] Device transfer possible. This shouldn't happen when skipDeviceTransfer is true.")
            state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.UnknownError)
          }
          is NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect -> {
            Log.w(TAG, "[PinEntered] Registration recovery password incorrect: ${registerResult.error.message}")
            TODO()
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        Log.w(TAG, "[PinEntered] Network error when registering.", registerResult.exception)
        state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.NetworkError)
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        Log.w(TAG, "[PinEntered] Application error when registering.", registerResult.exception)
        state.copy(oneTimeEvent = PinEntryState.OneTimeEvent.UnknownError)
      }
    }
  }

  private fun handleSkip() {
    Log.d(TAG, "Skip requested - this will result in account data loss after timeRemaining: $timeRemaining ms")
    // TODO: Show confirmation dialog warning about data loss, then proceed without PIN
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val timeRemaining: Long,
    private val svrCredentials: NetworkController.SvrCredentials
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return PinEntryForRegistrationLockViewModel(
        repository,
        parentState,
        parentEventEmitter,
        timeRemaining,
        svrCredentials
      ) as T
    }
  }
}
