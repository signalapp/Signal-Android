/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileContentUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileKeyUpdateJob
import org.thoughtcrime.securesms.jobs.ProfileUploadJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.pin.SvrWrongPinException
import org.thoughtcrime.securesms.registration.RegistrationData
import org.thoughtcrime.securesms.registration.RegistrationUtil
import org.thoughtcrime.securesms.registration.v2.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.v2.data.network.BackupAuthCheckResult
import org.thoughtcrime.securesms.registration.v2.data.network.RegistrationSessionCheckResult
import org.thoughtcrime.securesms.registration.v2.data.network.RegistrationSessionCreationResult
import org.thoughtcrime.securesms.registration.v2.data.network.RegistrationSessionResult
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult.AttemptsExhausted
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult.ChallengeRequired
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult.ExternalServiceFailure
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult.ImpossibleNumber
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult.InvalidTransportModeFailure
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult.MalformedRequest
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult.MustRetry
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult.NonNormalizedNumber
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult.RateLimited
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult.RegistrationLocked
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult.Success
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult.TokenNotAccepted
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult.UnknownError
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.dualsim.MccMncProducer
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.SvrNoDataException
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.internal.push.LockedException
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

/**
 * ViewModel shared across all of registration.
 */
class RegistrationV2ViewModel : ViewModel() {

  private val store = MutableStateFlow(RegistrationV2State())
  private val password = Util.getSecret(18) // TODO [regv2]: persist this

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

  init {
    val existingE164 = SignalStore.registrationValues().sessionE164
    if (existingE164 != null) {
      try {
        val existingPhoneNumber = PhoneNumberUtil.getInstance().parse(existingE164, null)
        if (existingPhoneNumber != null) {
          setPhoneNumber(existingPhoneNumber)
        }
      } catch (ex: NumberParseException) {
        Log.w(TAG, "Could not parse stored E164.", ex)
      }
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
      it.copy(phoneNumber = phoneNumber)
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
    val recoveryPassword = SignalStore.svr().recoveryPassword
    store.update {
      it.copy(registrationCheckpoint = RegistrationCheckpoint.BACKUP_RESTORED_OR_SKIPPED, recoveryPassword = SignalStore.svr().recoveryPassword, canSkipSms = recoveryPassword != null)
    }
  }

  fun onUserConfirmedPhoneNumber(context: Context, errorHandler: (VerificationCodeRequestResult, RegistrationRepository.Mode) -> Unit) {
    setRegistrationCheckpoint(RegistrationCheckpoint.PHONE_NUMBER_CONFIRMED)
    val state = store.value
    if (state.phoneNumber == null) {
      Log.w(TAG, "Phone number was null after confirmation.")
      onErrorOccurred()
      return
    }

    val e164 = state.phoneNumber.toE164()
    if (hasRecoveryPassword() && matchesSavedE164(e164)) {
      // Re-registration when the local database is intact.
      Log.d(TAG, "Has recovery password, and therefore can skip SMS verification.")
      store.update {
        it.copy(
          canSkipSms = true,
          inProgress = false
        )
      }
      return
    }

    viewModelScope.launch {
      val svrCredentialsResult = RegistrationRepository.hasValidSvrAuthCredentials(context, e164, password)

      when (svrCredentialsResult) {
        is BackupAuthCheckResult.UnknownError -> {
          handleGenericError(svrCredentialsResult.getCause())
          return@launch
        }

        is BackupAuthCheckResult.SuccessWithCredentials -> {
          Log.d(TAG, "Found local valid SVR auth credentials.")
          store.update {
            it.copy(canSkipSms = true, svrAuthCredentials = svrCredentialsResult.authCredentials, inProgress = false)
          }
          return@launch
        }

        is BackupAuthCheckResult.SuccessWithoutCredentials -> Log.d(TAG, "No local SVR auth credentials could be found and/or validated.")
      }

      val validSession = getOrCreateValidSession(context) ?: return@launch

      if (!validSession.body.allowedToRequestCode) {
        val challenges = validSession.body.requestedInformation.joinToString()
        Log.i(TAG, "Not allowed to request code! Remaining challenges: $challenges")
        handleSessionStateResult(context, ChallengeRequired(validSession.body.requestedInformation), RegistrationRepository.Mode.NONE, errorHandler)
        return@launch
      }

      requestSmsCodeInternal(context, validSession.body.id, e164, errorHandler)
    }
  }

  fun requestSmsCode(context: Context, errorHandler: (VerificationCodeRequestResult, RegistrationRepository.Mode) -> Unit) {
    val e164 = getCurrentE164()

    if (e164 == null) {
      Log.w(TAG, "Phone number was null after confirmation.")
      onErrorOccurred()
      return
    }

    viewModelScope.launch {
      val validSession = getOrCreateValidSession(context) ?: return@launch
      requestSmsCodeInternal(context, validSession.body.id, e164, errorHandler)
    }
  }

  fun requestVerificationCall(context: Context, errorHandler: (VerificationCodeRequestResult, RegistrationRepository.Mode) -> Unit) {
    val e164 = getCurrentE164()

    if (e164 == null) {
      Log.w(TAG, "Phone number was null after confirmation.")
      onErrorOccurred()
      return
    }

    viewModelScope.launch {
      val validSession = getOrCreateValidSession(context) ?: return@launch
      Log.d(TAG, "Requesting voice call code…")
      val codeRequestResponse = RegistrationRepository.requestSmsCode(
        context = context,
        sessionId = validSession.body.id,
        e164 = e164,
        password = password,
        mode = RegistrationRepository.Mode.PHONE_CALL
      )
      Log.d(TAG, "Voice call code request submitted.")

      handleSessionStateResult(context, codeRequestResponse, RegistrationRepository.Mode.PHONE_CALL, errorHandler)
    }
  }

  private suspend fun requestSmsCodeInternal(context: Context, sessionId: String, e164: String, errorHandler: (VerificationCodeRequestResult, RegistrationRepository.Mode) -> Unit) {
    var smsListenerReady = false
    Log.d(TAG, "Captcha token submitted.")
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
    Log.d(TAG, "SMS code request submitted.")

    handleSessionStateResult(context, codeRequestResponse, transportMode, errorHandler)
  }

  private suspend fun getOrCreateValidSession(context: Context): RegistrationSessionMetadataResponse? {
    val e164 = getCurrentE164() ?: throw IllegalStateException("E164 required to create session!")
    Log.d(TAG, "Validating/creating a registration session.")
    val mccMncProducer = MccMncProducer(context)

    val existingSessionId = store.value.sessionId
    val sessionResult: RegistrationSessionResult = RegistrationRepository.createOrValidateSession(context, existingSessionId, e164, password, mccMncProducer.mcc, mccMncProducer.mnc)
    when (sessionResult) {
      is RegistrationSessionCheckResult.Success -> {
        val metadata = sessionResult.getMetadata()
        val newSessionId = metadata.body.id
        if (newSessionId.isNotNullOrBlank() && newSessionId != existingSessionId) {
          store.update {
            it.copy(
              sessionId = newSessionId
            )
          }
        }
        Log.d(TAG, "Registration session validated.")
        return metadata
      }

      is RegistrationSessionCreationResult.Success -> {
        val metadata = sessionResult.getMetadata()
        val newSessionId = metadata.body.id
        if (newSessionId.isNotNullOrBlank() && newSessionId != existingSessionId) {
          store.update {
            it.copy(
              sessionId = newSessionId
            )
          }
        }
        Log.d(TAG, "Registration session created.")
        return metadata
      }

      is RegistrationSessionCheckResult.SessionNotFound -> {
        Log.w(TAG, "This should be impossible to reach at this stage; it should have been handled in RegistrationRepository.", sessionResult.getCause())
        handleGenericError(sessionResult.getCause())
      }

      is RegistrationSessionCheckResult.UnknownError -> {
        Log.i(TAG, "Unknown error occurred while checking registration session.", sessionResult.getCause())
        handleGenericError(sessionResult.getCause())
      }

      is RegistrationSessionCreationResult.MalformedRequest -> {
        Log.i(TAG, "Malformed request error occurred while creating registration session.", sessionResult.getCause())
        handleGenericError(sessionResult.getCause())
      }

      is RegistrationSessionCreationResult.RateLimited -> {
        Log.i(TAG, "Rate limit occurred while creating registration session.", sessionResult.getCause())
        handleGenericError(sessionResult.getCause())
      }

      is RegistrationSessionCreationResult.ServerUnableToParse -> {
        Log.i(TAG, "Server unable to parse request for creating registration session.", sessionResult.getCause())
        handleGenericError(sessionResult.getCause())
      }

      is RegistrationSessionCreationResult.UnknownError -> {
        Log.i(TAG, "Unknown error occurred while checking registration session.", sessionResult.getCause())
        handleGenericError(sessionResult.getCause())
      }
    }
    return null
  }

  fun submitCaptchaToken(context: Context, errorHandler: (VerificationCodeRequestResult, RegistrationRepository.Mode) -> Unit) {
    val e164 = getCurrentE164() ?: throw IllegalStateException("Can't submit captcha token if no phone number is set!")
    val captchaToken = store.value.captchaToken ?: throw IllegalStateException("Can't submit captcha token if no captcha token is set!")

    viewModelScope.launch {
      val session = getOrCreateValidSession(context) ?: return@launch
      Log.d(TAG, "Submitting captcha token…")
      val captchaSubmissionResult = RegistrationRepository.submitCaptchaToken(context, e164, password, session.body.id, captchaToken)
      Log.d(TAG, "Captcha token submitted.")
      handleSessionStateResult(context, captchaSubmissionResult, RegistrationRepository.Mode.NONE, errorHandler)
    }
  }

  /**
   * @return whether the request was successful and execution should continue
   */
  private suspend fun handleSessionStateResult(context: Context, sessionResult: VerificationCodeRequestResult, requestedTransport: RegistrationRepository.Mode, errorHandler: (VerificationCodeRequestResult, RegistrationRepository.Mode) -> Unit): Boolean {
    when (sessionResult) {
      is UnknownError -> {
        handleGenericError(sessionResult.getCause())
        return false
      }

      is Success -> {
        Log.d(TAG, "New registration session status received.")
        updateFcmToken(context)
        store.update {
          it.copy(
            sessionId = sessionResult.sessionId,
            nextSms = sessionResult.nextSmsTimestamp,
            nextCall = sessionResult.nextCallTimestamp,
            registrationCheckpoint = RegistrationCheckpoint.VERIFICATION_CODE_REQUESTED,
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

      is RegistrationLocked -> Log.i(TAG, "Received RegistrationLocked.", sessionResult.getCause())
    }
    setInProgress(false)
    errorHandler(sessionResult, requestedTransport)
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
          verifyReRegisterInternal(context, pin, SignalStore.svr().getOrCreateMasterKey())
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
    val authCredentials = store.value.svrAuthCredentials
    if (authCredentials != null) {
      Log.d(TAG, "Found SVR auth credentials, fetching recovery password from SVR.")
      viewModelScope.launch(context = coroutineExceptionHandler) {
        try {
          val masterKey = RegistrationRepository.fetchMasterKeyFromSvrRemote(pin, authCredentials)
          setRecoveryPassword(masterKey.deriveRegistrationRecoveryPassword())
          updateSvrTriesRemaining(10)
          verifyReRegisterInternal(context, pin, masterKey)
        } catch (rejectedPin: SvrWrongPinException) {
          Log.w(TAG, "Submitted PIN was rejected by SVR.", rejectedPin)
          updateSvrTriesRemaining(rejectedPin.triesRemaining)
          wrongPinHandler()
        } catch (noData: SvrNoDataException) {
          Log.w(TAG, "SVR has no data for these credentials. Aborting skip SMS flow.", noData)
          setUserSkippedReRegisterFlow(true)
        }
        setInProgress(false)
      }
      return
    }

    Log.w(TAG, "Could not get credentials to skip SMS registration, aborting!")
    // TODO [regv2]: Investigate why in v1, this case throws a [IncorrectRegistrationRecoveryPasswordException], which seems weird.
    store.update {
      it.copy(canSkipSms = false, inProgress = false)
    }
  }

  private suspend fun verifyReRegisterInternal(context: Context, pin: String, masterKey: MasterKey) {
    updateFcmToken(context)

    val registrationData = getRegistrationData("")

    val resultAndRegLockStatus = registerAccountInternal(context, null, registrationData, pin, masterKey)
    val result = resultAndRegLockStatus.first
    val reglockEnabled = resultAndRegLockStatus.second

    if (result !is NetworkResult.Success) {
      Log.w(TAG, "Error during registration!", result.getCause())
      return
    }

    onSuccessfulRegistration(context, registrationData, result.result, reglockEnabled)
  }

  private suspend fun registerAccountInternal(context: Context, sessionId: String?, registrationData: RegistrationData, pin: String?, masterKey: MasterKey): Pair<NetworkResult<RegistrationRepository.AccountRegistrationResult>, Boolean> {
    val registrationResult = RegistrationRepository.registerAccount(context = context, sessionId = sessionId, registrationData = registrationData, pin = pin) { masterKey }

    // TODO: check for wrong recovery password

    // Check if reg lock is enabled
    if (registrationResult !is NetworkResult.StatusCodeError || registrationResult.exception !is LockedException) {
      return Pair(registrationResult, false)
    }

    Log.i(TAG, "Received a registration lock response when trying to register an account. Retrying with master key.")
    val lockedException = registrationResult.exception as LockedException
    store.update {
      it.copy(svrAuthCredentials = lockedException.svr2Credentials)
    }

    return Pair(RegistrationRepository.registerAccount(context = context, sessionId = sessionId, registrationData = registrationData, pin = pin) { masterKey }, true)
  }

  fun verifyCodeWithoutRegistrationLock(context: Context, code: String) {
    store.update {
      it.copy(inProgress = true, registrationCheckpoint = RegistrationCheckpoint.VERIFICATION_CODE_ENTERED)
    }

    val sessionId = store.value.sessionId
    if (sessionId == null) {
      Log.w(TAG, "Session ID was null. TODO: handle this better in the UI.")
      return
    }
    val e164: String = getCurrentE164() ?: throw IllegalStateException()

    viewModelScope.launch(context = coroutineExceptionHandler) {
      val registrationData = getRegistrationData(code)
      val verificationResponse = RegistrationRepository.submitVerificationCode(context, e164, password, sessionId, registrationData).successOrThrow()

      if (!verificationResponse.body.verified) {
        Log.w(TAG, "Could not verify code!")
        // TODO [regv2]: error handling
        return@launch
      }

      setRegistrationCheckpoint(RegistrationCheckpoint.VERIFICATION_CODE_VALIDATED)

      val registrationResponse = RegistrationRepository.registerAccount(context, sessionId, registrationData).successOrThrow()
      // TODO [regv2]: error handling
      onSuccessfulRegistration(context, registrationData, registrationResponse, false)
    }
  }

  private suspend fun onSuccessfulRegistration(context: Context, registrationData: RegistrationData, remoteResult: RegistrationRepository.AccountRegistrationResult, reglockEnabled: Boolean) {
    RegistrationRepository.registerAccountLocally(context, registrationData, remoteResult, reglockEnabled)

    refreshFeatureFlags()

    store.update {
      it.copy(
        registrationCheckpoint = RegistrationCheckpoint.SERVICE_REGISTRATION_COMPLETED,
        inProgress = false
      )
    }
  }

  fun hasPin(): Boolean {
    return RegistrationRepository.hasPin() || store.value.isReRegister
  }

  fun completeRegistration() {
    ApplicationDependencies.getJobManager().startChain(ProfileUploadJob()).then(listOf(MultiDeviceProfileKeyUpdateJob(), MultiDeviceProfileContentUpdateJob())).enqueue()
    RegistrationUtil.maybeMarkRegistrationComplete()
  }

  fun clearNetworkError() {
    store.update {
      it.copy(networkError = null)
    }
  }

  private fun matchesSavedE164(e164: String?): Boolean {
    return if (e164 == null) {
      false
    } else {
      e164 == SignalStore.account().e164
    }
  }

  private fun hasRecoveryPassword(): Boolean {
    return store.value.recoveryPassword != null
  }

  private fun getCurrentE164(): String? {
    return store.value.phoneNumber?.toE164()
  }

  private suspend fun getRegistrationData(code: String): RegistrationData {
    val currentState = store.value
    val e164: String = currentState.phoneNumber?.toE164() ?: throw IllegalStateException()
    val recoveryPassword = if (currentState.sessionId == null) SignalStore.svr().getRecoveryPassword() else null
    return RegistrationData(code, e164, password, RegistrationRepository.getRegistrationId(), RegistrationRepository.getProfileKey(e164), currentState.fcmToken, RegistrationRepository.getPniRegistrationId(), recoveryPassword)
  }

  /**
   * This is a generic error UI handler that re-enables the UI so that the user can recover from errors.
   * Do not forget to log any errors when calling this method!
   */
  private fun onErrorOccurred() {
    setInProgress(false)
  }

  companion object {
    private val TAG = Log.tag(RegistrationV2ViewModel::class.java)

    private suspend fun refreshFeatureFlags() = withContext(Dispatchers.IO) {
      val startTime = System.currentTimeMillis()
      try {
        FeatureFlags.refreshSync()
        Log.i(TAG, "Took " + (System.currentTimeMillis() - startTime) + " ms to get feature flags.")
      } catch (e: IOException) {
        Log.w(TAG, "Failed to refresh flags after " + (System.currentTimeMillis() - startTime) + " ms.", e)
      }
    }
  }
}
