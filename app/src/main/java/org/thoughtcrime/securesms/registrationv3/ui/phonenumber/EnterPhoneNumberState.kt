/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.phonenumber

import org.thoughtcrime.securesms.registrationv3.data.RegistrationRepository

/**
 * State holder for the phone number entry screen, including phone number and Play Services errors.
 */
data class EnterPhoneNumberState(
  val countryPrefixIndex: Int,
  val phoneNumber: String = "",
  val phoneNumberRegionCode: String,
  val mode: RegistrationRepository.E164VerificationMode = RegistrationRepository.E164VerificationMode.SMS_WITHOUT_LISTENER,
  val error: Error = Error.NONE
) {
  enum class Error {
    NONE, INVALID_PHONE_NUMBER, PLAY_SERVICES_MISSING, PLAY_SERVICES_NEEDS_UPDATE, PLAY_SERVICES_TRANSIENT
  }
}
