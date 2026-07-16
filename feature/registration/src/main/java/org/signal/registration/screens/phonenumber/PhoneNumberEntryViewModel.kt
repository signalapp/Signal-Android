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
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.E164Util
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.PendingRestoreOption
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.countrycode.Country
import org.signal.registration.screens.countrycode.CountryUtils
import org.signal.registration.screens.localbackuprestore.LocalBackupRestoreResult
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
  val state: StateFlow<PhoneNumberEntryState> = _state.asStateFlow()

  init {
    setDefaultCountry()

    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    onEvent(PhoneNumberEntryScreenEvents.Initialize)

    parentState
      .onEach { onEvent(PhoneNumberEntryScreenEvents.ParentStateChanged(it)) }
      .launchIn(viewModelScope)
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
      ).withNumberValidity()
    }
  }

  override suspend fun processEvent(event: PhoneNumberEntryScreenEvents) {
    applyEvent(_state.value, event, parentEventEmitter) {
      _state.value = it
    }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: PhoneNumberEntryState, event: PhoneNumberEntryScreenEvents, parentEventEmitter: (RegistrationFlowEvent) -> Unit, stateEmitter: (PhoneNumberEntryState) -> Unit) {
    when (event) {
      is PhoneNumberEntryScreenEvents.Initialize -> {
        stateEmitter(applyInitialize(state))
      }
      is PhoneNumberEntryScreenEvents.ParentStateChanged -> {
        stateEmitter(applyParentState(state, event.parentState))
      }
      is PhoneNumberEntryScreenEvents.CountryCodeChanged -> {
        stateEmitter(applyCountryCodeChanged(state, event.value))
      }
      is PhoneNumberEntryScreenEvents.CountrySelected -> {
        stateEmitter(applyCountrySelected(state, event.countryCode, event.regionCode, event.countryName, event.countryEmoji))
      }
      is PhoneNumberEntryScreenEvents.FullPhoneNumberEntered -> {
        val populatedState = applyFullPhoneNumberEntered(state, event.e164)
        stateEmitter(populatedState.copy(dialogs = populatedState.dialogs.copy(confirmNumber = event.autoConfirm && populatedState.isNumberPossible)))
      }
      is PhoneNumberEntryScreenEvents.NationalNumberChanged -> {
        stateEmitter(applyPhoneNumberChanged(state, event.oldValue, event.newValue))
      }
      is PhoneNumberEntryScreenEvents.NextClicked -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(confirmNumber = true)))
      }
      is PhoneNumberEntryScreenEvents.PhoneNumberCancelled -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(confirmNumber = false)))
      }
      is PhoneNumberEntryScreenEvents.PhoneNumberConfirmed -> {
        var localState = state.copy(showSpinner = true, dialogs = state.dialogs.copy(confirmNumber = false))
        stateEmitter(localState)
        localState = applyPhoneNumberSubmitted(localState, parentEventEmitter)
        stateEmitter(localState.copy(showSpinner = false))
      }
      is PhoneNumberEntryScreenEvents.CountryPicker -> {
        state.also {
          parentEventEmitter.navigateTo(
            RegistrationRoute.CountryCodePicker(
              Country(state.countryEmoji, state.countryName, state.countryCode.toIntOrNull() ?: 0, state.regionCode).takeIf { state.countryName.isNotEmpty() }
            )
          )
        }
      }
      is PhoneNumberEntryScreenEvents.LinkDevice -> {
        parentEventEmitter.navigateTo(RegistrationRoute.LinkAccount())
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
          is LocalBackupRestoreResult.DeferredToSms -> {
            Log.i(TAG, "[LocalRestore] Backup belongs to a different account. Verifying the number over SMS before restoring.")
            var localState = state.copy(showSpinner = true)
            stateEmitter(localState)
            localState = applySessionBasedRegistration(localState, localState.sessionE164 ?: "+${localState.countryCode}${localState.nationalNumber}", parentEventEmitter)
            stateEmitter(localState.copy(showSpinner = false))
          }
          is LocalBackupRestoreResult.Canceled -> {
            parentEventEmitter(RegistrationFlowEvent.PendingRestoreOptionSelected(null))
          }
        }
      }
      is PhoneNumberEntryScreenEvents.NetworkErrorDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(networkError = false)))
      }
      is PhoneNumberEntryScreenEvents.UnknownErrorDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(unknownError = false)))
      }
      is PhoneNumberEntryScreenEvents.RateLimitedDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(rateLimitedRetryAfter = null)))
      }
      is PhoneNumberEntryScreenEvents.UnableToSendSmsDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(unableToSendSms = false)))
      }
      is PhoneNumberEntryScreenEvents.CouldNotRequestCodeWithSelectedTransportDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(couldNotRequestCodeWithSelectedTransport = false)))
      }
      is PhoneNumberEntryScreenEvents.InvalidPhoneNumberDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(invalidPhoneNumber = false)))
      }
    }
  }

  private suspend fun applyInitialize(inputState: PhoneNumberEntryState): PhoneNumberEntryState {
    var state = inputState.copy(restoredSvrCredentials = repository.getRestoredSvrCredentials())

    parentState.value.preExistingRegistrationData?.e164?.let { preExistingE164 ->
      if (state.formattedNumber.isEmpty()) {
        state = applyFullPhoneNumberEntered(state, preExistingE164)
      }
    }

    return state.copy(initialized = true)
  }

  private fun applyParentState(state: PhoneNumberEntryState, parentState: RegistrationFlowState): PhoneNumberEntryState {
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
    ).withNumberValidity()
  }

  @VisibleForTesting
  fun applyFullPhoneNumberEntered(state: PhoneNumberEntryState, e164: String): PhoneNumberEntryState {
    return redistributeFullPhoneNumber(state, e164) ?: state
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
    ).withNumberValidity()
  }

  private fun applyPhoneNumberChanged(state: PhoneNumberEntryState, oldValue: String, newValue: String): PhoneNumberEntryState {
    // Extract only digits from the input
    val digitsOnly = newValue.filter { it.isDigit() }
    if (digitsOnly == state.nationalNumber) return state

    // Only attempt to split out a country code / trunk prefix on a bulk entry (paste or autofill)
    if (insertedCharCount(oldValue, newValue) > 1) {
      if (newValue.trimStart().startsWith("+")) {
        redistributeFullPhoneNumber(state, "+$digitsOnly")?.let { return it }
      } else {
        reinterpretNationalNumber(state, digitsOnly)?.let { return it }
      }
    }

    val formattedNumber = formatNumber(digitsOnly)

    return state.copy(
      nationalNumber = digitsOnly,
      formattedNumber = formattedNumber
    ).withNumberValidity()
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

      Log.i(TAG, "Pending restore option: ${state.pendingRestoreOption}. Navigating to appropriate screen.")

      when (state.pendingRestoreOption) {
        PendingRestoreOption.LocalBackup -> parentEventEmitter.navigateTo(RegistrationRoute.LocalBackupRestore(isPreRegistration = true))
        PendingRestoreOption.RemoteBackup -> parentEventEmitter.navigateTo(RegistrationRoute.EnterAepForRemoteBackupPreRegistration(e164))
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

          parentEventEmitter(RegistrationFlowEvent.Registered(keyMaterial.accountEntropyPool, response.storageCapable))

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
              error("[Register] Got told that our session could not be found when registering with RRP. We should never get into this state.")
            }
            is NetworkController.RegisterAccountError.DeviceTransferPossible -> {
              error("[Register] Got told a device transfer is possible. We should never get into this state.")
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
              return state.copy(dialogs = state.dialogs.copy(rateLimitedRetryAfter = error.retryAfter))
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
          return state.copy(dialogs = state.dialogs.copy(networkError = true))
        }
        is RequestResult.ApplicationError -> {
          Log.w(TAG, "[Register] Unknown error when registering account.", registerResult.cause)
          return state.copy(dialogs = state.dialogs.copy(unknownError = true))
        }
      }
    }

    return applySessionBasedRegistration(state, e164, parentEventEmitter)
  }

  /**
   * Handles the result of a pre-registration V1 local backup restore (V2 backups register before restoring instead).
   * If the restored database contained an AEP, attempts RRP-based registration with it.
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

    parentEventEmitter(RegistrationFlowEvent.UserSuppliedAepSubmitted(aep))

    Log.i(TAG, "[LocalRestore] Attempting registration with RRP derived from restored AEP.")

    return attemptRegistrationWithRestoredAep(state, e164, aep, provideRegistrationLock = false, parentEventEmitter)
  }

  private suspend fun attemptRegistrationWithRestoredAep(
    state: PhoneNumberEntryState,
    e164: String,
    aep: AccountEntropyPool,
    provideRegistrationLock: Boolean,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ): PhoneNumberEntryState {
    val masterKey = aep.deriveMasterKey()
    val recoveryPassword = masterKey.deriveRegistrationRecoveryPassword()
    val registrationLock = masterKey.deriveRegistrationLock().takeIf { provideRegistrationLock }

    return when (val result = repository.registerAccountWithRecoveryPassword(e164, recoveryPassword, registrationLock, existingAccountEntropyPool = aep)) {
      is RequestResult.Success -> {
        Log.i(TAG, "[LocalRestore] Successfully registered using RRP from restored AEP.")
        val (response, keyMaterial) = result.result

        parentEventEmitter(RegistrationFlowEvent.Registered(keyMaterial.accountEntropyPool, response.storageCapable))

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
            if (provideRegistrationLock) {
              Log.w(TAG, "[LocalRestore] Still registration locked after providing the reglock token derived from the AEP. Falling back to PIN entry.")
              parentEventEmitter.navigateTo(
                RegistrationRoute.PinEntryForRegistrationLock(
                  timeRemaining = error.data.timeRemaining,
                  svrCredentials = error.data.svr2Credentials
                )
              )
              state
            } else {
              Log.w(TAG, "[LocalRestore] Registration locked. Retrying with the reglock token derived from the AEP.")
              attemptRegistrationWithRestoredAep(state, e164, aep, provideRegistrationLock = true, parentEventEmitter)
            }
          }
          is NetworkController.RegisterAccountError.RateLimited -> {
            Log.w(TAG, "[LocalRestore] Rate limited (retryAfter: ${error.retryAfter}).")
            state.copy(dialogs = state.dialogs.copy(rateLimitedRetryAfter = error.retryAfter))
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
        state.copy(dialogs = state.dialogs.copy(networkError = true))
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[LocalRestore] Application error.", result.cause)
        state.copy(dialogs = state.dialogs.copy(unknownError = true))
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
            Log.w(TAG, "[CreateSession] Invalid request when creating session, likely an invalid phone number. Message: ${error.message}")
            state.copy(dialogs = state.dialogs.copy(invalidPhoneNumber = true))
          }
          is NetworkController.CreateSessionError.RateLimited -> {
            Log.w(TAG, "[CreateSession] Rate limited (retryAfter: ${error.retryAfter}).")
            state.copy(dialogs = state.dialogs.copy(rateLimitedRetryAfter = error.retryAfter))
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[CreateSession] Network error.", response.networkError)
        return state.copy(dialogs = state.dialogs.copy(networkError = true))
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Unknown error when creating session.", response.cause)
        return state.copy(dialogs = state.dialogs.copy(unknownError = true))
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
      return state.copy(dialogs = state.dialogs.copy(unableToSendSms = true))
    }

    val verificationCodeResponse = this@PhoneNumberEntryViewModel.repository.requestVerificationCode(
      sessionMetadata.id,
      smsAutoRetrieveCodeSupported = repository.registerSmsListener(),
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
            state.copy(dialogs = state.dialogs.copy(unknownError = true))
          }
          is NetworkController.RequestVerificationCodeError.RateLimited -> {
            Log.w(TAG, "[RequestVerificationCode] Rate limited (retryAfter: ${error.retryAfter}).")
            state.copy(dialogs = state.dialogs.copy(rateLimitedRetryAfter = error.retryAfter))
          }
          is NetworkController.RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport -> {
            Log.w(TAG, "[RequestVerificationCode] Could not fulfill with requested transport.")
            state.copy(dialogs = state.dialogs.copy(couldNotRequestCodeWithSelectedTransport = true))
          }
          is NetworkController.RequestVerificationCodeError.InvalidSessionId -> {
            Log.w(TAG, "[RequestVerificationCode] Invalid session ID when requesting verification code.")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.MissingRequestInformationOrAlreadyVerified -> {
            Log.w(TAG, "[RequestVerificationCode] Missing request information or already verified.")
            state.copy(dialogs = state.dialogs.copy(unableToSendSms = true))
          }
          is NetworkController.RequestVerificationCodeError.SessionNotFound -> {
            Log.w(TAG, "[RequestVerificationCode] Session not found when requesting verification code.")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.ThirdPartyServiceError -> {
            Log.w(TAG, "[RequestVerificationCode] Third party service error.")
            state.copy(dialogs = state.dialogs.copy(unableToSendSms = true))
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[RequestVerificationCode] Network error.", verificationCodeResponse.networkError)
        return state.copy(dialogs = state.dialogs.copy(networkError = true))
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[RequestVerificationCode] Unknown error when creating session.", verificationCodeResponse.cause)
        return state.copy(dialogs = state.dialogs.copy(unknownError = true))
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
    var sessionMetadata = state.sessionMetadata ?: return state.copy(dialogs = state.dialogs.copy(unknownError = true))

    val updateResult = this@PhoneNumberEntryViewModel.repository.submitCaptchaToken(sessionMetadata.id, token)

    sessionMetadata = when (updateResult) {
      is RequestResult.Success -> updateResult.result
      is RequestResult.NonSuccess -> {
        return when (val error = updateResult.error) {
          is NetworkController.UpdateSessionError.InvalidRequest -> {
            state.copy(dialogs = state.dialogs.copy(unknownError = true))
          }
          is NetworkController.UpdateSessionError.RejectedUpdate -> {
            state.copy(dialogs = state.dialogs.copy(unknownError = true))
          }
          is NetworkController.UpdateSessionError.RateLimited -> {
            state.copy(dialogs = state.dialogs.copy(rateLimitedRetryAfter = error.retryAfter))
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        return state.copy(dialogs = state.dialogs.copy(networkError = true))
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Unknown error when submitting captcha.", updateResult.cause)
        return state.copy(dialogs = state.dialogs.copy(unknownError = true))
      }
    }

    state = state.copy(sessionMetadata = sessionMetadata)

    if (sessionMetadata.requestedInformation.contains("captcha")) {
      parentEventEmitter.navigateTo(RegistrationRoute.Captcha(sessionMetadata))
      return state
    }

    if (!sessionMetadata.allowedToRequestCode && sessionMetadata.requestedInformation.isEmpty()) {
      Log.w(TAG, "Not allowed to request code and no challenges requested after captcha. Unable to send SMS.")
      return state.copy(dialogs = state.dialogs.copy(unableToSendSms = true))
    }

    val verificationCodeResponse = this@PhoneNumberEntryViewModel.repository.requestVerificationCode(
      sessionId = sessionMetadata.id,
      smsAutoRetrieveCodeSupported = repository.registerSmsListener(),
      transport = NetworkController.VerificationCodeTransport.SMS
    )

    sessionMetadata = when (verificationCodeResponse) {
      is RequestResult.Success -> verificationCodeResponse.result
      is RequestResult.NonSuccess -> {
        return when (val error = verificationCodeResponse.error) {
          is NetworkController.RequestVerificationCodeError.InvalidRequest -> {
            state.copy(dialogs = state.dialogs.copy(unknownError = true))
          }
          is NetworkController.RequestVerificationCodeError.RateLimited -> {
            state.copy(dialogs = state.dialogs.copy(rateLimitedRetryAfter = error.retryAfter))
          }
          is NetworkController.RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport -> {
            state.copy(dialogs = state.dialogs.copy(couldNotRequestCodeWithSelectedTransport = true))
          }
          is NetworkController.RequestVerificationCodeError.InvalidSessionId -> {
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.MissingRequestInformationOrAlreadyVerified -> {
            Log.w(TAG, "When requesting verification code after captcha, missing request information or already verified.")
            state.copy(dialogs = state.dialogs.copy(unableToSendSms = true))
          }
          is NetworkController.RequestVerificationCodeError.SessionNotFound -> {
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RequestVerificationCodeError.ThirdPartyServiceError -> {
            state.copy(dialogs = state.dialogs.copy(unableToSendSms = true))
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        return state.copy(dialogs = state.dialogs.copy(networkError = true))
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Unknown error when requesting verification code.", verificationCodeResponse.cause)
        return state.copy(dialogs = state.dialogs.copy(unknownError = true))
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

  /**
   * Attempts to interpret [e164] as a complete phone number and split it into the country code and national number
   * fields. Returns null if it can't be parsed into a usable number, leaving it to the caller to decide on a fallback.
   */
  private fun redistributeFullPhoneNumber(state: PhoneNumberEntryState, e164: String): PhoneNumberEntryState? {
    val parsedNumber = try {
      phoneNumberUtil.parse(e164, null)
    } catch (e: NumberParseException) {
      Log.w(TAG, "Failed to parse E164 used to populate phone number.", e)
      return null
    }

    if (parsedNumber.nationalNumber == 0L) {
      return null
    }

    val countryCode = parsedNumber.countryCode
    val nationalNumber = parsedNumber.nationalNumber.toString()
    val regionCode = phoneNumberUtil.getRegionCodeForNumber(parsedNumber) ?: phoneNumberUtil.getRegionCodeForCountryCode(countryCode)

    formatter = phoneNumberUtil.getAsYouTypeFormatter(regionCode)
    val formattedNumber = formatNumber(nationalNumber)

    return state.copy(
      countryCode = countryCode.toString(),
      regionCode = regionCode,
      countryName = E164Util.getRegionDisplayName(regionCode).orElse(""),
      countryEmoji = CountryUtils.countryToEmoji(regionCode).takeIf { regionCode != "ZZ" } ?: "",
      nationalNumber = nationalNumber,
      formattedNumber = formattedNumber
    ).withNumberValidity()
  }

  /**
   * Handles a national number that was pasted with a country code or a redundant trunk prefix still attached (e.g.
   * "16105550103" or "02079460958"), splitting the country code into its own field and normalizing the national
   * number. Returns null if [digitsOnly] should be treated as a plain national number.
   *
   * We do all of this because the system autofill likes put the entire phone number in the national number field,
   * generating an invalid number by default.
   */
  private fun reinterpretNationalNumber(state: PhoneNumberEntryState, digitsOnly: String): PhoneNumberEntryState? {
    val countryCode = state.countryCode
    if (countryCode.isEmpty() || digitsOnly.isEmpty()) return null

    val withCountryCode = try {
      phoneNumberUtil.parse("+$countryCode$digitsOnly", null)
    } catch (_: NumberParseException) {
      return null
    }

    return when (phoneNumberUtil.isPossibleNumberWithReason(withCountryCode)) {
      PhoneNumberUtil.ValidationResult.TOO_LONG -> {
        if (isPossibleFullNumber(digitsOnly)) redistributeFullPhoneNumber(state, "+$digitsOnly") else null
      }
      PhoneNumberUtil.ValidationResult.IS_POSSIBLE -> {
        if (withCountryCode.nationalNumber.toString() != digitsOnly) redistributeFullPhoneNumber(state, "+$countryCode$digitsOnly") else null
      }
      else -> null
    }
  }

  /** True if [digits] parses into a plausible international number (i.e. it appears to already contain a country code). */
  private fun isPossibleFullNumber(digits: String): Boolean {
    return try {
      val number = phoneNumberUtil.parse("+$digits", null)
      number.nationalNumber != 0L && phoneNumberUtil.isPossibleNumber(number)
    } catch (_: NumberParseException) {
      false
    }
  }

  private fun insertedCharCount(old: String, new: String): Int {
    val max = minOf(old.length, new.length)

    var prefix = 0
    while (prefix < max && old[prefix] == new[prefix]) {
      prefix++
    }

    var suffix = 0
    while (suffix < max - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) {
      suffix++
    }

    return (new.length - prefix - suffix).coerceAtLeast(0)
  }

  /**
   * Recomputes [PhoneNumberEntryState.isNumberPossible] and [PhoneNumberEntryState.isNumberInvalid] from the current
   * country code and national number. Should be applied to any state that changes either of those fields.
   */
  private fun PhoneNumberEntryState.withNumberValidity(): PhoneNumberEntryState {
    if (countryCode.isEmpty() || nationalNumber.isEmpty()) {
      return copy(isNumberPossible = false, isNumberInvalid = false)
    }

    val parsedNumber = try {
      phoneNumberUtil.parse("+$countryCode$nationalNumber", null)
    } catch (_: NumberParseException) {
      return copy(isNumberPossible = false, isNumberInvalid = false)
    }

    val isNumberInvalid = when (phoneNumberUtil.isPossibleNumberWithReason(parsedNumber)) {
      PhoneNumberUtil.ValidationResult.TOO_LONG,
      PhoneNumberUtil.ValidationResult.INVALID_LENGTH,
      PhoneNumberUtil.ValidationResult.INVALID_COUNTRY_CODE -> true
      else -> false
    }
    val isNumberPossible = phoneNumberUtil.isPossibleNumber(parsedNumber)

    return if (this.isNumberInvalid != isNumberInvalid || this.isNumberPossible != isNumberPossible) {
      copy(isNumberPossible = isNumberPossible, isNumberInvalid = isNumberInvalid)
    } else {
      this
    }
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
