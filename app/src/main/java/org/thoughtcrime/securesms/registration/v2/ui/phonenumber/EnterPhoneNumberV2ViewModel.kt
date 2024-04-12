/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.ui.phonenumber

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.registration.util.CountryPrefix

/**
 * ViewModel for the phone number entry screen.
 */
class EnterPhoneNumberV2ViewModel : ViewModel() {

  private val TAG = Log.tag(EnterPhoneNumberV2ViewModel::class.java)

  private val store = MutableStateFlow(EnterPhoneNumberV2State.INIT)
  val uiState = store.asLiveData()

  val supportedCountryPrefixes: List<CountryPrefix> = PhoneNumberUtil.getInstance().supportedCallingCodes
    .map { CountryPrefix(it, PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(it)) }
    .sortedBy { it.digits.toString() }

  fun countryPrefix(): CountryPrefix {
    return supportedCountryPrefixes[store.value.countryPrefixIndex]
  }

  fun phoneNumber(): PhoneNumber? {
    return try {
      parsePhoneNumber(store.value)
    } catch (ex: NumberParseException) {
      Log.w(TAG, "Could not parse phone number in current state.", ex)
      null
    }
  }

  fun setPhoneNumber(phoneNumber: String?) {
    store.update { it.copy(phoneNumber = phoneNumber ?: "") }
  }

  fun setCountry(digits: Int) {
    val matchingIndex = countryCodeToAdapterIndex(digits)
    store.update {
      it.copy(countryPrefixIndex = matchingIndex)
    }
  }

  fun parsePhoneNumber(state: EnterPhoneNumberV2State): PhoneNumber {
    return PhoneNumberUtil.getInstance().parse(state.phoneNumber, supportedCountryPrefixes[state.countryPrefixIndex].regionCode)
  }

  fun isEnteredNumberValid(state: EnterPhoneNumberV2State): Boolean {
    return try {
      PhoneNumberUtil.getInstance().isValidNumber(parsePhoneNumber(state))
    } catch (ex: NumberParseException) {
      false
    }
  }

  fun restoreState(value: PhoneNumber) {
    val prefixIndex = countryCodeToAdapterIndex(value.countryCode)
    if (prefixIndex != -1) {
      store.update {
        it.copy(
          countryPrefixIndex = prefixIndex,
          phoneNumber = value.nationalNumber.toString()
        )
      }
    }
  }

  private fun countryCodeToAdapterIndex(countryCode: Int): Int {
    return supportedCountryPrefixes.indexOfFirst { prefix -> prefix.digits == countryCode }
  }

  fun clearError() {
    setError(EnterPhoneNumberV2State.Error.NONE)
  }

  fun setError(error: EnterPhoneNumberV2State.Error) {
    store.update {
      it.copy(error = error)
    }
  }
}
