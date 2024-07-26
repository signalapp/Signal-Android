/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.RegistrationData
import org.thoughtcrime.securesms.registration.SmsRetrieverReceiver
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.data.network.Challenge
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionCreationResult
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.viewmodel.NumberViewState
import org.thoughtcrime.securesms.registration.viewmodel.SvrAuthCredentialSet
import org.thoughtcrime.securesms.util.dualsim.MccMncProducer
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * [ViewModel] for the change number flow.
 *
 * @see [RegistrationViewModel], from which this is derived.
 */
class ChangeNumberViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(ChangeNumberViewModel::class.java)

    val CHANGE_NUMBER_LOCK = ReentrantLock()
  }

  private val repository = ChangeNumberRepository()
  private val store = MutableStateFlow(ChangeNumberState())
  private val serialContext = SignalExecutors.SERIAL.asCoroutineDispatcher()
  private val smsRetrieverReceiver: SmsRetrieverReceiver = SmsRetrieverReceiver(AppDependencies.application)

  private val initialLocalNumber = SignalStore.account.e164
  private val password = SignalStore.account.servicePassword!!

  val uiState = store.asLiveData()
  val liveOldNumberState = store.map { it.oldPhoneNumber }.asLiveData()
  val liveNewNumberState = store.map { it.number }.asLiveData()
  val liveLockedTimeRemaining = store.map { it.lockedTimeRemaining }.asLiveData()
  val incorrectCodeAttempts = store.map { it.incorrectCodeAttempts }.asLiveData()

  init {
    try {
      val countryCode: Int = PhoneNumberUtil.getInstance()
        .parse(SignalStore.account.e164!!, null)
        .countryCode

      store.update {
        it.copy(
          number = it.number.toBuilder().countryCode(countryCode).build(),
          oldPhoneNumber = it.oldPhoneNumber.toBuilder().countryCode(countryCode).build()
        )
      }
    } catch (e: NumberParseException) {
      Log.i(TAG, "Unable to parse number for default country code")
    }

    smsRetrieverReceiver.registerReceiver()
  }

  override fun onCleared() {
    super.onCleared()
    smsRetrieverReceiver.unregisterReceiver()
  }

  // region Public Getters and Setters

  val number: NumberViewState
    get() = store.value.number

  val oldNumberState: NumberViewState
    get() = store.value.oldPhoneNumber

  val svrTriesRemaining: Int
    get() = store.value.svrTriesRemaining

  fun setOldNationalNumber(updatedNumber: String) {
    store.update {
      it.copy(oldPhoneNumber = oldNumberState.toBuilder().nationalNumber(updatedNumber).build())
    }
  }

  fun setOldCountry(countryCode: Int, country: String? = null) {
    store.update {
      it.copy(oldPhoneNumber = oldNumberState.toBuilder().selectedCountryDisplayName(country).countryCode(countryCode).build())
    }
  }

  fun setNewNationalNumber(updatedNumber: String) {
    store.update {
      it.copy(number = number.toBuilder().nationalNumber(updatedNumber).build())
    }
  }

  fun setNewCountry(countryCode: Int, country: String? = null) {
    store.update {
      it.copy(number = number.toBuilder().selectedCountryDisplayName(country).countryCode(countryCode).build())
    }
  }

  fun setCaptchaResponse(token: String) {
    Log.v(TAG, "setCaptchaResponse()")
    store.update {
      it.copy(captchaToken = token)
    }
  }

  fun setEnteredPin(pin: String) {
    store.update {
      it.copy(enteredPin = pin)
    }
  }

  fun incrementIncorrectCodeAttempts() {
    store.update {
      it.copy(incorrectCodeAttempts = it.incorrectCodeAttempts + 1)
    }
  }

  fun addPresentedChallenge(challenge: Challenge) {
    Log.v(TAG, "addPresentedChallenge()")
    store.update {
      it.copy(challengesPresented = it.challengesPresented.plus(challenge))
    }
  }

  fun removePresentedChallenge(challenge: Challenge) {
    Log.v(TAG, "addPresentedChallenge()")
    store.update {
      it.copy(challengesPresented = it.challengesPresented.minus(challenge))
    }
  }

  fun resetLocalSessionState() {
    Log.v(TAG, "resetLocalSessionState()")
    store.update {
      it.copy(inProgress = false, changeNumberOutcome = null, captchaToken = null, challengesRequested = emptyList(), allowedToRequestCode = false)
    }
  }

  fun canContinue(): ContinueStatus {
    return if (oldNumberState.e164Number == initialLocalNumber) {
      if (number.isValid) {
        ContinueStatus.CAN_CONTINUE
      } else {
        ContinueStatus.INVALID_NUMBER
      }
    } else {
      ContinueStatus.OLD_NUMBER_DOESNT_MATCH
    }
  }

  // endregion

  // region Public actions

  fun checkWhoAmI(onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
    Log.v(TAG, "checkWhoAmI()")
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val whoAmI = repository.whoAmI()

        if (whoAmI.number == SignalStore.account.e164) {
          return@launch bail { Log.i(TAG, "Local and remote numbers match, nothing needs to be done.") }
        }

        Log.i(TAG, "Local (${SignalStore.account.e164}) and remote (${whoAmI.number}) numbers do not match, updating local.")

        withLockOnSerialExecutor {
          repository.changeLocalNumber(whoAmI.number, ServiceId.PNI.parseOrThrow(whoAmI.pni))
        }

        withContext(Dispatchers.Main) {
          onSuccess()
        }
      } catch (ioException: IOException) {
        Log.w(TAG, "Encountered an exception when requesting whoAmI()", ioException)
        withContext(Dispatchers.Main) {
          onError(ioException)
        }
      }
    }
  }

  fun registerSmsListenerWithCompletionListener(context: Context, onComplete: (Boolean) -> Unit) {
    Log.v(TAG, "registerSmsListenerWithCompletionListener()")
    viewModelScope.launch {
      val listenerRegistered = RegistrationRepository.registerSmsListener(context)
      onComplete(listenerRegistered)
    }
  }

  fun verifyCodeWithoutRegistrationLock(context: Context, code: String, verificationErrorHandler: (VerificationCodeRequestResult) -> Unit, numberChangeErrorHandler: (ChangeNumberResult) -> Unit) {
    Log.v(TAG, "verifyCodeWithoutRegistrationLock()")
    store.update {
      it.copy(
        inProgress = true,
        enteredCode = code
      )
    }

    viewModelScope.launch {
      verifyCodeInternal(context = context, pin = null, verificationErrorHandler = verificationErrorHandler, numberChangeErrorHandler = numberChangeErrorHandler)
    }
  }

  fun verifyCodeAndRegisterAccountWithRegistrationLock(context: Context, pin: String, verificationErrorHandler: (VerificationCodeRequestResult) -> Unit, numberChangeErrorHandler: (ChangeNumberResult) -> Unit) {
    Log.v(TAG, "verifyCodeAndRegisterAccountWithRegistrationLock()")
    store.update { it.copy(inProgress = true) }

    viewModelScope.launch {
      verifyCodeInternal(context = context, pin = pin, verificationErrorHandler = verificationErrorHandler, numberChangeErrorHandler = numberChangeErrorHandler)
    }
  }

  private suspend fun verifyCodeInternal(context: Context, pin: String?, verificationErrorHandler: (VerificationCodeRequestResult) -> Unit, numberChangeErrorHandler: (ChangeNumberResult) -> Unit) {
    val sessionId = getOrCreateValidSession(context)?.body?.id ?: return bail { Log.i(TAG, "Bailing from code verification due to invalid session.") }
    val registrationData = getRegistrationData(context)

    val verificationResponse = RegistrationRepository.submitVerificationCode(context, sessionId, registrationData)

    if (verificationResponse !is VerificationCodeRequestResult.Success && verificationResponse !is VerificationCodeRequestResult.AlreadyVerified) {
      handleVerificationError(verificationResponse, verificationErrorHandler)
      return bail { Log.i(TAG, "Bailing from code verification due to non-successful response.") }
    }

    val result: ChangeNumberResult = if (pin == null) {
      repository.changeNumberWithoutRegistrationLock(sessionId = sessionId, newE164 = number.e164Number)
    } else {
      repository.changeNumberWithRegistrationLock(
        sessionId = sessionId,
        newE164 = number.e164Number,
        pin = pin,
        svrAuthCredentials = SvrAuthCredentialSet(
          svr2Credentials = store.value.svr2Credentials,
          svr3Credentials = store.value.svr3Credentials
        )
      )
    }

    if (result is ChangeNumberResult.Success) {
      handleSuccessfulChangedRemoteNumber(e164 = result.numberChangeResult.number, pni = ServiceId.PNI.parseOrThrow(result.numberChangeResult.pni), changeNumberOutcome = ChangeNumberOutcome.RecoveryPasswordWorked)
    } else {
      handleChangeNumberError(result, numberChangeErrorHandler)
    }
  }

  fun submitCaptchaToken(context: Context) {
    Log.v(TAG, "submitCaptchaToken()")
    val e164 = number.e164Number
    val captchaToken = store.value.captchaToken ?: throw IllegalStateException("Can't submit captcha token if no captcha token is set!")
    store.update {
      it.copy(
        captchaToken = null,
        inProgress = true,
        changeNumberOutcome = null
      )
    }

    viewModelScope.launch {
      Log.d(TAG, "Getting session in order to submit captcha token…")
      val session = getOrCreateValidSession(context) ?: return@launch bail { Log.i(TAG, "Bailing captcha token submission due to invalid session.") }
      if (!Challenge.parse(session.body.requestedInformation).contains(Challenge.CAPTCHA)) {
        Log.d(TAG, "Captcha submission no longer necessary, bailing.")
        store.update {
          it.copy(
            inProgress = false,
            changeNumberOutcome = null
          )
        }
        return@launch
      }
      Log.d(TAG, "Submitting captcha token…")
      val captchaSubmissionResult = RegistrationRepository.submitCaptchaToken(context, e164, password, session.body.id, captchaToken)
      Log.d(TAG, "Captcha token submitted.")
      store.update {
        it.copy(inProgress = false, changeNumberOutcome = ChangeNumberOutcome.ChangeNumberRequestOutcome(captchaSubmissionResult))
      }
    }
  }

  fun requestAndSubmitPushToken(context: Context) {
    Log.v(TAG, "validatePushToken()")

    addPresentedChallenge(Challenge.PUSH)

    val e164 = number.e164Number

    viewModelScope.launch {
      Log.d(TAG, "Getting session in order to perform push token verification…")
      val session = getOrCreateValidSession(context) ?: return@launch bail { Log.i(TAG, "Bailing from push token verification due to invalid session.") }

      if (!Challenge.parse(session.body.requestedInformation).contains(Challenge.PUSH)) {
        Log.d(TAG, "Push submission no longer necessary, bailing.")
        store.update {
          it.copy(
            inProgress = false,
            changeNumberOutcome = null
          )
        }
        return@launch
      }

      Log.d(TAG, "Requesting push challenge token…")
      val pushSubmissionResult = RegistrationRepository.requestAndVerifyPushToken(context, session.body.id, e164, password)
      Log.d(TAG, "Push challenge token submitted.")
      store.update {
        it.copy(inProgress = false, changeNumberOutcome = ChangeNumberOutcome.ChangeNumberRequestOutcome(pushSubmissionResult))
      }
    }
  }

  fun initiateChangeNumberSession(context: Context, mode: RegistrationRepository.Mode) {
    Log.v(TAG, "changeNumber()")
    store.update { it.copy(inProgress = true) }
    viewModelScope.launch {
      val encryptionDrained = repository.ensureDecryptionsDrained() ?: false

      if (!encryptionDrained) {
        return@launch bail { Log.i(TAG, "Failed to drain encryption.") }
      }

      val changed = changeNumberWithRecoveryPassword()

      if (changed) {
        Log.d(TAG, "Successfully changed number using recovery password, which cleaned up after itself.")
        return@launch
      }

      requestVerificationCode(context, mode)
    }
  }

  // endregion

  // region Private actions

  private fun updateLocalStateFromSession(response: RegistrationSessionMetadataResponse) {
    Log.v(TAG, "updateLocalStateFromSession()")
    store.update {
      it.copy(sessionId = response.body.id, challengesRequested = Challenge.parse(response.body.requestedInformation), allowedToRequestCode = response.body.allowedToRequestCode)
    }
  }

  private suspend fun getOrCreateValidSession(context: Context): RegistrationSessionMetadataResponse? {
    Log.v(TAG, "getOrCreateValidSession()")
    val e164 = number.e164Number
    val mccMncProducer = MccMncProducer(context)
    val existingSessionId = store.value.sessionId
    return RegistrationViewModel.getOrCreateValidSession(context = context, existingSessionId = existingSessionId, e164 = e164, password = password, mcc = mccMncProducer.mcc, mnc = mccMncProducer.mnc, successListener = { freshMetadata ->
      Log.v(TAG, "Valid session received, updating local state.")
      updateLocalStateFromSession(freshMetadata)
    }, errorHandler = { result ->
      val requestCode: VerificationCodeRequestResult = when (result) {
        is RegistrationSessionCreationResult.RateLimited -> VerificationCodeRequestResult.RateLimited(result.getCause(), result.timeRemaining)
        is RegistrationSessionCreationResult.MalformedRequest -> VerificationCodeRequestResult.MalformedRequest(result.getCause())
        else -> VerificationCodeRequestResult.UnknownError(result.getCause())
      }

      store.update {
        it.copy(changeNumberOutcome = ChangeNumberOutcome.ChangeNumberRequestOutcome(requestCode))
      }
    })
  }

  private suspend fun changeNumberWithRecoveryPassword(): Boolean {
    Log.v(TAG, "changeNumberWithRecoveryPassword()")
    SignalStore.svr.recoveryPassword?.let { recoveryPassword ->
      if (SignalStore.svr.hasPin()) {
        val result = repository.changeNumberWithRecoveryPassword(recoveryPassword = recoveryPassword, newE164 = number.e164Number)

        if (result is ChangeNumberResult.Success) {
          handleSuccessfulChangedRemoteNumber(e164 = result.numberChangeResult.number, pni = ServiceId.PNI.parseOrThrow(result.numberChangeResult.pni), changeNumberOutcome = ChangeNumberOutcome.RecoveryPasswordWorked)
          return true
        }

        Log.d(TAG, "Encountered error while trying to change number with recovery password.", result.getCause())
      }
    }
    return false
  }

  private suspend fun handleSuccessfulChangedRemoteNumber(e164: String, pni: ServiceId.PNI, changeNumberOutcome: ChangeNumberOutcome) {
    var result = changeNumberOutcome
    Log.v(TAG, "handleSuccessfulChangedRemoteNumber(${result.javaClass.simpleName}")
    try {
      withLockOnSerialExecutor {
        repository.changeLocalNumber(e164, pni)
      }
    } catch (ioException: IOException) {
      Log.w(TAG, "Failed to change local number!", ioException)
      result = ChangeNumberOutcome.ChangeNumberRequestOutcome(VerificationCodeRequestResult.UnknownError(ioException))
    }

    store.update {
      it.copy(inProgress = false, changeNumberOutcome = result)
    }
  }

  private fun handleVerificationError(result: VerificationCodeRequestResult, verificationErrorHandler: (VerificationCodeRequestResult) -> Unit) {
    Log.v(TAG, "handleVerificationError(${result.javaClass.simpleName}")
    when (result) {
      is VerificationCodeRequestResult.Success -> Unit
      is VerificationCodeRequestResult.RegistrationLocked ->
        store.update {
          it.copy(
            lockedTimeRemaining = result.timeRemaining,
            svr2Credentials = result.svr2Credentials,
            svr3Credentials = result.svr3Credentials
          )
        }
      else -> Log.i(TAG, "Received exception during verification.", result.getCause())
    }

    verificationErrorHandler(result)
  }

  private fun handleChangeNumberError(result: ChangeNumberResult, numberChangeErrorHandler: (ChangeNumberResult) -> Unit) {
    Log.v(TAG, "handleChangeNumberError(${result.javaClass.simpleName}")
    when (result) {
      is ChangeNumberResult.Success -> Unit
      is ChangeNumberResult.RegistrationLocked ->
        store.update {
          it.copy(
            svr2Credentials = result.svr2Credentials,
            svr3Credentials = result.svr3Credentials
          )
        }
      is ChangeNumberResult.SvrWrongPin -> {
        store.update {
          it.copy(
            svrTriesRemaining = result.triesRemaining
          )
        }
      }
      else -> Log.i(TAG, "Received exception during change number.", result.getCause())
    }

    numberChangeErrorHandler(result)
  }

  private suspend fun requestVerificationCode(context: Context, mode: RegistrationRepository.Mode) {
    Log.v(TAG, "requestVerificationCode()")
    val e164 = number.e164Number

    val validSession = getOrCreateValidSession(context)

    if (validSession == null) {
      Log.w(TAG, "Bailing on requesting verification code because could not create a session!")
      resetLocalSessionState()
      return
    }

    val result = if (!validSession.body.allowedToRequestCode) {
      val challenges = validSession.body.requestedInformation.joinToString()
      Log.i(TAG, "Not allowed to request code! Remaining challenges: $challenges")
      VerificationCodeRequestResult.ChallengeRequired(Challenge.parse(validSession.body.requestedInformation))
    } else {
      store.update {
        it.copy(changeNumberOutcome = null, challengesRequested = emptyList())
      }
      val response = RegistrationRepository.requestSmsCode(context = context, sessionId = validSession.body.id, e164 = e164, password = password, mode = mode)
      Log.d(TAG, "SMS code request submitted")
      response
    }

    val challengesRequested = if (result is VerificationCodeRequestResult.ChallengeRequired) {
      result.challenges
    } else {
      emptyList()
    }

    Log.d(TAG, "Received result: ${result.javaClass.canonicalName}\nwith challenges: ${challengesRequested.joinToString { it.key }}")

    store.update {
      it.copy(changeNumberOutcome = ChangeNumberOutcome.ChangeNumberRequestOutcome(result), challengesRequested = challengesRequested, inProgress = false)
    }
  }

  private suspend fun getRegistrationData(context: Context): RegistrationData {
    val currentState = store.value
    val code = currentState.enteredCode ?: throw IllegalStateException("Can't construct registration data without entered code!")
    val e164: String = number.e164Number ?: throw IllegalStateException("Can't construct registration data without E164!")
    val recoveryPassword = if (currentState.sessionId == null) SignalStore.svr.getRecoveryPassword() else null
    val fcmToken = RegistrationRepository.getFcmToken(context)
    return RegistrationData(code, e164, password, RegistrationRepository.getRegistrationId(), RegistrationRepository.getProfileKey(e164), fcmToken, RegistrationRepository.getPniRegistrationId(), recoveryPassword)
  }

  // endregion

  // region Utility Functions

  /**
   * Used for early returns in order to end the in-progress visual state, as well as print a log message explaining what happened.
   *
   * @param logMessage Logging code is wrapped in lambda so that our automated tools detect the various [Log] calls with their accompanying messages.
   */
  private fun bail(logMessage: () -> Unit) {
    logMessage()
    store.update {
      it.copy(inProgress = false)
    }
  }

  /**
   * Anything that runs through this will be run serially, with locks.
   */
  private suspend fun <T> withLockOnSerialExecutor(action: () -> T): T = withContext(serialContext) {
    Log.v(TAG, "withLock()")
    val result = CHANGE_NUMBER_LOCK.withLock {
      SignalStore.misc.lockChangeNumber()
      Log.v(TAG, "Change number lock acquired.")
      try {
        action()
      } finally {
        SignalStore.misc.unlockChangeNumber()
      }
    }
    Log.v(TAG, "Change number lock released.")
    return@withContext result
  }

  // endregion

  enum class ContinueStatus {
    CAN_CONTINUE, INVALID_NUMBER, OLD_NUMBER_DOESNT_MATCH
  }
}
