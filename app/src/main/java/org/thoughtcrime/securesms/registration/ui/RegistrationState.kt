/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.data.network.Challenge
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionResult
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult
import org.whispersystems.signalservice.api.svr.Svr3Credentials
import org.whispersystems.signalservice.internal.push.AuthCredentials

/**
 * State holder shared across all of registration.
 */
data class RegistrationState(
  val sessionId: String? = null,
  val enteredCode: String = "",
  val phoneNumber: Phonenumber.PhoneNumber? = fetchExistingE164FromValues(),
  val inProgress: Boolean = false,
  val isReRegister: Boolean = false,
  val recoveryPassword: String? = SignalStore.svr.getRecoveryPassword(),
  val canSkipSms: Boolean = false,
  val svr2AuthCredentials: AuthCredentials? = null,
  val svr3AuthCredentials: Svr3Credentials? = null,
  val svrTriesRemaining: Int = 10,
  val incorrectCodeAttempts: Int = 0,
  val isRegistrationLockEnabled: Boolean = false,
  val lockedTimeRemaining: Long = 0L,
  val userSkippedReregistration: Boolean = false,
  val isFcmSupported: Boolean = false,
  val isAllowedToRequestCode: Boolean = false,
  val fcmToken: String? = null,
  val challengesRequested: List<Challenge> = emptyList(),
  val challengesPresented: Set<Challenge> = emptySet(),
  val captchaToken: String? = null,
  val allowedToRequestCode: Boolean = false,
  val nextSmsTimestamp: Long = 0L,
  val nextCallTimestamp: Long = 0L,
  val nextVerificationAttempt: Long = 0L,
  val verified: Boolean = false,
  val smsListenerTimeout: Long = 0L,
  val registrationCheckpoint: RegistrationCheckpoint = RegistrationCheckpoint.INITIALIZATION,
  val networkError: Throwable? = null,
  val sessionCreationError: RegistrationSessionResult? = null,
  val sessionStateError: VerificationCodeRequestResult? = null,
  val registerAccountError: RegisterAccountResult? = null
) {
  val challengesRemaining: List<Challenge> = challengesRequested.filterNot { it in challengesPresented }

  companion object {
    private val TAG = Log.tag(RegistrationState::class)

    private fun fetchExistingE164FromValues(): Phonenumber.PhoneNumber? {
      val existingE164 = SignalStore.registration.sessionE164
      if (existingE164 != null) {
        try {
          return PhoneNumberUtil.getInstance().parse(existingE164, null)
        } catch (ex: NumberParseException) {
          Log.w(TAG, "Could not parse stored E164.", ex)
          return null
        }
      } else {
        return null
      }
    }
  }
}
