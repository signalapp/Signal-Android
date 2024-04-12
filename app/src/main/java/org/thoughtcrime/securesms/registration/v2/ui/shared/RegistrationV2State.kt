/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.ui.shared

import com.google.i18n.phonenumbers.Phonenumber

/**
 * State holder shared across all of registration.
 */
data class RegistrationV2State(
  val sessionId: String? = null,
  val phoneNumber: Phonenumber.PhoneNumber? = null,
  val inProgress: Boolean = false,
  val isReRegister: Boolean = false,
  val canSkipSms: Boolean = false,
  val isFcmSupported: Boolean = false,
  val fcmToken: String? = null,
  val nextSms: Long = 0L,
  val nextCall: Long = 0L,
  val registrationCheckpoint: RegistrationCheckpoint = RegistrationCheckpoint.INITIALIZATION
)
