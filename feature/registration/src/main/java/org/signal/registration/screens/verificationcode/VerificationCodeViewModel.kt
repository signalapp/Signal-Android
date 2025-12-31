/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

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
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo
import org.signal.registration.screens.verificationcode.VerificationCodeState.OneTimeEvent

class VerificationCodeViewModel(
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(VerificationCodeViewModel::class)
  }

  private val _localState = MutableStateFlow(VerificationCodeState())
  val state = combine(_localState, parentState) { state, parentState -> applyParentState(state, parentState) }
    .onEach { Log.d(TAG, "[State] $it") }
    .stateIn(viewModelScope, SharingStarted.Eagerly, VerificationCodeState())

  fun onEvent(event: VerificationCodeScreenEvents) {
    viewModelScope.launch {
      _localState.emit(applyEvent(state.value, event))
    }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: VerificationCodeState, event: VerificationCodeScreenEvents): VerificationCodeState {
    return when (event) {
      is VerificationCodeScreenEvents.CodeEntered -> transformCodeEntered(state, event.code)
      is VerificationCodeScreenEvents.WrongNumber -> state.also { parentEventEmitter.navigateTo(RegistrationRoute.PhoneNumberEntry) }
      is VerificationCodeScreenEvents.ResendSms -> transformResendCode(state, NetworkController.VerificationCodeTransport.SMS)
      is VerificationCodeScreenEvents.CallMe -> transformResendCode(state, NetworkController.VerificationCodeTransport.VOICE)
      is VerificationCodeScreenEvents.HavingTrouble -> TODO("having trouble flow")
      is VerificationCodeScreenEvents.ConsumeInnerOneTimeEvent -> state.copy(oneTimeEvent = null)
    }
  }

  @VisibleForTesting
  fun applyParentState(state: VerificationCodeState, parentState: RegistrationFlowState): VerificationCodeState {
    if (parentState.sessionMetadata == null || parentState.sessionE164 == null) {
      Log.w(TAG, "Parent state is missing session metadata or e164! Resetting.")
      parentEventEmitter(RegistrationFlowEvent.ResetState)
      return state
    }

    return state.copy(
      sessionMetadata = parentState.sessionMetadata,
      e164 = parentState.sessionE164
    )
  }

  private suspend fun transformCodeEntered(inputState: VerificationCodeState, code: String): VerificationCodeState {
    var state = inputState.copy()
    var sessionMetadata = state.sessionMetadata ?: return state.also { parentEventEmitter(RegistrationFlowEvent.ResetState) }

    // TODO should we be checking on whether we need to do more captcha stuff?

    val result = repository.submitVerificationCode(sessionMetadata.id, code)

    sessionMetadata = when (result) {
      is NetworkController.RegistrationNetworkResult.Success -> {
        result.data
      }
      is NetworkController.RegistrationNetworkResult.Failure -> {
        when (result.error) {
          is NetworkController.SubmitVerificationCodeError.InvalidSessionIdOrVerificationCode -> {
            Log.w(TAG, "[SubmitCode] Invalid sessionId or verification code entered. This is distinct from an *incorrect* verification code. Body: ${result.error.message}")
            return state.copy(oneTimeEvent = OneTimeEvent.IncorrectVerificationCode)
          }
          is NetworkController.SubmitVerificationCodeError.SessionNotFound -> {
            Log.w(TAG, "[SubmitCode] Session not found: ${result.error.message}")
            // TODO don't start over, go back to phone number entry
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            return state
          }
          is NetworkController.SubmitVerificationCodeError.SessionAlreadyVerifiedOrNoCodeRequested -> {
            if (result.error.session.verified) {
              Log.i(TAG, "[SubmitCode] Session already had number verified, continuing with registration.")
              result.error.session
            } else {
              Log.w(TAG, "[SubmitCode] No code was requested for this session? Need to have user re-submit.")
              parentEventEmitter.navigateBack()
              return state
            }
          }
          is NetworkController.SubmitVerificationCodeError.RateLimited -> {
            Log.w(TAG, "[SubmitCode] Rate limited.")
            return state.copy(oneTimeEvent = OneTimeEvent.RateLimited(result.error.retryAfter))
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        return state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        Log.w(TAG, "[SubmitCode] Unknown error when submitting verification code.", result.exception)
        return state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
      }
    }

    state = state.copy(sessionMetadata = sessionMetadata)

    if (!sessionMetadata.verified) {
      Log.w(TAG, "[SubmitCode] Verification code was incorrect.")
      return state.copy(oneTimeEvent = OneTimeEvent.IncorrectVerificationCode)
    }

    // Attempt to register
    val registerResult = repository.registerAccount(e164 = state.e164, sessionId = sessionMetadata.id, skipDeviceTransfer = true)

    return when (registerResult) {
      is NetworkController.RegistrationNetworkResult.Success -> {
        val (response, keyMaterial) = registerResult.data

        parentEventEmitter(RegistrationFlowEvent.Registered(keyMaterial.accountEntropyPool))

        if (response.storageCapable) {
          parentEventEmitter.navigateTo(RegistrationRoute.PinEntryForSvrRestore)
        } else {
          parentEventEmitter.navigateTo(RegistrationRoute.PinCreate)
        }
        state
      }
      is NetworkController.RegistrationNetworkResult.Failure -> {
        when (registerResult.error) {
          is NetworkController.RegisterAccountError.SessionNotFoundOrNotVerified -> {
            TODO()
          }
          is NetworkController.RegisterAccountError.DeviceTransferPossible -> {
            Log.w(TAG, "[Register] Got told a device transfer is possible. We should never get into this state. Resetting.")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RegisterAccountError.RegistrationLock -> {
            Log.w(TAG, "[Register] Reglocked.")
            parentEventEmitter.navigateTo(
              RegistrationRoute.PinEntryForRegistrationLock(
                timeRemaining = registerResult.error.data.timeRemaining,
                svrCredentials = registerResult.error.data.svr2Credentials
              )
            )
            state
          }
          is NetworkController.RegisterAccountError.RateLimited -> {
            Log.w(TAG, "[Register] Rate limited.")
            state.copy(oneTimeEvent = OneTimeEvent.RateLimited(registerResult.error.retryAfter))
          }
          is NetworkController.RegisterAccountError.InvalidRequest -> {
            Log.w(TAG, "[Register] Invalid request when registering account: ${registerResult.error.message}")
            state.copy(oneTimeEvent = OneTimeEvent.RegistrationError)
          }
          is NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect -> {
            Log.w(TAG, "[Register] Registration recovery password incorrect: ${registerResult.error.message}")
            state.copy(oneTimeEvent = OneTimeEvent.RegistrationError)
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        Log.w(TAG, "[Register] Network error.", registerResult.exception)
        state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        Log.w(TAG, "[Register] Unknown error when registering account.", registerResult.exception)
        state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
      }
    }
  }

  private suspend fun transformResendCode(
    inputState: VerificationCodeState,
    transport: NetworkController.VerificationCodeTransport
  ): VerificationCodeState {
    val state = inputState.copy()
    if (state.sessionMetadata == null) {
      parentEventEmitter(RegistrationFlowEvent.ResetState)
      return inputState
    }

    val sessionMetadata = state.sessionMetadata

    val result = repository.requestVerificationCode(
      sessionId = sessionMetadata.id,
      smsAutoRetrieveCodeSupported = false,
      transport = transport
    )

    return when (result) {
      is NetworkController.RegistrationNetworkResult.Success -> {
        state.copy(sessionMetadata = result.data)
      }
      is NetworkController.RegistrationNetworkResult.Failure -> {
        when (result.error) {
          is NetworkController.RequestVerificationCodeError.InvalidRequest -> {
            state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
          }
          is NetworkController.RequestVerificationCodeError.RateLimited -> {
            state.copy(oneTimeEvent = OneTimeEvent.RateLimited(result.error.retryAfter))
          }
          is NetworkController.RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport -> {
            state.copy(oneTimeEvent = OneTimeEvent.CouldNotRequestCodeWithSelectedTransport)
          }
          is NetworkController.RequestVerificationCodeError.InvalidSessionId -> {
            // TODO don't start over, go back to phone number entry
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.MissingRequestInformationOrAlreadyVerified -> {
            Log.w(TAG, "When requesting verification code, missing request information or already verified.")
            state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
          }
          is NetworkController.RequestVerificationCodeError.SessionNotFound -> {
            // TODO don't start over, go back to phone number entry
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.ThirdPartyServiceError -> {
            state.copy(oneTimeEvent = OneTimeEvent.ThirdPartyError)
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        Log.w(TAG, "Unknown error when requesting verification code.", result.exception)
        state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
      }
    }
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return VerificationCodeViewModel(repository, parentState, parentEventEmitter) as T
    }
  }
}
