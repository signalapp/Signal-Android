/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui

import android.Manifest
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.Base64
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileContentUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileKeyUpdateJob
import org.thoughtcrime.securesms.jobs.ProfileUploadJob
import org.thoughtcrime.securesms.keyvalue.NewAccount
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.Skipped
import org.thoughtcrime.securesms.keyvalue.Start
import org.thoughtcrime.securesms.keyvalue.intendToRestore
import org.thoughtcrime.securesms.keyvalue.isDecisionPending
import org.thoughtcrime.securesms.keyvalue.isWantingManualRemoteRestore
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.pin.SvrRepository
import org.thoughtcrime.securesms.pin.SvrWrongPinException
import org.thoughtcrime.securesms.registration.data.AccountRegistrationResult
import org.thoughtcrime.securesms.registration.data.LocalRegistrationMetadataUtil
import org.thoughtcrime.securesms.registration.data.RegistrationData
import org.thoughtcrime.securesms.registration.data.network.BackupAuthCheckResult
import org.thoughtcrime.securesms.registration.data.network.Challenge
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionCheckResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionCreationResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionResult
import org.thoughtcrime.securesms.registration.data.network.SessionMetadataResult
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.AlreadyVerified
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.ChallengeRequired
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.ExternalServiceFailure
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.ImpossibleNumber
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.InvalidTransportModeFailure
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.MalformedRequest
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.NoSuchSession
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.NonNormalizedNumber
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.RateLimited
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.RegistrationLocked
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.RequestVerificationCodeRateLimited
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.SubmitVerificationCodeRateLimited
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.Success
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.TokenNotAccepted
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.UnknownError
import org.thoughtcrime.securesms.registration.ui.toE164
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.registration.viewmodel.SvrAuthCredentialSet
import org.thoughtcrime.securesms.registrationv3.data.RegistrationRepository
import org.thoughtcrime.securesms.registrationv3.ui.restore.StorageServiceRestore
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.dualsim.MccMncProducer
import org.whispersystems.signalservice.api.AccountEntropyPool
import org.whispersystems.signalservice.api.SvrNoDataException
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.svr.Svr3Credentials
import org.whispersystems.signalservice.api.websocket.WebSocketUnavailableException
import org.whispersystems.signalservice.internal.push.AuthCredentials
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.jvm.optionals.getOrNull
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * ViewModel shared across all of registration.
 */
class RegistrationViewModel : ViewModel() {

  private val store = MutableStateFlow(RegistrationState())
  private val password = Util.getSecret(18)

  private val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
    Log.w(TAG, "CoroutineExceptionHandler invoked!")
    handleGenericError(exception)
  }

  val state: StateFlow<RegistrationState> = store

  val uiState = store.asLiveData()

  val checkpoint = store.map { it.registrationCheckpoint }.asLiveData()

  val lockedTimeRemaining = store.map { it.lockedTimeRemaining }.asLiveData()

  val incorrectCodeAttempts = store.map { it.incorrectCodeAttempts }.asLiveData()

  val svrTriesRemaining: Int
    get() = store.value.svrTriesRemaining

  var isReregister: Boolean
    get() = store.value.isReRegister
    set(value) {
      store.update {
        it.copy(isReRegister = value)
      }
    }

  val phoneNumber: Phonenumber.PhoneNumber?
    get() = store.value.phoneNumber

  var nationalNumber: String
    get() = store.value.nationalNumber
    set(value) {
      store.update {
        it.copy(nationalNumber = value)
      }
    }

  fun maybePrefillE164(context: Context) {
    Log.v(TAG, "maybePrefillE164()")
    if (Permissions.hasAll(context, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)) {
      val localNumber = Util.getDeviceNumber(context).getOrNull()

      if (localNumber != null) {
        Log.v(TAG, "Phone number detected.")
        setPhoneNumber(localNumber)
      } else {
        Log.i(TAG, "Could not read phone number.")
      }
    } else {
      Log.i(TAG, "No phone permission.")
    }
  }

  fun setInProgress(inProgress: Boolean) {
    store.update {
      it.copy(inProgress = inProgress)
    }
  }

  fun setRegistrationCheckpoint(checkpoint: RegistrationCheckpoint) {
    store.update {
      it.copy(registrationCheckpoint = checkpoint)
    }
  }

  fun setPhoneNumber(phoneNumber: Phonenumber.PhoneNumber?) {
    store.update {
      it.copy(
        phoneNumber = phoneNumber,
        sessionId = null
      )
    }
  }

  fun setCaptchaResponse(token: String) {
    store.update {
      it.copy(
        registrationCheckpoint = RegistrationCheckpoint.CHALLENGE_COMPLETED,
        captchaToken = token
      )
    }
  }

  fun sessionCreationErrorShown() {
    store.update {
      it.copy(sessionCreationError = null)
    }
  }

  fun sessionStateErrorShown() {
    store.update {
      it.copy(sessionStateError = null)
    }
  }

  fun registerAccountErrorShown() {
    store.update {
      it.copy(registerAccountError = null)
    }
  }

  fun incrementIncorrectCodeAttempts() {
    store.update {
      it.copy(incorrectCodeAttempts = it.incorrectCodeAttempts + 1)
    }
  }

  fun addPresentedChallenge(challenge: Challenge) {
    store.update {
      it.copy(challengesPresented = it.challengesPresented.plus(challenge))
    }
  }

  fun removePresentedChallenge(challenge: Challenge) {
    store.update {
      it.copy(challengesPresented = it.challengesPresented.minus(challenge))
    }
  }

  fun fetchFcmToken(context: Context) {
    viewModelScope.launch(context = coroutineExceptionHandler) {
      val fcmToken = RegistrationRepository.getFcmToken(context)
      store.update {
        it.copy(registrationCheckpoint = RegistrationCheckpoint.PUSH_NETWORK_AUDITED, isFcmSupported = true, fcmToken = fcmToken)
      }
    }
  }

  private suspend fun updateFcmToken(context: Context): String? {
    Log.d(TAG, "Fetching FCM token…")
    val fcmToken = RegistrationRepository.getFcmToken(context)
    store.update {
      it.copy(fcmToken = fcmToken)
    }
    Log.d(TAG, "FCM token fetched.")
    return fcmToken
  }

  fun onBackupSuccessfullyRestored() {
    val recoveryPassword = SignalStore.svr.recoveryPassword
    store.update {
      it.copy(registrationCheckpoint = RegistrationCheckpoint.BACKUP_RESTORED_OR_SKIPPED, recoveryPassword = SignalStore.svr.recoveryPassword, canSkipSms = recoveryPassword != null, isReRegister = true)
    }
  }

  fun onUserConfirmedPhoneNumber(context: Context) {
    setRegistrationCheckpoint(RegistrationCheckpoint.PHONE_NUMBER_CONFIRMED)
    val state = store.value

    val e164 = state.phoneNumber?.toE164() ?: return bail { Log.i(TAG, "Phone number was null after confirmation.") }

    if (!state.userSkippedReregistration) {
      if (hasRecoveryPassword() && matchesSavedE164(e164)) {
        // Re-registration when the local database is intact.
        Log.d(TAG, "Has recovery password, and therefore can skip SMS verification.")
        store.update {
          it.copy(
            canSkipSms = true,
            isReRegister = true
          )
        }
        return
      }
    }

    viewModelScope.launch {
      if (!state.userSkippedReregistration) {
        val svrCredentialsResult: BackupAuthCheckResult = RegistrationRepository.hasValidSvrAuthCredentials(context, e164, password)

        when (svrCredentialsResult) {
          is BackupAuthCheckResult.UnknownError -> {
            handleGenericError(svrCredentialsResult.getCause())
            return@launch
          }

          is BackupAuthCheckResult.SuccessWithCredentials -> {
            Log.d(TAG, "Found local valid SVR auth credentials.")
            store.update {
              it.copy(
                isReRegister = true,
                canSkipSms = true,
                svr2AuthCredentials = svrCredentialsResult.svr2Credentials,
                svr3AuthCredentials = svrCredentialsResult.svr3Credentials
              )
            }
            return@launch
          }

          is BackupAuthCheckResult.SuccessWithoutCredentials -> {
            Log.d(TAG, "No local SVR auth credentials could be found and/or validated.")
          }
        }
      }

      val validSession = getOrCreateValidSession(context) ?: return@launch bail { Log.i(TAG, "Could not create valid session for confirming the entered E164.") }

      if (validSession.verified) {
        Log.i(TAG, "Session is already verified, registering account.")
        registerVerifiedSession(context, validSession.sessionId)
        return@launch
      }

      if (!validSession.allowedToRequestCode) {
        if (System.currentTimeMillis().milliseconds > validSession.nextVerificationAttempt) {
          store.update {
            it.copy(registrationCheckpoint = RegistrationCheckpoint.VERIFICATION_CODE_REQUESTED)
          }
        } else {
          Log.i(TAG, "Not allowed to request code! Remaining challenges: ${validSession.challengesRequested.joinToString()}")
          handleSessionStateResult(context, ChallengeRequired(validSession.challengesRequested))
        }
        return@launch
      }

      requestSmsCodeInternal(context, validSession.sessionId, e164)
    }
  }

  fun requestSmsCode(context: Context) {
    val e164 = getCurrentE164() ?: return bail { Log.i(TAG, "Phone number was null after confirmation.") }

    viewModelScope.launch {
      val validSession = getOrCreateValidSession(context) ?: return@launch bail { Log.i(TAG, "Could not create valid session for requesting an SMS code.") }
      requestSmsCodeInternal(context, validSession.sessionId, e164)
    }
  }

  fun requestVerificationCall(context: Context) {
    val e164 = getCurrentE164()

    if (e164 == null) {
      Log.w(TAG, "Phone number was null after confirmation.")
      setInProgress(false)
      return
    }

    viewModelScope.launch {
      val validSession = getOrCreateValidSession(context) ?: return@launch bail { Log.i(TAG, "Could not create valid session for requesting a verification call.") }
      Log.d(TAG, "Requesting voice call code…")
      val codeRequestResponse = RegistrationRepository.requestSmsCode(
        context = context,
        sessionId = validSession.sessionId,
        e164 = e164,
        password = password,
        mode = RegistrationRepository.E164VerificationMode.PHONE_CALL
      )
      Log.d(TAG, "Voice code request network call completed.")

      handleSessionStateResult(context, codeRequestResponse)
      if (codeRequestResponse is Success) {
        Log.d(TAG, "Voice code request was successful.")
      }
    }
  }

  private suspend fun requestSmsCodeInternal(context: Context, sessionId: String, e164: String) {
    var smsListenerReady = false
    Log.d(TAG, "Initializing SMS listener.")
    if (store.value.smsListenerTimeout < System.currentTimeMillis()) {
      smsListenerReady = store.value.isFcmSupported && RegistrationRepository.registerSmsListener(context)

      if (smsListenerReady) {
        val smsRetrieverTimeout = System.currentTimeMillis() + 5.minutes.inWholeMilliseconds
        Log.d(TAG, "Successfully started verification code SMS retriever, which will last until $smsRetrieverTimeout.")
        store.update { it.copy(smsListenerTimeout = smsRetrieverTimeout) }
      } else {
        Log.d(TAG, "Could not start verification code SMS retriever.")
      }
    }

    Log.d(TAG, "Requesting SMS code…")
    val transportMode = if (smsListenerReady) RegistrationRepository.E164VerificationMode.SMS_WITH_LISTENER else RegistrationRepository.E164VerificationMode.SMS_WITHOUT_LISTENER
    val codeRequestResponse = RegistrationRepository.requestSmsCode(
      context = context,
      sessionId = sessionId,
      e164 = e164,
      password = password,
      mode = transportMode
    )
    Log.d(TAG, "SMS code request network call completed.")

    if (codeRequestResponse is AlreadyVerified) {
      Log.d(TAG, "Got session was already verified when requesting SMS code.")
      registerVerifiedSession(context, sessionId)
      return
    }

    handleSessionStateResult(context, codeRequestResponse)

    if (codeRequestResponse is Success) {
      Log.d(TAG, "SMS code request was successful.")
      store.update {
        it.copy(
          registrationCheckpoint = RegistrationCheckpoint.VERIFICATION_CODE_REQUESTED
        )
      }
    }
  }

  private suspend fun getOrCreateValidSession(context: Context): SessionMetadataResult? {
    Log.v(TAG, "getOrCreateValidSession()")
    val e164 = getCurrentE164() ?: throw IllegalStateException("E164 required to create session!")
    val mccMncProducer = MccMncProducer(context)

    val existingSessionId = store.value.sessionId
    return getOrCreateValidSession(
      context = context,
      existingSessionId = existingSessionId,
      e164 = e164,
      password = password,
      mcc = mccMncProducer.mcc,
      mnc = mccMncProducer.mnc,
      successListener = { sessionData ->
        store.update {
          it.copy(
            sessionId = sessionData.sessionId,
            nextSmsTimestamp = sessionData.nextSmsTimestamp,
            nextCallTimestamp = sessionData.nextCallTimestamp,
            nextVerificationAttempt = sessionData.nextVerificationAttempt,
            allowedToRequestCode = sessionData.allowedToRequestCode,
            challengesRequested = sessionData.challengesRequested,
            verified = sessionData.verified
          )
        }
      },
      errorHandler = { error ->
        Log.d(TAG, "Setting ${error::class.simpleName} as session creation error.")
        store.update {
          it.copy(
            sessionCreationError = error,
            inProgress = false
          )
        }
      }
    )
  }

  fun submitCaptchaToken(context: Context) {
    val e164 = getCurrentE164() ?: throw IllegalStateException("Can't submit captcha token if no phone number is set!")
    val captchaToken = store.value.captchaToken ?: throw IllegalStateException("Can't submit captcha token if no captcha token is set!")

    store.update {
      it.copy(captchaToken = null)
    }

    viewModelScope.launch {
      val session = getOrCreateValidSession(context) ?: return@launch bail { Log.i(TAG, "Could not create valid session for submitting a captcha token.") }
      Log.d(TAG, "Submitting captcha token…")
      val captchaSubmissionResult = RegistrationRepository.submitCaptchaToken(context, e164, password, session.sessionId, captchaToken)
      Log.d(TAG, "Captcha token submitted.")

      handleSessionStateResult(context, captchaSubmissionResult)
    }
  }

  fun requestAndSubmitPushToken(context: Context) {
    Log.v(TAG, "validatePushToken()")

    addPresentedChallenge(Challenge.PUSH)

    val e164 = getCurrentE164() ?: throw IllegalStateException("Can't submit captcha token if no phone number is set!")

    viewModelScope.launch {
      Log.d(TAG, "Getting session in order to perform push token verification…")
      val session = getOrCreateValidSession(context) ?: return@launch bail { Log.i(TAG, "Could not create valid session for submitting a push challenge token.") }

      if (!session.challengesRequested.contains(Challenge.PUSH)) {
        return@launch bail { Log.i(TAG, "Push challenge token no longer needed, bailing.") }
      }

      Log.d(TAG, "Requesting push challenge token…")
      val pushSubmissionResult = RegistrationRepository.requestAndVerifyPushToken(context, session.sessionId, e164, password)
      Log.d(TAG, "Push challenge token submitted.")
      handleSessionStateResult(context, pushSubmissionResult)
    }
  }

  /**
   * @return whether the request was successful and execution should continue
   */
  private suspend fun handleSessionStateResult(context: Context, sessionResult: VerificationCodeRequestResult): Boolean {
    Log.v(TAG, "handleSessionStateResult()")
    when (sessionResult) {
      is UnknownError -> {
        handleGenericError(sessionResult.getCause())
      }

      is Success -> {
        Log.d(TAG, "New registration session status received.")
        updateFcmToken(context)
        store.update {
          it.copy(
            sessionId = sessionResult.sessionId,
            nextSmsTimestamp = sessionResult.nextSmsTimestamp,
            nextCallTimestamp = sessionResult.nextCallTimestamp,
            isAllowedToRequestCode = sessionResult.allowedToRequestCode,
            challengesRequested = emptyList()
          )
        }
        return true
      }

      is ChallengeRequired -> {
        Log.d(TAG, "[${sessionResult.challenges.joinToString()}] registration challenges received.")
        store.update {
          it.copy(
            registrationCheckpoint = RegistrationCheckpoint.CHALLENGE_RECEIVED,
            challengesRequested = sessionResult.challenges
          )
        }
        return false
      }

      is ImpossibleNumber -> Log.i(TAG, "Received ImpossibleNumber.", sessionResult.getCause())

      is NonNormalizedNumber -> Log.i(TAG, "Received NonNormalizedNumber.", sessionResult.getCause())

      is RateLimited -> Log.i(TAG, "Received RateLimited.", sessionResult.getCause())

      is ExternalServiceFailure -> Log.i(TAG, "Received ExternalServiceFailure.", sessionResult.getCause())

      is InvalidTransportModeFailure -> Log.i(TAG, "Received InvalidTransportModeFailure.", sessionResult.getCause())

      is MalformedRequest -> Log.i(TAG, "Received MalformedRequest.", sessionResult.getCause())

      is RequestVerificationCodeRateLimited -> {
        Log.i(TAG, "Received RequestVerificationCodeRateLimited.", sessionResult.getCause())

        if (sessionResult.willBeAbleToRequestAgain) {
          store.update {
            it.copy(
              nextSmsTimestamp = sessionResult.nextSmsTimestamp,
              nextCallTimestamp = sessionResult.nextCallTimestamp
            )
          }
        } else {
          Log.w(TAG, "Request verification code rate limit is forever, need to start new session")
          SignalStore.registration.sessionId = null
          store.update { RegistrationState() }
        }
      }

      is SubmitVerificationCodeRateLimited -> Log.i(TAG, "Received SubmitVerificationCodeRateLimited.", sessionResult.getCause())

      is TokenNotAccepted -> Log.i(TAG, "Received TokenNotAccepted.", sessionResult.getCause())

      is RegistrationLocked -> {
        store.update {
          it.copy(lockedTimeRemaining = sessionResult.timeRemaining)
        }
        Log.i(TAG, "Received RegistrationLocked.", sessionResult.getCause())
      }

      is NoSuchSession -> Log.i(TAG, "Received NoSuchSession.", sessionResult.getCause())

      is AlreadyVerified -> Log.i(TAG, "Received AlreadyVerified", sessionResult.getCause())
    }

    store.update {
      it.copy(
        inProgress = false,
        sessionStateError = sessionResult
      )
    }
    return false
  }

  /**
   * @return whether the request was successful and execution should continue
   */
  private suspend fun handleRegistrationResult(context: Context, registrationData: RegistrationData, registrationResult: RegisterAccountResult, reglockEnabled: Boolean): Boolean {
    Log.v(TAG, "handleRegistrationResult()")
    var stayInProgress = false
    when (registrationResult) {
      is RegisterAccountResult.Success -> {
        Log.i(TAG, "Register account result: Success! Registration lock: $reglockEnabled")
        store.update {
          it.copy(
            registrationCheckpoint = RegistrationCheckpoint.SERVICE_REGISTRATION_COMPLETED
          )
        }
        onSuccessfulRegistration(context, registrationData, registrationResult.accountRegistrationResult, reglockEnabled)
        return true
      }

      is RegisterAccountResult.IncorrectRecoveryPassword -> {
        Log.i(TAG, "Registration recovery password was incorrect, falling back to SMS verification.", registrationResult.getCause())
        setUserSkippedReRegisterFlow(true)
      }

      is RegisterAccountResult.RegistrationLocked -> {
        Log.i(TAG, "Account is registration locked!", registrationResult.getCause())
        stayInProgress = true
      }

      is RegisterAccountResult.SvrWrongPin -> {
        Log.i(TAG, "Received wrong SVR PIN response! ${registrationResult.triesRemaining} tries remaining.")
        updateSvrTriesRemaining(registrationResult.triesRemaining)
      }

      is RegisterAccountResult.SvrNoData,
      is RegisterAccountResult.AttemptsExhausted,
      is RegisterAccountResult.RateLimited,
      is RegisterAccountResult.AuthorizationFailed,
      is RegisterAccountResult.MalformedRequest,
      is RegisterAccountResult.ValidationError,
      is RegisterAccountResult.UnknownError -> Log.i(TAG, "Received error when trying to register!", registrationResult.getCause())
    }
    store.update {
      it.copy(
        inProgress = stayInProgress,
        registerAccountError = registrationResult
      )
    }
    return false
  }

  private fun handleGenericError(cause: Throwable) {
    Log.w(TAG, "Encountered unknown error!", cause)
    store.update {
      it.copy(inProgress = false, networkError = cause)
    }
  }

  private fun setRecoveryPassword(recoveryPassword: String?) {
    store.update {
      it.copy(recoveryPassword = recoveryPassword)
    }
  }

  private fun updateSvrTriesRemaining(remainingTries: Int) {
    store.update {
      it.copy(svrTriesRemaining = remainingTries)
    }
  }

  fun setUserSkippedReRegisterFlow(value: Boolean) {
    store.update {
      it.copy(userSkippedReregistration = value, canSkipSms = !value)
    }
  }

  fun verifyReRegisterWithPin(context: Context, pin: String, wrongPinHandler: () -> Unit) {
    setInProgress(true)

    // remote recovery password
    val svr2Credentials = store.value.svr2AuthCredentials ?: SignalStore.svr.svr2AuthTokens.toSvrCredentials()
    val svr3Credentials = store.value.svr3AuthCredentials ?: SignalStore.svr.svr3AuthTokens.toSvrCredentials()?.let { Svr3Credentials(it.username(), it.password(), null) }

    if (svr2Credentials != null || svr3Credentials != null) {
      Log.d(TAG, "Found SVR auth credentials, fetching recovery password from SVR (svr2: ${svr2Credentials != null}, svr3: ${svr3Credentials != null}).")
      viewModelScope.launch(context = coroutineExceptionHandler) {
        try {
          val masterKey = RegistrationRepository.fetchMasterKeyFromSvrRemote(pin, svr2Credentials, svr3Credentials)
          SignalStore.svr.masterKeyForInitialDataRestore = masterKey
          SignalStore.svr.setPin(pin)

          setRecoveryPassword(masterKey.deriveRegistrationRecoveryPassword())
          updateSvrTriesRemaining(10)
          verifyReRegisterInternal(context, pin, masterKey)
        } catch (rejectedPin: SvrWrongPinException) {
          Log.w(TAG, "Submitted PIN was rejected by SVR.", rejectedPin)
          updateSvrTriesRemaining(rejectedPin.triesRemaining)
          wrongPinHandler()
        } catch (noData: SvrNoDataException) {
          Log.w(TAG, "SVR has no data for these credentials. Aborting skip SMS flow.", noData)
          updateSvrTriesRemaining(0)
          setUserSkippedReRegisterFlow(true)
        }
      }
      return
    }

    // Local recovery password
    if (RegistrationRepository.canUseLocalRecoveryPassword()) {
      if (RegistrationRepository.doesPinMatchLocalHash(pin)) {
        Log.d(TAG, "Found recovery password, attempting to re-register.")
        viewModelScope.launch(context = coroutineExceptionHandler) {
          val masterKey = SignalStore.svr.masterKey
          setRecoveryPassword(masterKey.deriveRegistrationRecoveryPassword())
          verifyReRegisterInternal(context, pin, masterKey)
        }
      } else {
        Log.d(TAG, "Entered PIN did not match local PIN hash.")
        wrongPinHandler()
      }
      return
    }

    Log.w(TAG, "Could not get credentials to skip SMS registration, aborting!")
    store.update {
      it.copy(canSkipSms = false)
    }
  }

  private suspend fun verifyReRegisterInternal(context: Context, pin: String?, masterKey: MasterKey) {
    Log.v(TAG, "verifyReRegisterInternal(hasPin=${pin != null})")
    updateFcmToken(context)

    val registrationData = getRegistrationData()

    val resultAndRegLockStatus = registerAccountInternal(context, null, registrationData, pin, masterKey)
    val result = resultAndRegLockStatus.first
    val reglockEnabled = resultAndRegLockStatus.second

    handleRegistrationResult(context, registrationData, result, reglockEnabled)
  }

  /**
   * @return a [Pair] containing the server response and a boolean signifying whether the current account is registration locked.
   */
  private suspend fun registerAccountInternal(context: Context, sessionId: String?, registrationData: RegistrationData, pin: String?, masterKey: MasterKey): Pair<RegisterAccountResult, Boolean> {
    Log.v(TAG, "registerAccountInternal()")
    var registrationResult: RegisterAccountResult = RegistrationRepository.registerAccount(context = context, sessionId = sessionId, registrationData = registrationData, pin = pin)

    // Check if reg lock is enabled
    if (registrationResult !is RegisterAccountResult.RegistrationLocked) {
      if (registrationResult is RegisterAccountResult.Success) {
        registrationResult = RegisterAccountResult.Success(registrationResult.accountRegistrationResult.copy(masterKey = masterKey))
      }

      Log.i(TAG, "Received a non-registration lock response to registration. Assuming registration lock as DISABLED")
      return Pair(registrationResult, false)
    }

    Log.i(TAG, "Received a registration lock response when trying to register an account. Retrying with master key.")
    store.update {
      it.copy(
        svr2AuthCredentials = registrationResult.svr2Credentials,
        svr3AuthCredentials = registrationResult.svr3Credentials
      )
    }

    return Pair(RegistrationRepository.registerAccount(context = context, sessionId = sessionId, registrationData = registrationData, pin = pin) { masterKey }, true)
  }

  fun verifyCodeWithoutRegistrationLock(context: Context, code: String) {
    Log.v(TAG, "verifyCodeWithoutRegistrationLock()")
    store.update {
      it.copy(
        inProgress = true,
        enteredCode = code,
        registrationCheckpoint = RegistrationCheckpoint.VERIFICATION_CODE_ENTERED
      )
    }

    viewModelScope.launch(context = coroutineExceptionHandler) {
      verifyCodeInternal(
        context = context,
        registrationLocked = false,
        pin = null
      )
    }
  }

  fun verifyCodeAndRegisterAccountWithRegistrationLock(context: Context, pin: String) {
    Log.v(TAG, "verifyCodeAndRegisterAccountWithRegistrationLock()")
    store.update {
      it.copy(
        inProgress = true,
        registrationCheckpoint = RegistrationCheckpoint.PIN_ENTERED
      )
    }
    viewModelScope.launch {
      verifyCodeInternal(
        context = context,
        registrationLocked = true,
        pin = pin
      )
    }
  }

  private suspend fun verifyCodeInternal(context: Context, registrationLocked: Boolean, pin: String?) {
    Log.d(TAG, "Getting valid session in order to submit verification code.")

    if (registrationLocked && pin.isNullOrBlank()) {
      throw IllegalStateException("Must have PIN to register with registration lock!")
    }

    var reglock = registrationLocked

    val session: SessionMetadataResult? = getOrCreateValidSession(context)
    val sessionId: String = session?.sessionId ?: return
    val registrationData: RegistrationData = getRegistrationData()

    if (session.verified) {
      Log.i(TAG, "Session is already verified, registering account.")
    } else {
      Log.d(TAG, "Submitting verification code…")

      val verificationResponse = RegistrationRepository.submitVerificationCode(context, sessionId, registrationData)

      val submissionSuccessful = verificationResponse is Success
      val alreadyVerified = verificationResponse is AlreadyVerified

      Log.d(TAG, "Verification code submission network call completed. Submission successful? $submissionSuccessful Account already verified? $alreadyVerified")

      if (!submissionSuccessful && !alreadyVerified) {
        handleSessionStateResult(context, verificationResponse)
        return
      }
    }

    Log.d(TAG, "Submitting registration…")

    var result: RegisterAccountResult? = null
    var state = store.value

    if (!reglock) {
      Log.d(TAG, "Registration lock not enabled, attempting to register account without master key producer.")
      result = RegistrationRepository.registerAccount(context, sessionId, registrationData, pin)
    }

    if (result is RegisterAccountResult.RegistrationLocked) {
      Log.d(TAG, "Registration lock response received.")
      val timeRemaining = result.timeRemaining
      store.update {
        it.copy(lockedTimeRemaining = timeRemaining)
      }
      reglock = true
      if (pin == null && SignalStore.svr.registrationLockToken != null) {
        Log.d(TAG, "Retrying registration with stored credentials.")
        result = RegistrationRepository.registerAccount(context, sessionId, registrationData, SignalStore.svr.pin) { SignalStore.svr.masterKey }
      } else if (result.svr2Credentials != null || result.svr3Credentials != null) {
        Log.d(TAG, "Retrying registration with received credentials (svr2: ${result.svr2Credentials != null}, svr3: ${result.svr3Credentials != null}).")
        val svr2Credentials = result.svr2Credentials
        val svr3Credentials = result.svr3Credentials
        state = store.updateAndGet {
          it.copy(svr2AuthCredentials = svr2Credentials, svr3AuthCredentials = svr3Credentials)
        }
      }
    }

    if (reglock && pin.isNotNullOrBlank()) {
      Log.d(TAG, "Registration lock enabled, attempting to register account restore master key from SVR (svr2: ${state.svr2AuthCredentials != null}, svr3: ${state.svr3AuthCredentials != null})")
      result = RegistrationRepository.registerAccount(context, sessionId, registrationData, pin) {
        SvrRepository.restoreMasterKeyPreRegistration(
          credentials = SvrAuthCredentialSet(
            svr2Credentials = state.svr2AuthCredentials,
            svr3Credentials = state.svr3AuthCredentials
          ),
          userPin = pin
        )
      }
    }

    if (result != null) {
      handleRegistrationResult(context, registrationData, result, reglock)
    } else {
      Log.w(TAG, "No registration response received!")
    }
  }

  private suspend fun registerVerifiedSession(context: Context, sessionId: String) {
    Log.v(TAG, "registerVerifiedSession()")
    val registrationData = getRegistrationData()
    val registrationResult: RegisterAccountResult = RegistrationRepository.registerAccount(context = context, sessionId = sessionId, registrationData = registrationData)

    val reglockEnabled = if (registrationResult is RegisterAccountResult.RegistrationLocked) {
      Log.i(TAG, "Received a registration lock response when trying to register verified session. Retrying with master key.")
      store.update {
        it.copy(
          svr2AuthCredentials = registrationResult.svr2Credentials,
          svr3AuthCredentials = registrationResult.svr3Credentials
        )
      }
      true
    } else {
      false
    }

    handleRegistrationResult(context, registrationData, registrationResult, reglockEnabled)
  }

  private suspend fun onSuccessfulRegistration(context: Context, registrationData: RegistrationData, remoteResult: AccountRegistrationResult, reglockEnabled: Boolean) = withContext(Dispatchers.IO) {
    Log.v(TAG, "onSuccessfulRegistration()")
    val metadata = LocalRegistrationMetadataUtil.createLocalRegistrationMetadata(SignalStore.account.aciIdentityKey, SignalStore.account.pniIdentityKey, registrationData, remoteResult, reglockEnabled)
    SignalStore.registration.localRegistrationMetadata = metadata
    RegistrationRepository.registerAccountLocally(context, metadata)

    try {
      AppDependencies.authWebSocket.connect()
    } catch (e: WebSocketUnavailableException) {
      Log.w(TAG, "Unable to start auth websocket", e)
    }

    if (!remoteResult.storageCapable && SignalStore.registration.restoreDecisionState.isDecisionPending) {
      Log.v(TAG, "Not storage capable and still pending restore decision, likely an account with no data to restore, skipping post register restore")
      SignalStore.registration.restoreDecisionState = RestoreDecisionState.NewAccount
    }

    if (reglockEnabled || SignalStore.svr.hasOptedInWithAccess()) {
      SignalStore.onboarding.clearAll()

      if (!SignalStore.registration.restoreDecisionState.isDecisionPending) {
        Log.d(TAG, "No pending restore decisions, can restore account from storage service")
        StorageServiceRestore.restore()
      }
    }

    if (SignalStore.account.restoredAccountEntropyPool) {
      Log.d(TAG, "Restoring backup tier")
      BackupRepository.restoreBackupTier(SignalStore.account.requireAci())
    }

    refreshRemoteConfig()

    val checkpoint = if (SignalStore.registration.restoreDecisionState.isDecisionPending &&
      SignalStore.registration.restoreDecisionState.isWantingManualRemoteRestore &&
      (!SignalStore.backup.isBackupTierRestored || SignalStore.backup.lastBackupTime == 0L)
    ) {
      RegistrationCheckpoint.BACKUP_TIER_NOT_RESTORED
    } else {
      RegistrationCheckpoint.LOCAL_REGISTRATION_COMPLETE
    }

    store.update {
      it.copy(
        registrationCheckpoint = checkpoint
      )
    }
  }

  fun resetRestoreDecision() {
    SignalStore.registration.restoreDecisionState = RestoreDecisionState.Start
  }

  fun intendToRestore(hasOldDevice: Boolean, fromRemote: Boolean? = null) {
    SignalStore.registration.restoreDecisionState = RestoreDecisionState.intendToRestore(hasOldDevice, fromRemote)
  }

  fun skipRestore() {
    SignalStore.registration.restoreDecisionState = RestoreDecisionState.Skipped
  }

  fun resumeNormalRegistration() {
    store.update {
      it.copy(registrationCheckpoint = RegistrationCheckpoint.LOCAL_REGISTRATION_COMPLETE)
    }
  }

  fun restoreBackupTier() {
    store.update {
      it.copy(inProgress = true, registrationCheckpoint = RegistrationCheckpoint.SERVICE_REGISTRATION_COMPLETED)
    }

    viewModelScope.launch {
      val start = System.currentTimeMillis()
      val tierUnknown = BackupRepository.restoreBackupTier(SignalStore.account.requireAci()) == null
      delay(max(0L, 500L - (System.currentTimeMillis() - start)))

      if (tierUnknown || SignalStore.backup.lastBackupTime == 0L) {
        store.update {
          it.copy(registrationCheckpoint = RegistrationCheckpoint.BACKUP_TIER_NOT_RESTORED)
        }
      } else {
        store.update {
          it.copy(registrationCheckpoint = RegistrationCheckpoint.LOCAL_REGISTRATION_COMPLETE)
        }
      }
    }
  }

  fun completeRegistration() {
    AppDependencies.jobManager.startChain(ProfileUploadJob()).then(listOf(MultiDeviceProfileKeyUpdateJob(), MultiDeviceProfileContentUpdateJob())).enqueue()
    RegistrationUtil.maybeMarkRegistrationComplete()
  }

  fun networkErrorShown() {
    store.update {
      it.copy(networkError = null)
    }
  }

  private fun matchesSavedE164(e164: String?): Boolean {
    return if (e164 == null) {
      false
    } else {
      e164 == SignalStore.account.e164
    }
  }

  private fun hasRecoveryPassword(): Boolean {
    return store.value.recoveryPassword != null
  }

  private fun getCurrentE164(): String? {
    return store.value.phoneNumber?.toE164()
  }

  private suspend fun getRegistrationData(): RegistrationData {
    val currentState = store.value
    val code = currentState.enteredCode
    val e164: String = currentState.phoneNumber?.toE164() ?: throw IllegalStateException("Can't construct registration data without E164!")
    val recoveryPassword = if (currentState.sessionId == null && hasRecoveryPassword()) store.value.recoveryPassword!! else null
    return RegistrationData(code, e164, password, RegistrationRepository.getRegistrationId(), RegistrationRepository.getProfileKey(e164), currentState.fcmToken, RegistrationRepository.getPniRegistrationId(), recoveryPassword)
  }

  /**
   * Used for early returns in order to end the in-progress visual state, as well as print a log message explaining what happened.
   *
   * @param logMessage Logging code is wrapped in lambda so that our automated tools detect the various [Log] calls with their accompanying messages.
   */
  private fun bail(logMessage: () -> Unit) {
    logMessage()
    setInProgress(false)
  }

  fun registerWithBackupKey(context: Context, backupKey: String, e164: String?, pin: String?) {
    setInProgress(true)

    viewModelScope.launch(context = coroutineExceptionHandler) {
      if (e164 != null) {
        setPhoneNumber(PhoneNumberUtil.getInstance().parse(e164, null))
      }

      val accountEntropyPool = AccountEntropyPool(backupKey)
      SignalStore.account.restoreAccountEntropyPool(accountEntropyPool)

      val masterKey = accountEntropyPool.deriveMasterKey()
      setRecoveryPassword(masterKey.deriveRegistrationRecoveryPassword())
      verifyReRegisterInternal(context = context, pin = pin, masterKey = masterKey)

      setInProgress(false)
    }
  }

  /** Converts the basic-auth creds we have locally into username:password pairs that are suitable for handing off to the service. */
  private fun List<String?>.toSvrCredentials(): AuthCredentials? {
    return this
      .asSequence()
      .filterNotNull()
      .map { it.replace("Basic ", "").trim() }
      .mapNotNull {
        try {
          Base64.decode(it)
        } catch (e: IOException) {
          Log.w(TAG, "Encountered error trying to decode a token!", e)
          null
        }
      }
      .map { String(it, StandardCharsets.ISO_8859_1) }
      .mapNotNull {
        val colonIndex = it.indexOf(":")
        if (colonIndex < 0) {
          return@mapNotNull null
        }
        AuthCredentials.create(it.substring(0, colonIndex), it.substring(colonIndex + 1))
      }
      .firstOrNull()
  }

  companion object {
    private val TAG = Log.tag(RegistrationViewModel::class.java)

    private suspend fun refreshRemoteConfig() = withContext(Dispatchers.IO) {
      val startTime = System.currentTimeMillis()
      try {
        RemoteConfig.refreshSync()
        Log.i(TAG, "Took " + (System.currentTimeMillis() - startTime) + " ms to get feature flags.")
      } catch (e: IOException) {
        Log.w(TAG, "Failed to refresh flags after " + (System.currentTimeMillis() - startTime) + " ms.", e)
      }
    }

    suspend fun getOrCreateValidSession(
      context: Context,
      existingSessionId: String?,
      e164: String,
      password: String,
      mcc: String?,
      mnc: String?,
      successListener: (SessionMetadataResult) -> Unit,
      errorHandler: (RegistrationSessionResult) -> Unit
    ): SessionMetadataResult? {
      Log.d(TAG, "Validating/creating a registration session.")
      val sessionResult: RegistrationSessionResult = RegistrationRepository.createOrValidateSession(context, existingSessionId, e164, password, mcc, mnc)
      when (sessionResult) {
        is RegistrationSessionCheckResult.Success -> {
          successListener(sessionResult)
          Log.d(TAG, "Registration session validated.")
          return sessionResult
        }

        is RegistrationSessionCreationResult.Success -> {
          successListener(sessionResult)
          Log.d(TAG, "Registration session created.")
          return sessionResult
        }

        else -> {
          Log.d(TAG, "Handling error during session creation.")
          errorHandler(sessionResult)
        }
      }
      return null
    }
  }
}
