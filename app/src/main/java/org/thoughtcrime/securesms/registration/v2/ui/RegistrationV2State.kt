/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.ui

import com.google.i18n.phonenumbers.Phonenumber
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.internal.push.AuthCredentials

/**
 * State holder shared across all of registration.
 */
data class RegistrationV2State(
  val sessionId: String? = null,
  val phoneNumber: Phonenumber.PhoneNumber? = null,
  val inProgress: Boolean = false,
  val isReRegister: Boolean = false,
  val recoveryPassword: String? = SignalStore.svr().getRecoveryPassword(),
  val canSkipSms: Boolean = false,
  val svrAuthCredentials: AuthCredentials? = null,
  val svrTriesRemaining: Int = 10,
  val isRegistrationLockEnabled: Boolean = false,
  val userSkippedReregistration: Boolean = false,
  val isFcmSupported: Boolean = false,
  val fcmToken: String? = null,
  val nextSms: Long = 0L,
  val nextCall: Long = 0L,
  val registrationCheckpoint: RegistrationCheckpoint = RegistrationCheckpoint.INITIALIZATION,
  val networkError: Throwable? = null
)
