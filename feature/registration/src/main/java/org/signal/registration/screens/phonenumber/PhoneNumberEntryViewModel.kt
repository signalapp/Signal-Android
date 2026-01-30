/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.i18n.phonenumbers.AsYouTypeFormatter
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.signal.core.util.logging.Log
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.phonenumber.PhoneNumberEntryState.OneTimeEvent
import org.signal.registration.screens.phonenumber.PhoneNumberEntryState.OneTimeEvent.*
import org.signal.registration.screens.util.navigateTo
import org.signal.registration.screens.verificationcode.VerificationCodeState
import org.signal.registration.screens.verificationcode.VerificationCodeViewModel

class PhoneNumberEntryViewModel(
  val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(PhoneNumberEntryViewModel::class)
    private const val PUSH_CHALLENGE_TIMEOUT_MS = 5000L
  }

  private val phoneNumberUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()
  private var formatter: AsYouTypeFormatter = phoneNumberUtil.getAsYouTypeFormatter("US")

  private val _state = MutableStateFlow(PhoneNumberEntryState())
  val state = _state
    .combine(parentState) { state, parentState -> applyParentState(state, parentState) }
    .onEach { Log.d(TAG, "[State] $it") }
    .stateIn(viewModelScope, SharingStarted.Eagerly, PhoneNumberEntryState())

  fun onEvent(event: PhoneNumberEntryScreenEvents) {
    viewModelScope.launch {
      val stateEmitter: (PhoneNumberEntryState) -> Unit = { state ->
        _state.value = state
      }
      applyEvent(state.value, event, stateEmitter, parentEventEmitter)
    }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: PhoneNumberEntryState, event: PhoneNumberEntryScreenEvents, stateEmitter: (PhoneNumberEntryState) -> Unit, parentEventEmitter: (RegistrationFlowEvent) -> Unit) {
    when (event) {
      is PhoneNumberEntryScreenEvents.CountryCodeChanged -> {
        stateEmitter(applyCountryCodeChanged(state, event.value))
      }
      is PhoneNumberEntryScreenEvents.PhoneNumberChanged -> {
        stateEmitter(applyPhoneNumberChanged(state, event.value))
      }
      is PhoneNumberEntryScreenEvents.PhoneNumberSubmitted -> {
        var localState = state.copy(showFullScreenSpinner = true)
        stateEmitter(localState)
        localState = applyPhoneNumberSubmitted(localState, parentEventEmitter)
        stateEmitter(localState.copy(showFullScreenSpinner = false))
      }
      is PhoneNumberEntryScreenEvents.CountryPicker -> {
        state.also { parentEventEmitter.navigateTo(RegistrationRoute.CountryCodePicker) }
      }
      is PhoneNumberEntryScreenEvents.CaptchaCompleted -> {
        stateEmitter(applyCaptchaCompleted(state, event.token, parentEventEmitter))
      }
      is PhoneNumberEntryScreenEvents.ConsumeOneTimeEvent -> {
        stateEmitter(state.copy(oneTimeEvent = null))
      }
    }
  }

  @VisibleForTesting
  fun applyParentState(state: PhoneNumberEntryState, parentState: RegistrationFlowState): PhoneNumberEntryState {
    return state.copy(
      sessionE164 =  parentState.sessionE164,
      sessionMetadata = parentState.sessionMetadata,
      preExistingRegistrationData = parentState.preExistingRegistrationData
    )
  }

  private fun applyCountryCodeChanged(state: PhoneNumberEntryState, countryCode: String): PhoneNumberEntryState {
    // Only allow digits, max 3 characters
    val sanitized = countryCode.filter { it.isDigit() }.take(3)
    if (sanitized == state.countryCode) return state

    // Try to determine region from country code
    val regionCode = phoneNumberUtil.getRegionCodeForCountryCode(sanitized.toIntOrNull() ?: 0) ?: state.regionCode

    // Reset formatter for new region and reformat the existing national number
    formatter = phoneNumberUtil.getAsYouTypeFormatter(regionCode)
    val formattedNumber = formatNumber(state.nationalNumber)

    return state.copy(
      countryCode = sanitized,
      regionCode = regionCode,
      formattedNumber = formattedNumber
    )
  }

  private fun applyPhoneNumberChanged(state: PhoneNumberEntryState, input: String): PhoneNumberEntryState {
    // Extract only digits from the input
    val digitsOnly = input.filter { it.isDigit() }
    if (digitsOnly == state.nationalNumber) return state

    // Format the number using AsYouTypeFormatter
    val formattedNumber = formatNumber(digitsOnly)

    return state.copy(
      nationalNumber = digitsOnly,
      formattedNumber = formattedNumber
    )
  }

  private suspend fun applyPhoneNumberSubmitted(
    inputState: PhoneNumberEntryState,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ): PhoneNumberEntryState {
    val e164 = "+${inputState.countryCode}${inputState.nationalNumber}"
    var state = inputState.copy()

    // If we're re-registering for the same number we used to be registered for, we should try to skip right to registration
    if (state.preExistingRegistrationData?.e164 == e164) {
      val masterKey = state.preExistingRegistrationData.aep.deriveMasterKey()
      val recoveryPassword = masterKey.deriveRegistrationRecoveryPassword()
      val registrationLock = masterKey.deriveRegistrationLock()

      when (val registerResult = repository.registerAccountWithRecoveryPassword(e164, recoveryPassword, registrationLock, skipDeviceTransfer = true)) {
        is NetworkController.RegistrationNetworkResult.Success -> {
          val (response, keyMaterial) = registerResult.data

          parentEventEmitter(RegistrationFlowEvent.Registered(keyMaterial.accountEntropyPool))

          if (response.storageCapable) {
            parentEventEmitter.navigateTo(RegistrationRoute.PinEntryForSvrRestore)
          } else {
            parentEventEmitter.navigateTo(RegistrationRoute.PinCreate)
          }
        }
        is NetworkController.RegistrationNetworkResult.Failure -> {
          when (registerResult.error) {
            is NetworkController.RegisterAccountError.SessionNotFoundOrNotVerified -> {
              Log.w(TAG, "[Register] Got told that our session could not be found when registering with RRP. We should never get into this state. Resetting.")
              parentEventEmitter(RegistrationFlowEvent.ResetState)
              return state
            }
            is NetworkController.RegisterAccountError.DeviceTransferPossible -> {
              Log.w(TAG, "[Register] Got told a device transfer is possible. We should never get into this state. Resetting.")
              parentEventEmitter(RegistrationFlowEvent.ResetState)
              return state
            }
            is NetworkController.RegisterAccountError.RegistrationLock -> {
              Log.w(TAG, "[Register] Reglocked.")
              parentEventEmitter.navigateTo(
                RegistrationRoute.PinEntryForRegistrationLock(
                  timeRemaining = registerResult.error.data.timeRemaining,
                  svrCredentials = registerResult.error.data.svr2Credentials
                )
              )
              return state
            }
            is NetworkController.RegisterAccountError.RateLimited -> {
              Log.w(TAG, "[Register] Rate limited.")
              return state.copy(oneTimeEvent = OneTimeEvent.RateLimited(registerResult.error.retryAfter))
            }
            is NetworkController.RegisterAccountError.InvalidRequest -> {
              Log.w(TAG, "[Register] Invalid request when registering account with RRP. Ditching pre-existing data and continuing with session creation. Message: ${registerResult.error.message}")
              // TODO should we clear it in the parent state as well?
              state = state.copy(preExistingRegistrationData = null)
            }
            is NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect -> {
              Log.w(TAG, "[Register] Registration recovery password incorrect. Ditching pre-existing data and continuing with session creation. Message: ${registerResult.error.message}")
              // TODO should we clear it in the parent state as well?
              state = state.copy(preExistingRegistrationData = null)
            }
          }
        }
        is NetworkController.RegistrationNetworkResult.NetworkError -> {
          Log.w(TAG, "[Register] Network error.", registerResult.exception)
          return state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
        }
        is NetworkController.RegistrationNetworkResult.ApplicationError -> {
          Log.w(TAG, "[Register] Unknown error when registering account.", registerResult.exception)
          return state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
        }
      }
    }

    // Detect if someone backed into this screen and entered a different number
    if (state.sessionE164 != null && state.sessionE164 != e164) {
      state = state.copy(sessionMetadata = null)
    }

    var sessionMetadata: NetworkController.SessionMetadata = state.sessionMetadata ?: when (val response = this@PhoneNumberEntryViewModel.repository.createSession(e164)) {
      is NetworkController.RegistrationNetworkResult.Success<NetworkController.SessionMetadata> -> {
        response.data
      }
      is NetworkController.RegistrationNetworkResult.Failure<NetworkController.CreateSessionError> -> {
        return when (response.error) {
          is NetworkController.CreateSessionError.InvalidRequest -> {
            state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
          }
          is NetworkController.CreateSessionError.RateLimited -> {
            state.copy(oneTimeEvent = OneTimeEvent.RateLimited(response.error.retryAfter))
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        return state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        Log.w(TAG, "Unknown error when creating session.", response.exception)
        return state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
      }
    }

    state = state.copy(sessionMetadata = sessionMetadata)

    if (sessionMetadata.requestedInformation.contains("pushChallenge")) {
      Log.d(TAG, "Push challenge requested, waiting for token...")
      val pushChallengeToken = withTimeoutOrNull(PUSH_CHALLENGE_TIMEOUT_MS) {
        repository.awaitPushChallengeToken()
      }

      if (pushChallengeToken != null) {
        Log.d(TAG, "Received push challenge token, submitting...")
        val updateResult = repository.submitPushChallengeToken(sessionMetadata.id, pushChallengeToken)
        sessionMetadata = when (updateResult) {
          is NetworkController.RegistrationNetworkResult.Success -> updateResult.data
          is NetworkController.RegistrationNetworkResult.Failure -> {
            Log.w(TAG, "Failed to submit push challenge token: ${updateResult.error}")
            sessionMetadata
          }
          is NetworkController.RegistrationNetworkResult.NetworkError -> {
            Log.w(TAG, "Network error submitting push challenge token", updateResult.exception)
            sessionMetadata
          }
          is NetworkController.RegistrationNetworkResult.ApplicationError -> {
            Log.w(TAG, "Application error submitting push challenge token", updateResult.exception)
            sessionMetadata
          }
        }
        state = state.copy(sessionMetadata = sessionMetadata)
      } else {
        Log.d(TAG, "Push challenge token not received within timeout")
      }
    }

    if (sessionMetadata.requestedInformation.contains("captcha")) {
      parentEventEmitter.navigateTo(RegistrationRoute.Captcha(sessionMetadata))
      return state
    }

    val verificationCodeResponse = this@PhoneNumberEntryViewModel.repository.requestVerificationCode(
      sessionMetadata.id,
      smsAutoRetrieveCodeSupported = false,
      transport = NetworkController.VerificationCodeTransport.SMS
    )

    sessionMetadata = when (verificationCodeResponse) {
      is NetworkController.RegistrationNetworkResult.Success<NetworkController.SessionMetadata> -> {
        verificationCodeResponse.data
      }
      is NetworkController.RegistrationNetworkResult.Failure<NetworkController.RequestVerificationCodeError> -> {
        return when (verificationCodeResponse.error) {
          is NetworkController.RequestVerificationCodeError.InvalidRequest -> {
            state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
          }
          is NetworkController.RequestVerificationCodeError.RateLimited -> {
            state.copy(oneTimeEvent = OneTimeEvent.RateLimited(verificationCodeResponse.error.retryAfter))
          }
          is NetworkController.RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport -> {
            state.copy(oneTimeEvent = OneTimeEvent.CouldNotRequestCodeWithSelectedTransport)
          }
          is NetworkController.RequestVerificationCodeError.InvalidSessionId -> {
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.MissingRequestInformationOrAlreadyVerified -> {
            Log.w(TAG, "When requesting verification code, missing request information or already verified.")
            state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
          }
          is NetworkController.RequestVerificationCodeError.SessionNotFound -> {
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.ThirdPartyServiceError -> {
            state.copy(oneTimeEvent = OneTimeEvent.ThirdPartyError)
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        return state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        Log.w(TAG, "Unknown error when creating session.", verificationCodeResponse.exception)
        return state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
      }
    }

    state = state.copy(sessionMetadata = sessionMetadata)

    if (sessionMetadata.requestedInformation.contains("captcha")) {
      parentEventEmitter.navigateTo(RegistrationRoute.Captcha(sessionMetadata))
      return state
    }

    parentEventEmitter.navigateTo(RegistrationRoute.VerificationCodeEntry(sessionMetadata, e164))
    return state
  }

  private suspend fun applyCaptchaCompleted(inputState: PhoneNumberEntryState, token: String, parentEventEmitter: (RegistrationFlowEvent) -> Unit): PhoneNumberEntryState {
    var state = inputState.copy()
    var sessionMetadata = state.sessionMetadata ?: return state.copy(oneTimeEvent = OneTimeEvent.UnknownError)

    val updateResult = this@PhoneNumberEntryViewModel.repository.submitCaptchaToken(sessionMetadata.id, token)

    sessionMetadata = when (updateResult) {
      is NetworkController.RegistrationNetworkResult.Success -> updateResult.data
      is NetworkController.RegistrationNetworkResult.Failure -> {
        return when (updateResult.error) {
          is NetworkController.UpdateSessionError.InvalidRequest -> {
            state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
          }
          is NetworkController.UpdateSessionError.RejectedUpdate -> {
            state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
          }
          is NetworkController.UpdateSessionError.RateLimited -> {
            state.copy(oneTimeEvent = OneTimeEvent.RateLimited(updateResult.error.retryAfter))
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        return state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        Log.w(TAG, "Unknown error when submitting captcha.", updateResult.exception)
        return state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
      }
    }

    state = state.copy(sessionMetadata = sessionMetadata)

    // TODO should we be reading "allowedToRequestCode"?
    if (sessionMetadata.requestedInformation.contains("captcha")) {
      parentEventEmitter.navigateTo(RegistrationRoute.Captcha(sessionMetadata))
      return state
    }

    val verificationCodeResponse = this@PhoneNumberEntryViewModel.repository.requestVerificationCode(
      sessionId = sessionMetadata.id,
      smsAutoRetrieveCodeSupported = false, // TODO eventually support this
      transport = NetworkController.VerificationCodeTransport.SMS
    )

    sessionMetadata = when (verificationCodeResponse) {
      is NetworkController.RegistrationNetworkResult.Success -> verificationCodeResponse.data
      is NetworkController.RegistrationNetworkResult.Failure -> {
        return when (verificationCodeResponse.error) {
          is NetworkController.RequestVerificationCodeError.InvalidRequest -> {
            state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
          }
          is NetworkController.RequestVerificationCodeError.RateLimited -> {
            state.copy(oneTimeEvent = OneTimeEvent.RateLimited(verificationCodeResponse.error.retryAfter))
          }
          is NetworkController.RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport -> {
            state.copy(oneTimeEvent = OneTimeEvent.CouldNotRequestCodeWithSelectedTransport)
          }
          is NetworkController.RequestVerificationCodeError.InvalidSessionId -> {
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.MissingRequestInformationOrAlreadyVerified -> {
            TODO()
          }
          is NetworkController.RequestVerificationCodeError.SessionNotFound -> {
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.ThirdPartyServiceError -> {
            state.copy(oneTimeEvent = OneTimeEvent.ThirdPartyError)
          }
        }
      }
      is NetworkController.RegistrationNetworkResult.NetworkError -> {
        return state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
      }
      is NetworkController.RegistrationNetworkResult.ApplicationError -> {
        Log.w(TAG, "Unknown error when requesting verification code.", verificationCodeResponse.exception)
        return state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
      }
    }

    val e164 = "+${inputState.countryCode}${inputState.nationalNumber}"

    parentEventEmitter.navigateTo(RegistrationRoute.VerificationCodeEntry(sessionMetadata, e164))
    return state
  }

  private fun formatNumber(nationalNumber: String): String {
    formatter.clear()
    var result = ""
    for (digit in nationalNumber) {
      result = formatter.inputDigit(digit)
    }
    return result
  }

  class Factory(
    val repository: RegistrationRepository,
    val parentState: StateFlow<RegistrationFlowState>,
    val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return PhoneNumberEntryViewModel(repository, parentState, parentEventEmitter) as T
    }
  }
}
