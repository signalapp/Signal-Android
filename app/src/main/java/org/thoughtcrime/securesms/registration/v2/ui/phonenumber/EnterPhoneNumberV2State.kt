/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.ui.phonenumber

import android.text.TextWatcher

/**
 * State holder for the phone number entry screen, including phone number and Play Services errors.
 */
data class EnterPhoneNumberV2State(val countryPrefixIndex: Int, val phoneNumber: String, val phoneNumberFormatter: TextWatcher? = null, val error: Error = Error.NONE) {

  companion object {
    @JvmStatic
    val INIT = EnterPhoneNumberV2State(0, "")
  }

  enum class Error {
    NONE,
    INVALID_PHONE_NUMBER,
    PLAY_SERVICES_MISSING,
    PLAY_SERVICES_NEEDS_UPDATE,
    PLAY_SERVICES_TRANSIENT
  }
}
