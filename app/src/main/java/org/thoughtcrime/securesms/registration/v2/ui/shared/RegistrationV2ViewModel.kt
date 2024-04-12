/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.ui.shared

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileContentUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileKeyUpdateJob
import org.thoughtcrime.securesms.jobs.ProfileUploadJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.RegistrationData
import org.thoughtcrime.securesms.registration.RegistrationUtil
import org.thoughtcrime.securesms.registration.v2.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.v2.ui.toE164
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.dualsim.MccMncProducer
import java.io.IOException

/**
 * ViewModel shared across all of registration.
 */
class RegistrationV2ViewModel : ViewModel() {

  private val store = MutableStateFlow(RegistrationV2State())

  private val password = Util.getSecret(18) // TODO [regv2]: persist this

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

  fun fetchFcmToken(context: Context) {
    viewModelScope.launch {
      val fcmToken = RegistrationRepository.getFcmToken(context)
      store.update {
        it.copy(
          registrationCheckpoint = RegistrationCheckpoint.PUSH_NETWORK_AUDITED,
          isFcmSupported = true,
          fcmToken = fcmToken
        )
      }
    }
  }

  fun onUserConfirmedPhoneNumber(context: Context) {
    setRegistrationCheckpoint(RegistrationCheckpoint.PHONE_NUMBER_CONFIRMED)
    // TODO [regv2]: check if can skip sms flow
    val state = store.value
    if (state.phoneNumber == null) {
      Log.w(TAG, "Phone number was null after confirmation.")
      onErrorOccurred()
      return
    }
    if (state.canSkipSms) {
      Log.w(TAG, "Not yet implemented!", NotImplementedError()) // TODO [regv2]
    } else {
      // TODO [regv2]: initialize Play Services sms retriever
      val mccMncProducer = MccMncProducer(context)
      val e164 = state.phoneNumber.toE164()
      viewModelScope.launch {
        val codeRequestResponse = RegistrationRepository.requestSmsCode(context, e164, password, mccMncProducer.mcc, mccMncProducer.mnc).successOrThrow()
        store.update {
          it.copy(
            sessionId = codeRequestResponse.body.id,
            nextSms = RegistrationRepository.deriveTimestamp(codeRequestResponse.headers, codeRequestResponse.body.nextSms),
            nextCall = RegistrationRepository.deriveTimestamp(codeRequestResponse.headers, codeRequestResponse.body.nextCall),
            registrationCheckpoint = RegistrationCheckpoint.VERIFICATION_CODE_REQUESTED
          )
        }
      }
    }
  }

  fun verifyCodeWithoutRegistrationLock(context: Context, code: String) {
    store.update {
      it.copy(
        inProgress = true,
        registrationCheckpoint = RegistrationCheckpoint.VERIFICATION_CODE_ENTERED
      )
    }

    val sessionId = store.value.sessionId
    if (sessionId == null) {
      Log.w(TAG, "Session ID was null. TODO: handle this better in the UI.")
      return
    }
    val e164: String = getCurrentE164() ?: throw IllegalStateException()

    viewModelScope.launch {
      val registrationData = getRegistrationData(code)
      val verificationResponse = RegistrationRepository.submitVerificationCode(context, e164, password, sessionId, registrationData).successOrThrow()

      if (!verificationResponse.body.verified) {
        Log.w(TAG, "Could not verify code!")
        // TODO [regv2]: error handling
        return@launch
      }

      setRegistrationCheckpoint(RegistrationCheckpoint.VERIFICATION_CODE_VALIDATED)

      val registrationResponse = RegistrationRepository.registerAccount(context, e164, password, sessionId, registrationData).successOrThrow()

      localRegisterAccount(context, registrationData, registrationResponse, false)

      refreshFeatureFlags()

      store.update {
        it.copy(
          registrationCheckpoint = RegistrationCheckpoint.SERVICE_REGISTRATION_COMPLETED
        )
      }
    }
  }

  fun hasPin(): Boolean {
    return RegistrationRepository.hasPin() || store.value.isReRegister
  }

  fun completeRegistration() {
    ApplicationDependencies.getJobManager()
      .startChain(ProfileUploadJob())
      .then(listOf(MultiDeviceProfileKeyUpdateJob(), MultiDeviceProfileContentUpdateJob()))
      .enqueue()
    RegistrationUtil.maybeMarkRegistrationComplete()
  }

  private fun getCurrentE164(): String? {
    return store.value.phoneNumber?.toE164()
  }

  private suspend fun localRegisterAccount(
    context: Context,
    registrationData: RegistrationData,
    remoteResult: RegistrationRepository.AccountRegistrationResult,
    reglockEnabled: Boolean
  ) {
    RegistrationRepository.registerAccountLocally(context, registrationData, remoteResult, reglockEnabled)
  }

  private suspend fun getRegistrationData(code: String): RegistrationData {
    val e164: String = getCurrentE164() ?: throw IllegalStateException()
    return RegistrationData(
      code,
      e164,
      password,
      RegistrationRepository.getRegistrationId(),
      RegistrationRepository.getProfileKey(e164),
      store.value.fcmToken,
      RegistrationRepository.getPniRegistrationId(),
      null // TODO [regv2]: recovery password
    )
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
