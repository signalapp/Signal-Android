/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

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
import org.signal.core.util.logging.Log
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.phonenumber.PhoneNumberEntryState.OneTimeEvent
import org.signal.registration.screens.util.navigateTo

class PhoneNumberEntryViewModel(
  val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(PhoneNumberEntryViewModel::class)
  }

  private val phoneNumberUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()
  private var formatter: AsYouTypeFormatter = phoneNumberUtil.getAsYouTypeFormatter("US")

  private val _state = MutableStateFlow(PhoneNumberEntryState())
  val state = combine(_state, parentState) { state, parentState -> applyParentState(state, parentState) }
    .onEach { Log.d(TAG, "[State] $it") }
    .stateIn(viewModelScope, SharingStarted.Eagerly, PhoneNumberEntryState())

  fun onEvent(event: PhoneNumberEntryScreenEvents) {
    viewModelScope.launch {
      _state.emit(applyEvent(_state.value, event))
    }
  }

  suspend fun applyEvent(state: PhoneNumberEntryState, event: PhoneNumberEntryScreenEvents): PhoneNumberEntryState {
    return when (event) {
      is PhoneNumberEntryScreenEvents.CountryCodeChanged -> transformCountryCodeChanged(state, event.value)
      is PhoneNumberEntryScreenEvents.PhoneNumberChanged -> transformPhoneNumberChanged(state, event.value)
      is PhoneNumberEntryScreenEvents.PhoneNumberSubmitted -> transformPhoneNumberSubmitted(state)
      is PhoneNumberEntryScreenEvents.CountryPicker -> state.also { parentEventEmitter.navigateTo(RegistrationRoute.CountryCodePicker) }
      is PhoneNumberEntryScreenEvents.CaptchaCompleted -> transformCaptchaCompleted(state, event.token)
      is PhoneNumberEntryScreenEvents.ConsumeOneTimeEvent -> state.copy(oneTimeEvent = null)
    }
  }

  fun applyParentState(state: PhoneNumberEntryState, parentState: RegistrationFlowState): PhoneNumberEntryState {
    return state.copy(sessionMetadata = parentState.sessionMetadata)
  }

  private fun transformCountryCodeChanged(state: PhoneNumberEntryState, countryCode: String): PhoneNumberEntryState {
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

  private fun transformPhoneNumberChanged(state: PhoneNumberEntryState, input: String): PhoneNumberEntryState {
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

  private fun formatNumber(nationalNumber: String): String {
    formatter.clear()
    var result = ""
    for (digit in nationalNumber) {
      result = formatter.inputDigit(digit)
    }
    return result
  }

  private suspend fun transformPhoneNumberSubmitted(
    inputState: PhoneNumberEntryState
  ): PhoneNumberEntryState {
    val e164 = "+${inputState.countryCode}${inputState.nationalNumber}"
    var state = inputState.copy()

    // TODO Consider that someone may back into this screen and change the number, requiring us to create a new session.

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

  private suspend fun transformCaptchaCompleted(inputState: PhoneNumberEntryState, token: String): PhoneNumberEntryState {
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
