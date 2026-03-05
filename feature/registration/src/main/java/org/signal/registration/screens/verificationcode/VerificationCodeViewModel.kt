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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class VerificationCodeViewModel(
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(VerificationCodeViewModel::class)
  }

  private val _localState = MutableStateFlow(VerificationCodeState())
  val state = combine(_localState, parentState) { state, parentState -> applyParentState(state, parentState) }
    .onEach { Log.d(TAG, "[State] $it") }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VerificationCodeState())

  private var nextSmsAvailableAt: Duration = 0.seconds
  private var nextCallAvailableAt: Duration = 0.seconds

  fun onEvent(event: VerificationCodeScreenEvents) {
    viewModelScope.launch {
      val stateEmitter: (VerificationCodeState) -> Unit = { newState ->
        _localState.value = newState
      }
      applyEvent(state.value, event, stateEmitter)
    }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: VerificationCodeState, event: VerificationCodeScreenEvents, stateEmitter: (VerificationCodeState) -> Unit) {
    val result = when (event) {
      is VerificationCodeScreenEvents.CodeEntered -> {
        stateEmitter(state.copy(isSubmittingCode = true))
        applyCodeEntered(state, event.code).copy(isSubmittingCode = false)
      }
      is VerificationCodeScreenEvents.WrongNumber -> state.also { parentEventEmitter.navigateTo(RegistrationRoute.PhoneNumberEntry) }
      is VerificationCodeScreenEvents.ResendSms -> applyResendCode(state, NetworkController.VerificationCodeTransport.SMS)
      is VerificationCodeScreenEvents.CallMe -> applyResendCode(state, NetworkController.VerificationCodeTransport.VOICE)
      is VerificationCodeScreenEvents.HavingTrouble -> throw NotImplementedError("having trouble flow") // TODO [registration] - Having trouble flow
      is VerificationCodeScreenEvents.ConsumeInnerOneTimeEvent -> state.copy(oneTimeEvent = null)
      is VerificationCodeScreenEvents.CountdownTick -> applyCountdownTick(state)
    }
    stateEmitter(result)
  }

  @VisibleForTesting
  fun applyParentState(state: VerificationCodeState, parentState: RegistrationFlowState): VerificationCodeState {
    if (parentState.sessionMetadata == null || parentState.sessionE164 == null) {
      Log.w(TAG, "Parent state is missing session metadata or e164! Resetting.")
      parentEventEmitter(RegistrationFlowEvent.ResetState)
      return state
    }

    val sessionChanged = state.sessionMetadata?.id != parentState.sessionMetadata.id

    val rateLimits = if (sessionChanged) {
      computeRateLimits(parentState.sessionMetadata)
    } else {
      state.rateLimits
    }

    return state.copy(
      sessionMetadata = parentState.sessionMetadata,
      e164 = parentState.sessionE164,
      rateLimits = rateLimits
    )
  }

  /**
   * Decrements countdown timers by 1 second, ensuring they don't go below 0.
   */
  private fun applyCountdownTick(state: VerificationCodeState): VerificationCodeState {
    return state.copy(
      rateLimits = SmsAndCallRateLimits(
        smsResendTimeRemaining = (state.rateLimits.smsResendTimeRemaining - 1.seconds).coerceAtLeast(0.seconds),
        callRequestTimeRemaining = (state.rateLimits.callRequestTimeRemaining - 1.seconds).coerceAtLeast(0.seconds)
      )
    )
  }

  private suspend fun applyCodeEntered(inputState: VerificationCodeState, code: String): VerificationCodeState {
    var state = inputState
    var sessionMetadata = state.sessionMetadata ?: return state.also {
      parentEventEmitter(RegistrationFlowEvent.ResetState)
    }

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
            val newAttempts = state.incorrectCodeAttempts + 1
            return state.copy(oneTimeEvent = OneTimeEvent.IncorrectVerificationCode, incorrectCodeAttempts = newAttempts)
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
            Log.w(TAG, "[SubmitCode] Rate limited  (retryAfter: ${result.error.retryAfter}).")
            return state.copy(oneTimeEvent = OneTimeEvent.RateLimited(result.error.retryAfter))
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        Log.w(TAG, "[SubmitCode] Network error.", result.exception)
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
      val newAttempts = state.incorrectCodeAttempts + 1
      return state.copy(oneTimeEvent = OneTimeEvent.IncorrectVerificationCode, incorrectCodeAttempts = newAttempts)
    }

    // Attempt to register
    val registerResult = repository.registerAccountWithSession(e164 = state.e164, sessionId = sessionMetadata.id, skipDeviceTransfer = true)

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
            // TODO [registration] Handle session not found or not verified case.
            throw NotImplementedError("Handle session not found or not verified case.")
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
            Log.w(TAG, "[Register] Rate limited (retryAfter: ${registerResult.error.retryAfter}).")
            state.copy(oneTimeEvent = OneTimeEvent.RateLimited(registerResult.error.retryAfter))
          }
          is NetworkController.RegisterAccountError.InvalidRequest -> {
            Log.w(TAG, "[Register] Invalid request when registering account: ${registerResult.error.message}")
            state.copy(oneTimeEvent = OneTimeEvent.RegistrationError)
          }
          is NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect -> {
            Log.w(TAG, "[Register] Got told the registration recovery password incorrect. We don't use the RRP in this flow, and should never get this error. Resetting. Message: ${registerResult.error.message}")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
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

  private suspend fun applyResendCode(
    state: VerificationCodeState,
    transport: NetworkController.VerificationCodeTransport
  ): VerificationCodeState {
    if (state.sessionMetadata == null) {
      parentEventEmitter(RegistrationFlowEvent.ResetState)
      return state
    }

    val result = repository.requestVerificationCode(
      sessionId = state.sessionMetadata.id,
      smsAutoRetrieveCodeSupported = false,
      transport = transport
    )

    return when (result) {
      is NetworkController.RegistrationNetworkResult.Success -> {
        Log.i(TAG, "[RequestCode][$transport] Successfully requested verification code.")
        parentEventEmitter(RegistrationFlowEvent.SessionUpdated(result.data))
        state.copy(
          sessionMetadata = result.data,
          rateLimits = computeRateLimits(result.data)
        )
      }
      is NetworkController.RegistrationNetworkResult.Failure -> {
        when (result.error) {
          is NetworkController.RequestVerificationCodeError.InvalidRequest -> {
            Log.w(TAG, "[RequestCode][$transport] Invalid request: ${result.error.message}")
            state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
          }
          is NetworkController.RequestVerificationCodeError.RateLimited -> {
            Log.w(TAG, "[RequestCode][$transport] Rate limited (retryAfter: ${result.error.retryAfter}).")
            parentEventEmitter(RegistrationFlowEvent.SessionUpdated(result.error.session))
            state.copy(
              oneTimeEvent = OneTimeEvent.RateLimited(result.error.retryAfter),
              sessionMetadata = result.error.session,
              rateLimits = computeRateLimits(result.error.session)
            )
          }
          is NetworkController.RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport -> {
            Log.w(TAG, "[RequestCode][$transport] Could not fulfill with requested transport.")
            parentEventEmitter(RegistrationFlowEvent.SessionUpdated(result.error.session))
            state.copy(
              oneTimeEvent = OneTimeEvent.CouldNotRequestCodeWithSelectedTransport,
              sessionMetadata = result.error.session,
              rateLimits = computeRateLimits(result.error.session)
            )
          }
          is NetworkController.RequestVerificationCodeError.InvalidSessionId -> {
            Log.w(TAG, "[RequestCode][$transport] Invalid session ID: ${result.error.message}")
            // TODO don't start over, go back to phone number entry
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.MissingRequestInformationOrAlreadyVerified -> {
            Log.w(TAG, "[RequestCode][$transport] Missing request information or already verified.")
            parentEventEmitter(RegistrationFlowEvent.SessionUpdated(result.error.session))
            state.copy(
              oneTimeEvent = OneTimeEvent.NetworkError,
              sessionMetadata = result.error.session,
              rateLimits = computeRateLimits(result.error.session)
            )
          }
          is NetworkController.RequestVerificationCodeError.SessionNotFound -> {
            Log.w(TAG, "[RequestCode][$transport] Session not found: ${result.error.message}")
            // TODO don't start over, go back to phone number entry
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.ThirdPartyServiceError -> {
            Log.w(TAG, "[RequestCode][$transport] Third party service error. ${result.error.data}")
            state.copy(oneTimeEvent = OneTimeEvent.ThirdPartyError)
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        Log.w(TAG, "[RequestCode][$transport] Network error.", result.exception)
        state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        Log.w(TAG, "[RequestCode][$transport] Unknown application error.", result.exception)
        state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
      }
    }
  }

  private fun computeRateLimits(session: NetworkController.SessionMetadata): SmsAndCallRateLimits {
    val now = clock().milliseconds
    nextSmsAvailableAt = now + (session.nextSms?.seconds ?: nextSmsAvailableAt)
    nextCallAvailableAt = now + (session.nextCall?.seconds ?: nextCallAvailableAt)

    return SmsAndCallRateLimits(
      smsResendTimeRemaining = (nextSmsAvailableAt - clock().milliseconds).coerceAtLeast(0.seconds),
      callRequestTimeRemaining = (nextCallAvailableAt - clock().milliseconds).coerceAtLeast(0.seconds)
    )
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
