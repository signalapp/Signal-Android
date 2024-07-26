/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui

import android.Manifest
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.i18n.phonenumbers.Phonenumber
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.Stopwatch
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileContentUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileKeyUpdateJob
import org.thoughtcrime.securesms.jobs.ProfileUploadJob
import org.thoughtcrime.securesms.jobs.ReclaimUsernameAndLinkJob
import org.thoughtcrime.securesms.jobs.StorageAccountRestoreJob
import org.thoughtcrime.securesms.jobs.StorageSyncJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.pin.SvrRepository
import org.thoughtcrime.securesms.pin.SvrWrongPinException
import org.thoughtcrime.securesms.registration.RegistrationData
import org.thoughtcrime.securesms.registration.RegistrationUtil
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.data.network.BackupAuthCheckResult
import org.thoughtcrime.securesms.registration.data.network.Challenge
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionCheckResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionCreationResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionResult
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.AlreadyVerified
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.AttemptsExhausted
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.ChallengeRequired
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.ExternalServiceFailure
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.ImpossibleNumber
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.InvalidTransportModeFailure
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.MalformedRequest
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.MustRetry
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.NoSuchSession
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.NonNormalizedNumber
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.RateLimited
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.RegistrationLocked
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.Success
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.TokenNotAccepted
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult.UnknownError
import org.thoughtcrime.securesms.registration.viewmodel.SvrAuthCredentialSet
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.dualsim.MccMncProducer
import org.whispersystems.signalservice.api.SvrNoDataException
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.minutes

/**
 * ViewModel shared across all of registration.
 */
class RegistrationViewModel : ViewModel() {

  private val store = MutableStateFlow(RegistrationState())
  private val password = Util.getSecret(18)

  private val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
    Log.w(TAG, "CoroutineExceptionHandler invoked.", exception)
    store.update {
      it.copy(
        networkError = exception,
        inProgress = false
      )
    }
  }

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
            isReRegister = true,
            inProgress = false
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
                svr3AuthCredentials = svrCredentialsResult.svr3Credentials,
                inProgress = false
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

      if (validSession.body.verified) {
        Log.i(TAG, "Session is already verified, registering account.")
        registerVerifiedSession(context, validSession.body.id)
        return@launch
      }

      if (!validSession.body.allowedToRequestCode) {
        if (System.currentTimeMillis() > (validSession.body.nextVerificationAttempt ?: Int.MAX_VALUE)) {
          store.update {
            it.copy(registrationCheckpoint = RegistrationCheckpoint.VERIFICATION_CODE_REQUESTED)
          }
        } else {
          val challenges = validSession.body.requestedInformation
          Log.i(TAG, "Not allowed to request code! Remaining challenges: ${challenges.joinToString()}")
          handleSessionStateResult(context, ChallengeRequired(Challenge.parse(validSession.body.requestedInformation)))
        }
        return@launch
      }

      requestSmsCodeInternal(context, validSession.body.id, e164)
    }
  }

  fun requestSmsCode(context: Context) {
    val e164 = getCurrentE164() ?: return bail { Log.i(TAG, "Phone number was null after confirmation.") }

    viewModelScope.launch {
      val validSession = getOrCreateValidSession(context) ?: return@launch bail { Log.i(TAG, "Could not create valid session for requesting an SMS code.") }
      requestSmsCodeInternal(context, validSession.body.id, e164)
    }
  }

  fun requestVerificationCall(context: Context) {
    val e164 = getCurrentE164()

    if (e164 == null) {
      Log.w(TAG, "Phone number was null after confirmation.")
      onErrorOccurred()
      return
    }

    viewModelScope.launch {
      val validSession = getOrCreateValidSession(context) ?: return@launch bail { Log.i(TAG, "Could not create valid session for requesting a verification call.") }
      Log.d(TAG, "Requesting voice call code…")
      val codeRequestResponse = RegistrationRepository.requestSmsCode(
        context = context,
        sessionId = validSession.body.id,
        e164 = e164,
        password = password,
        mode = RegistrationRepository.Mode.PHONE_CALL
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
    val transportMode = if (smsListenerReady) RegistrationRepository.Mode.SMS_WITH_LISTENER else RegistrationRepository.Mode.SMS_WITHOUT_LISTENER
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

  private suspend fun getOrCreateValidSession(context: Context): RegistrationSessionMetadataResponse? {
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
      successListener = { networkResult ->
        store.update {
          it.copy(
            sessionId = networkResult.body.id,
            nextSmsTimestamp = RegistrationRepository.deriveTimestamp(networkResult.headers, networkResult.body.nextSms),
            nextCallTimestamp = RegistrationRepository.deriveTimestamp(networkResult.headers, networkResult.body.nextCall),
            nextVerificationAttempt = RegistrationRepository.deriveTimestamp(networkResult.headers, networkResult.body.nextVerificationAttempt),
            allowedToRequestCode = networkResult.body.allowedToRequestCode,
            challengesRequested = Challenge.parse(networkResult.body.requestedInformation),
            verified = networkResult.body.verified
          )
        }
      },
      errorHandler = { error ->
        store.update {
          it.copy(
            sessionCreationError = error
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
      val captchaSubmissionResult = RegistrationRepository.submitCaptchaToken(context, e164, password, session.body.id, captchaToken)
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

      if (!Challenge.parse(session.body.requestedInformation).contains(Challenge.PUSH)) {
        Log.d(TAG, "Push submission no longer necessary, bailing.")
        store.update {
          it.copy(
            inProgress = false
          )
        }
        return@launch bail { Log.i(TAG, "Push challenge token no longer needed, bailing.") }
      }

      Log.d(TAG, "Requesting push challenge token…")
      val pushSubmissionResult = RegistrationRepository.requestAndVerifyPushToken(context, session.body.id, e164, password)
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
            challengesRequested = emptyList(),
            inProgress = false
          )
        }
        return true
      }

      is ChallengeRequired -> {
        Log.d(TAG, "[${sessionResult.challenges.joinToString()}] registration challenges received.")
        store.update {
          it.copy(
            registrationCheckpoint = RegistrationCheckpoint.CHALLENGE_RECEIVED,
            challengesRequested = sessionResult.challenges,
            inProgress = false
          )
        }
        return false
      }

      is AttemptsExhausted -> Log.i(TAG, "Received AttemptsExhausted.", sessionResult.getCause())

      is ImpossibleNumber -> Log.i(TAG, "Received ImpossibleNumber.", sessionResult.getCause())

      is NonNormalizedNumber -> Log.i(TAG, "Received NonNormalizedNumber.", sessionResult.getCause())

      is RateLimited -> Log.i(TAG, "Received RateLimited.", sessionResult.getCause())

      is ExternalServiceFailure -> Log.i(TAG, "Received ExternalServiceFailure.", sessionResult.getCause())

      is InvalidTransportModeFailure -> Log.i(TAG, "Received InvalidTransportModeFailure.", sessionResult.getCause())

      is MalformedRequest -> Log.i(TAG, "Received MalformedRequest.", sessionResult.getCause())

      is MustRetry -> Log.i(TAG, "Received MustRetry.", sessionResult.getCause())

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
    setInProgress(false)
    store.update {
      it.copy(
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
    setInProgress(false)
    store.update {
      it.copy(
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

    // Local recovery password
    if (RegistrationRepository.canUseLocalRecoveryPassword()) {
      if (RegistrationRepository.doesPinMatchLocalHash(pin)) {
        Log.d(TAG, "Found recovery password, attempting to re-register.")
        viewModelScope.launch(context = coroutineExceptionHandler) {
          verifyReRegisterInternal(context, pin, SignalStore.svr.getOrCreateMasterKey())
          setInProgress(false)
        }
      } else {
        Log.d(TAG, "Entered PIN did not match local PIN hash.")
        wrongPinHandler()
        setInProgress(false)
      }
      return
    }

    // remote recovery password
    val svr2Credentials = store.value.svr2AuthCredentials
    val svr3Credentials = store.value.svr3AuthCredentials

    if (svr2Credentials != null || svr3Credentials != null) {
      Log.d(TAG, "Found SVR auth credentials, fetching recovery password from SVR (svr2: ${svr2Credentials != null}, svr3: ${svr3Credentials != null}).")
      viewModelScope.launch(context = coroutineExceptionHandler) {
        try {
          val masterKey = RegistrationRepository.fetchMasterKeyFromSvrRemote(pin, svr2Credentials, svr3Credentials)
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
        setInProgress(false)
      }
      return
    }

    Log.w(TAG, "Could not get credentials to skip SMS registration, aborting!")
    store.update {
      it.copy(canSkipSms = false, inProgress = false)
    }
  }

  private suspend fun verifyReRegisterInternal(context: Context, pin: String, masterKey: MasterKey) {
    Log.v(TAG, "verifyReRegisterInternal()")
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
    val registrationResult: RegisterAccountResult = RegistrationRepository.registerAccount(context = context, sessionId = sessionId, registrationData = registrationData, pin = pin) { masterKey }

    // Check if reg lock is enabled
    if (registrationResult !is RegisterAccountResult.RegistrationLocked) {
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

    val sessionId = getOrCreateValidSession(context)?.body?.id ?: return
    val registrationData = getRegistrationData()

    Log.d(TAG, "Submitting verification code…")

    val verificationResponse = RegistrationRepository.submitVerificationCode(context, sessionId, registrationData)

    val submissionSuccessful = verificationResponse is Success
    val alreadyVerified = verificationResponse is AlreadyVerified

    Log.d(TAG, "Verification code submission network call completed. Submission successful? $submissionSuccessful Account already verified? $alreadyVerified")

    if (!submissionSuccessful && !alreadyVerified) {
      handleSessionStateResult(context, verificationResponse)
      return
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
        result = RegistrationRepository.registerAccount(context, sessionId, registrationData, SignalStore.svr.pin) { SignalStore.svr.getOrCreateMasterKey() }
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
    val registrationResponse: RegisterAccountResult = RegistrationRepository.registerAccount(context, sessionId, registrationData)
    handleRegistrationResult(context, registrationData, registrationResponse, false)
  }

  private suspend fun onSuccessfulRegistration(context: Context, registrationData: RegistrationData, remoteResult: RegistrationRepository.AccountRegistrationResult, reglockEnabled: Boolean) {
    Log.v(TAG, "onSuccessfulRegistration()")
    RegistrationRepository.registerAccountLocally(context, registrationData, remoteResult, reglockEnabled)

    if (reglockEnabled) {
      SignalStore.onboarding.clearAll()
      val stopwatch = Stopwatch("RegistrationLockRestore")

      AppDependencies.jobManager.runSynchronously(StorageAccountRestoreJob(), StorageAccountRestoreJob.LIFESPAN)
      stopwatch.split("AccountRestore")

      AppDependencies.jobManager
        .startChain(StorageSyncJob())
        .then(ReclaimUsernameAndLinkJob())
        .enqueueAndBlockUntilCompletion(TimeUnit.SECONDS.toMillis(10))
      stopwatch.split("ContactRestore")

      refreshRemoteConfig()

      stopwatch.split("RemoteConfig")

      stopwatch.stop(TAG)
    } else {
      refreshRemoteConfig()
    }

    store.update {
      it.copy(
        registrationCheckpoint = RegistrationCheckpoint.LOCAL_REGISTRATION_COMPLETE,
        inProgress = false
      )
    }
  }

  fun hasPin(): Boolean {
    return RegistrationRepository.hasPin() || store.value.isReRegister
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
    val recoveryPassword = if (currentState.sessionId == null) SignalStore.svr.getRecoveryPassword() else null
    return RegistrationData(code, e164, password, RegistrationRepository.getRegistrationId(), RegistrationRepository.getProfileKey(e164), currentState.fcmToken, RegistrationRepository.getPniRegistrationId(), recoveryPassword)
  }

  /**
   * This is a generic error UI handler that re-enables the UI so that the user can recover from errors.
   * Do not forget to log any errors when calling this method!
   */
  private fun onErrorOccurred() {
    setInProgress(false)
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
      successListener: (RegistrationSessionMetadataResponse) -> Unit,
      errorHandler: (RegistrationSessionResult) -> Unit
    ): RegistrationSessionMetadataResponse? {
      Log.d(TAG, "Validating/creating a registration session.")
      val sessionResult: RegistrationSessionResult = RegistrationRepository.createOrValidateSession(context, existingSessionId, e164, password, mcc, mnc)
      when (sessionResult) {
        is RegistrationSessionCheckResult.Success -> {
          val metadata = sessionResult.getMetadata()
          successListener(metadata)
          Log.d(TAG, "Registration session validated.")
          return metadata
        }

        is RegistrationSessionCreationResult.Success -> {
          val metadata = sessionResult.getMetadata()
          successListener(metadata)
          Log.d(TAG, "Registration session created.")
          return metadata
        }

        else -> errorHandler(sessionResult)
      }
      return null
    }
  }
}
