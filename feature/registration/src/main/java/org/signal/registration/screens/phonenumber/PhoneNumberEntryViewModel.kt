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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.E164Util
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.PendingRestoreOption
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.EventDrivenViewModel
import org.signal.registration.screens.countrycode.CountryUtils
import org.signal.registration.screens.localbackuprestore.LocalBackupRestoreResult
import org.signal.registration.screens.phonenumber.PhoneNumberEntryState.OneTimeEvent
import org.signal.registration.screens.util.navigateTo

class PhoneNumberEntryViewModel(
  val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<PhoneNumberEntryScreenEvents>(TAG) {

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
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PhoneNumberEntryState())

  init {
    viewModelScope.launch {
      _state.value = state.value.copy(
        restoredSvrCredentials = repository.getRestoredSvrCredentials()
      )
      setDefaultCountry()
    }
  }

  fun setDefaultCountry() {
    val regionCode = repository.getDefaultRegionCode()
    formatter = phoneNumberUtil.getAsYouTypeFormatter(regionCode)
    _state.update {
      it.copy(
        regionCode = regionCode,
        countryName = E164Util.getRegionDisplayName(regionCode).orElse(""),
        countryEmoji = CountryUtils.countryToEmoji(regionCode),
        countryCode = PhoneNumberUtil.getInstance().getCountryCodeForRegion(regionCode).toString()
      )
    }
  }

  override suspend fun processEvent(event: PhoneNumberEntryScreenEvents) {
    applyEvent(state.value, event, parentEventEmitter) {
      _state.value = it
    }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: PhoneNumberEntryState, event: PhoneNumberEntryScreenEvents, parentEventEmitter: (RegistrationFlowEvent) -> Unit, stateEmitter: (PhoneNumberEntryState) -> Unit) {
    when (event) {
      is PhoneNumberEntryScreenEvents.CountryCodeChanged -> {
        stateEmitter(applyCountryCodeChanged(state, event.value))
      }
      is PhoneNumberEntryScreenEvents.CountrySelected -> {
        stateEmitter(applyCountrySelected(state, event.countryCode, event.regionCode, event.countryName, event.countryEmoji))
      }
      is PhoneNumberEntryScreenEvents.PhoneNumberChanged -> {
        stateEmitter(applyPhoneNumberChanged(state, event.value))
      }
      is PhoneNumberEntryScreenEvents.PhoneNumberEntered -> {
        stateEmitter(state.copy(showDialog = true))
      }
      is PhoneNumberEntryScreenEvents.PhoneNumberCancelled -> {
        stateEmitter(state.copy(showDialog = false))
      }
      is PhoneNumberEntryScreenEvents.PhoneNumberSubmitted -> {
        var localState = state.copy(showSpinner = true)
        stateEmitter(localState)
        localState = applyPhoneNumberSubmitted(localState, parentEventEmitter)
        stateEmitter(localState.copy(showSpinner = false))
      }
      is PhoneNumberEntryScreenEvents.CountryPicker -> {
        state.also { parentEventEmitter.navigateTo(RegistrationRoute.CountryCodePicker) }
      }
      is PhoneNumberEntryScreenEvents.CaptchaCompleted -> {
        stateEmitter(applyCaptchaCompleted(state, event.token, parentEventEmitter))
      }
      is PhoneNumberEntryScreenEvents.LocalBackupRestoreCompleted -> {
        when (event.result) {
          is LocalBackupRestoreResult.Success -> {
            var localState = state.copy(showSpinner = true)
            stateEmitter(localState)
            localState = applyLocalBackupRestoreCompleted(localState, event.result.aep, parentEventEmitter)
            stateEmitter(localState.copy(showSpinner = false))
          }
          is LocalBackupRestoreResult.Canceled -> {
            parentEventEmitter(RegistrationFlowEvent.PendingRestoreOptionSelected(null))
          }
        }
      }
      is PhoneNumberEntryScreenEvents.ConsumeOneTimeEvent -> {
        stateEmitter(state.copy(oneTimeEvent = null))
      }
    }
  }

  @VisibleForTesting
  fun applyParentState(state: PhoneNumberEntryState, parentState: RegistrationFlowState): PhoneNumberEntryState {
    return state.copy(
      sessionE164 = parentState.sessionE164,
      sessionMetadata = parentState.sessionMetadata,
      preExistingRegistrationData = parentState.preExistingRegistrationData,
      restoredSvrCredentials = state.restoredSvrCredentials.takeUnless { parentState.doNotAttemptRecoveryPassword } ?: emptyList(),
      pendingRestoreOption = parentState.pendingRestoreOption
    )
  }

  private fun applyCountrySelected(state: PhoneNumberEntryState, countryCode: Int, regionCode: String, countryName: String, countryEmoji: String): PhoneNumberEntryState {
    val countryCodeStr = countryCode.toString()
    if (countryCodeStr == state.countryCode && regionCode == state.regionCode) return state

    formatter = phoneNumberUtil.getAsYouTypeFormatter(regionCode)
    val formattedNumber = formatNumber(state.nationalNumber)

    return state.copy(
      countryCode = countryCodeStr,
      regionCode = regionCode,
      countryName = countryName,
      countryEmoji = countryEmoji,
      formattedNumber = formattedNumber
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
      countryName = E164Util.getRegionDisplayName(regionCode).orElse(""),
      countryEmoji = CountryUtils.countryToEmoji(regionCode).takeIf { regionCode != "ZZ" } ?: "",
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

    // If the user selected a restore option before entering their phone number, navigate to the restore flow
    if (state.pendingRestoreOption != null) {
      parentEventEmitter(RegistrationFlowEvent.E164Chosen(e164))
      when (state.pendingRestoreOption) {
        PendingRestoreOption.LocalBackup -> parentEventEmitter.navigateTo(RegistrationRoute.LocalBackupRestore(isPreRegistration = true))
        PendingRestoreOption.RemoteBackup -> {
          Log.w(TAG, "[PendingRestore] Remote backup restore not yet implemented")
        }
      }
      return state
    }

    // If we're re-registering for the same number we used to be registered for, we should try to skip right to registration
    if (state.preExistingRegistrationData?.e164 == e164) {
      val masterKey = state.preExistingRegistrationData.aep.deriveMasterKey()
      val recoveryPassword = masterKey.deriveRegistrationRecoveryPassword()
      val registrationLock = masterKey.deriveRegistrationLock().takeIf { state.preExistingRegistrationData.registrationLockEnabled }

      when (val registerResult = repository.registerAccountWithRecoveryPassword(e164, recoveryPassword, registrationLock, skipDeviceTransfer = true, state.preExistingRegistrationData)) {
        is RequestResult.Success -> {
          Log.i(TAG, "[Register] Successfully re-registered using RRP from pre-existing data.")
          val (response, keyMaterial) = registerResult.result

          parentEventEmitter(RegistrationFlowEvent.Registered(keyMaterial.accountEntropyPool))

          if (response.storageCapable) {
            parentEventEmitter.navigateTo(RegistrationRoute.PinEntryForSvrRestore)
          } else {
            parentEventEmitter.navigateTo(RegistrationRoute.PinCreate)
          }
          return state
        }
        is RequestResult.NonSuccess -> {
          when (val error = registerResult.error) {
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
              Log.w(TAG, "[Register] Reglocked. This implies that the user still had reglock enabled despite the pre-existing data not thinking it was.")
              parentEventEmitter.navigateTo(
                RegistrationRoute.PinEntryForRegistrationLock(
                  timeRemaining = error.data.timeRemaining,
                  svrCredentials = error.data.svr2Credentials
                )
              )
              return state
            }
            is NetworkController.RegisterAccountError.RateLimited -> {
              Log.w(TAG, "[Register] Rate limited (retryAfter: ${error.retryAfter}).")
              return state.copy(oneTimeEvent = OneTimeEvent.RateLimited(error.retryAfter))
            }
            is NetworkController.RegisterAccountError.InvalidRequest -> {
              Log.w(TAG, "[Register] Invalid request when registering account with RRP. Ditching pre-existing data and continuing with session creation. Message: ${error.message}")
              parentEventEmitter(RegistrationFlowEvent.RecoveryPasswordInvalid)
              state = state.copy(preExistingRegistrationData = null)
            }
            is NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect -> {
              Log.w(TAG, "[Register] Registration recovery password incorrect. Ditching pre-existing data and continuing with session creation. Message: ${error.message}")
              parentEventEmitter(RegistrationFlowEvent.RecoveryPasswordInvalid)
              state = state.copy(preExistingRegistrationData = null)
            }
          }
        }
        is RequestResult.RetryableNetworkError -> {
          Log.w(TAG, "[Register] Network error.", registerResult.networkError)
          return state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
        }
        is RequestResult.ApplicationError -> {
          Log.w(TAG, "[Register] Unknown error when registering account.", registerResult.cause)
          return state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
        }
      }
    }

    return applySessionBasedRegistration(state, e164, parentEventEmitter)
  }

  /**
   * Handles the result of a pre-registration local backup restore.
   * If an AEP was obtained (V2 backup), attempts RRP-based registration.
   * Falls back to SVR check and SMS verification if RRP fails or no AEP is available.
   */
  private suspend fun applyLocalBackupRestoreCompleted(
    inputState: PhoneNumberEntryState,
    aep: AccountEntropyPool?,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ): PhoneNumberEntryState {
    val e164 = inputState.sessionE164 ?: "+${inputState.countryCode}${inputState.nationalNumber}"
    val state = inputState.copy()

    if (aep == null) {
      Log.i(TAG, "[LocalRestore] No AEP available (V1 backup). Proceeding to session-based registration.")
      return applySessionBasedRegistration(state, e164, parentEventEmitter)
    }

    parentEventEmitter(RegistrationFlowEvent.AepSubmittedViaLocalBackupRestore(aep))

    Log.i(TAG, "[LocalRestore] Attempting registration with RRP derived from restored AEP.")

    val recoveryPassword = aep.deriveMasterKey().deriveRegistrationRecoveryPassword()

    return when (val result = repository.registerAccountWithRecoveryPassword(e164, recoveryPassword, existingAccountEntropyPool = aep)) {
      is RequestResult.Success -> {
        Log.i(TAG, "[LocalRestore] Successfully registered using RRP from restored AEP.")
        val (response, keyMaterial) = result.result

        parentEventEmitter(RegistrationFlowEvent.Registered(keyMaterial.accountEntropyPool))

        if (response.storageCapable) {
          parentEventEmitter.navigateTo(RegistrationRoute.PinEntryForSvrRestore)
        } else {
          parentEventEmitter.navigateTo(RegistrationRoute.PinCreate)
        }
        state
      }
      is RequestResult.NonSuccess -> {
        when (val error = result.error) {
          is NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect -> {
            Log.w(TAG, "[LocalRestore] RRP incorrect. Falling back to session-based registration.")
            parentEventEmitter(RegistrationFlowEvent.RecoveryPasswordInvalid)
            applySessionBasedRegistration(state, e164, parentEventEmitter)
          }
          is NetworkController.RegisterAccountError.InvalidRequest -> {
            Log.w(TAG, "[LocalRestore] Invalid request. Falling back to session-based registration. Message: ${error.message}")
            parentEventEmitter(RegistrationFlowEvent.RecoveryPasswordInvalid)
            applySessionBasedRegistration(state, e164, parentEventEmitter)
          }
          is NetworkController.RegisterAccountError.RegistrationLock -> {
            Log.w(TAG, "[LocalRestore] Registration locked.")
            parentEventEmitter.navigateTo(
              RegistrationRoute.PinEntryForRegistrationLock(
                timeRemaining = error.data.timeRemaining,
                svrCredentials = error.data.svr2Credentials
              )
            )
            state
          }
          is NetworkController.RegisterAccountError.RateLimited -> {
            Log.w(TAG, "[LocalRestore] Rate limited (retryAfter: ${error.retryAfter}).")
            state.copy(oneTimeEvent = OneTimeEvent.RateLimited(error.retryAfter))
          }
          is NetworkController.RegisterAccountError.SessionNotFoundOrNotVerified -> {
            Log.w(TAG, "[LocalRestore] Session not found. Falling back to session-based registration.")
            applySessionBasedRegistration(state, e164, parentEventEmitter)
          }
          is NetworkController.RegisterAccountError.DeviceTransferPossible -> {
            Log.w(TAG, "[LocalRestore] Device transfer possible. Falling back to session-based registration.")
            applySessionBasedRegistration(state, e164, parentEventEmitter)
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[LocalRestore] Network error.", result.networkError)
        state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[LocalRestore] Application error.", result.cause)
        state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
      }
    }
  }

  /**
   * Checks SVR credentials, then creates a session and requests an SMS verification code.
   * This is the shared fallback path used by both phone number submission and local backup restore completion.
   */
  private suspend fun applySessionBasedRegistration(
    inputState: PhoneNumberEntryState,
    e164: String,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ): PhoneNumberEntryState {
    var state = inputState.copy()

    // Detect if we have valid SVR credentials for the current number. If so, we can go right to the PIN entry screen.
    // If they successfully restore the master key at that screen, we can use that to build the RRP and register without SMS.
    if (state.restoredSvrCredentials.isNotEmpty()) {
      when (val result = repository.checkSvrCredentials(e164, state.restoredSvrCredentials)) {
        is RequestResult.Success -> {
          Log.i(TAG, "[CheckSVRCredentials] Successfully validated credentials for $e164.")
          val credential = result.result.validCredential
          if (credential != null) {
            parentEventEmitter(RegistrationFlowEvent.E164Chosen(e164))
            parentEventEmitter.navigateTo(RegistrationRoute.PinEntryForSmsBypass(credential))
            return state
          }
        }
        is RequestResult.RetryableNetworkError -> {
          Log.w(TAG, "[CheckSVRCredentials] Network error. Ignoring error and continuing without RRP.", result.networkError)
        }
        is RequestResult.ApplicationError -> {
          Log.w(TAG, "[CheckSVRCredentials] Application error. Ignoring error and continuing without RRP.", result.cause)
        }
        is RequestResult.NonSuccess -> {
          when (val error = result.error) {
            is NetworkController.CheckSvrCredentialsError.InvalidRequest -> {
              Log.w(TAG, "[CheckSVRCredentials] Invalid request. Ignoring error and continuing without RRP. Message: ${error.message}")
            }

            NetworkController.CheckSvrCredentialsError.Unauthorized -> {
              Log.w(TAG, "[CheckSVRCredentials] Unauthorized. Ignoring error and continuing without RRP.")
            }
          }
        }
      }
    }

    // Detect if someone backed into this screen and entered a different number
    if (state.sessionE164 != null && state.sessionE164 != e164) {
      state = state.copy(sessionMetadata = null)
    }

    var sessionMetadata: NetworkController.SessionMetadata = state.sessionMetadata ?: when (val response = this@PhoneNumberEntryViewModel.repository.createSession(e164)) {
      is RequestResult.Success<NetworkController.SessionMetadata> -> {
        response.result
      }
      is RequestResult.NonSuccess<NetworkController.CreateSessionError> -> {
        return when (val error = response.error) {
          is NetworkController.CreateSessionError.InvalidRequest -> {
            Log.w(TAG, "[CreateSession] Invalid request when creating session. Message: ${error.message}")
            state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
          }
          is NetworkController.CreateSessionError.RateLimited -> {
            Log.w(TAG, "[CreateSession] Rate limited (retryAfter: ${error.retryAfter}).")
            state.copy(oneTimeEvent = OneTimeEvent.RateLimited(error.retryAfter))
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[CreateSession] Network error.", response.networkError)
        return state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Unknown error when creating session.", response.cause)
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
          is RequestResult.Success -> {
            Log.d(TAG, "[SubmitPushChallengeToken] Successfully submitted push challenge token.")
            updateResult.result
          }
          is RequestResult.NonSuccess -> {
            Log.w(TAG, "[SubmitPushChallengeToken] Failed to submit push challenge token: ${updateResult.error}")
            sessionMetadata
          }
          is RequestResult.RetryableNetworkError -> {
            Log.w(TAG, "[SubmitPushChallengeToken] Network error submitting push challenge token", updateResult.networkError)
            sessionMetadata
          }
          is RequestResult.ApplicationError -> {
            Log.w(TAG, "[SubmitPushChallengeToken] Application error submitting push challenge token", updateResult.cause)
            sessionMetadata
          }
        }
        state = state.copy(sessionMetadata = sessionMetadata)
      } else {
        Log.d(TAG, "[SubmitPushChallengeToken] Push challenge token not received within timeout")
      }
    }

    if (sessionMetadata.requestedInformation.contains("captcha")) {
      parentEventEmitter(RegistrationFlowEvent.SessionUpdated(sessionMetadata))
      parentEventEmitter(RegistrationFlowEvent.E164Chosen(e164))
      parentEventEmitter.navigateTo(RegistrationRoute.Captcha(sessionMetadata))
      return state
    }

    if (!sessionMetadata.allowedToRequestCode && sessionMetadata.requestedInformation.isEmpty()) {
      Log.w(TAG, "Not allowed to request code and no challenges requested. Unable to send SMS.")
      return state.copy(oneTimeEvent = OneTimeEvent.UnableToSendSms)
    }

    val verificationCodeResponse = this@PhoneNumberEntryViewModel.repository.requestVerificationCode(
      sessionMetadata.id,
      smsAutoRetrieveCodeSupported = false,
      transport = NetworkController.VerificationCodeTransport.SMS
    )

    sessionMetadata = when (verificationCodeResponse) {
      is RequestResult.Success<NetworkController.SessionMetadata> -> {
        Log.d(TAG, "[RequestVerificationCode] Successfully requested verification code.")
        verificationCodeResponse.result
      }
      is RequestResult.NonSuccess<NetworkController.RequestVerificationCodeError> -> {
        return when (val error = verificationCodeResponse.error) {
          is NetworkController.RequestVerificationCodeError.InvalidRequest -> {
            Log.w(TAG, "[RequestVerificationCode] Invalid request when requesting verification code. Message: ${error.message}")
            state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
          }
          is NetworkController.RequestVerificationCodeError.RateLimited -> {
            Log.w(TAG, "[RequestVerificationCode] Rate limited (retryAfter: ${error.retryAfter}).")
            state.copy(oneTimeEvent = OneTimeEvent.RateLimited(error.retryAfter))
          }
          is NetworkController.RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport -> {
            Log.w(TAG, "[RequestVerificationCode] Could not fulfill with requested transport.")
            state.copy(oneTimeEvent = OneTimeEvent.CouldNotRequestCodeWithSelectedTransport)
          }
          is NetworkController.RequestVerificationCodeError.InvalidSessionId -> {
            Log.w(TAG, "[RequestVerificationCode] Invalid session ID when requesting verification code.")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.MissingRequestInformationOrAlreadyVerified -> {
            Log.w(TAG, "[RequestVerificationCode] Missing request information or already verified.")
            state.copy(oneTimeEvent = OneTimeEvent.UnableToSendSms)
          }
          is NetworkController.RequestVerificationCodeError.SessionNotFound -> {
            Log.w(TAG, "[RequestVerificationCode] Session not found when requesting verification code.")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.ThirdPartyServiceError -> {
            Log.w(TAG, "[RequestVerificationCode] Third party service error.")
            state.copy(oneTimeEvent = OneTimeEvent.UnableToSendSms)
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[RequestVerificationCode] Network error.", verificationCodeResponse.networkError)
        return state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[RequestVerificationCode] Unknown error when creating session.", verificationCodeResponse.cause)
        return state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
      }
    }

    state = state.copy(sessionMetadata = sessionMetadata)

    if (sessionMetadata.requestedInformation.contains("captcha")) {
      parentEventEmitter.navigateTo(RegistrationRoute.Captcha(sessionMetadata))
      return state
    }

    parentEventEmitter(RegistrationFlowEvent.SessionUpdated(sessionMetadata))
    parentEventEmitter(RegistrationFlowEvent.E164Chosen(e164))
    parentEventEmitter.navigateTo(RegistrationRoute.VerificationCodeEntry)
    return state
  }

  private suspend fun applyCaptchaCompleted(inputState: PhoneNumberEntryState, token: String, parentEventEmitter: (RegistrationFlowEvent) -> Unit): PhoneNumberEntryState {
    var state = inputState.copy()
    var sessionMetadata = state.sessionMetadata ?: return state.copy(oneTimeEvent = OneTimeEvent.UnknownError)

    val updateResult = this@PhoneNumberEntryViewModel.repository.submitCaptchaToken(sessionMetadata.id, token)

    sessionMetadata = when (updateResult) {
      is RequestResult.Success -> updateResult.result
      is RequestResult.NonSuccess -> {
        return when (val error = updateResult.error) {
          is NetworkController.UpdateSessionError.InvalidRequest -> {
            state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
          }
          is NetworkController.UpdateSessionError.RejectedUpdate -> {
            state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
          }
          is NetworkController.UpdateSessionError.RateLimited -> {
            state.copy(oneTimeEvent = OneTimeEvent.RateLimited(error.retryAfter))
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        return state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Unknown error when submitting captcha.", updateResult.cause)
        return state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
      }
    }

    state = state.copy(sessionMetadata = sessionMetadata)

    if (sessionMetadata.requestedInformation.contains("captcha")) {
      parentEventEmitter.navigateTo(RegistrationRoute.Captcha(sessionMetadata))
      return state
    }

    if (!sessionMetadata.allowedToRequestCode && sessionMetadata.requestedInformation.isEmpty()) {
      Log.w(TAG, "Not allowed to request code and no challenges requested after captcha. Unable to send SMS.")
      return state.copy(oneTimeEvent = OneTimeEvent.UnableToSendSms)
    }

    val verificationCodeResponse = this@PhoneNumberEntryViewModel.repository.requestVerificationCode(
      sessionId = sessionMetadata.id,
      smsAutoRetrieveCodeSupported = false, // TODO eventually support this
      transport = NetworkController.VerificationCodeTransport.SMS
    )

    sessionMetadata = when (verificationCodeResponse) {
      is RequestResult.Success -> verificationCodeResponse.result
      is RequestResult.NonSuccess -> {
        return when (val error = verificationCodeResponse.error) {
          is NetworkController.RequestVerificationCodeError.InvalidRequest -> {
            state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
          }
          is NetworkController.RequestVerificationCodeError.RateLimited -> {
            state.copy(oneTimeEvent = OneTimeEvent.RateLimited(error.retryAfter))
          }
          is NetworkController.RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport -> {
            state.copy(oneTimeEvent = OneTimeEvent.CouldNotRequestCodeWithSelectedTransport)
          }
          is NetworkController.RequestVerificationCodeError.InvalidSessionId -> {
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.MissingRequestInformationOrAlreadyVerified -> {
            Log.w(TAG, "When requesting verification code after captcha, missing request information or already verified.")
            state.copy(oneTimeEvent = OneTimeEvent.UnableToSendSms)
          }
          is NetworkController.RequestVerificationCodeError.SessionNotFound -> {
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.ThirdPartyServiceError -> {
            state.copy(oneTimeEvent = OneTimeEvent.UnableToSendSms)
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        return state.copy(oneTimeEvent = OneTimeEvent.NetworkError)
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Unknown error when requesting verification code.", verificationCodeResponse.cause)
        return state.copy(oneTimeEvent = OneTimeEvent.UnknownError)
      }
    }

    parentEventEmitter(RegistrationFlowEvent.SessionUpdated(sessionMetadata))
    parentEventEmitter(RegistrationFlowEvent.E164Chosen("+${inputState.countryCode}${inputState.nationalNumber}"))
    parentEventEmitter.navigateTo(RegistrationRoute.VerificationCodeEntry)
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
