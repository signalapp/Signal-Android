/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.phonenumber

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.ui.countrycode.Country
import org.thoughtcrime.securesms.registration.ui.countrycode.CountryUtils
import org.thoughtcrime.securesms.registration.util.CountryPrefix
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter

/**
 * ViewModel for the phone number entry screen.
 */
class EnterPhoneNumberViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(EnterPhoneNumberViewModel::class.java)
  }

  val supportedCountryPrefixes: List<CountryPrefix> = PhoneNumberUtil.getInstance().supportedCallingCodes
    .map { CountryPrefix(it, PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(it)) }
    .sortedBy { it.digits }

  private val store = MutableStateFlow(
    EnterPhoneNumberState(
      countryPrefixIndex = 0,
      phoneNumberRegionCode = supportedCountryPrefixes[0].regionCode
    )
  )
  val uiState = store.asLiveData()

  val phoneNumber: PhoneNumber?
    get() = try {
      parsePhoneNumber(store.value)
    } catch (ex: NumberParseException) {
      Log.w(TAG, "Could not parse phone number in current state.", ex)
      null
    }

  var mode: RegistrationRepository.E164VerificationMode
    get() = store.value.mode
    set(value) = store.update {
      it.copy(mode = value)
    }

  fun countryPrefix(): CountryPrefix {
    return supportedCountryPrefixes[store.value.countryPrefixIndex]
  }

  fun setPhoneNumber(phoneNumber: String?) {
    store.update { it.copy(phoneNumber = phoneNumber ?: "") }
  }

  fun setCountry(digits: Int) {
    val matchingIndex = countryCodeToAdapterIndex(digits)
    if (matchingIndex == -1) {
      Log.d(TAG, "Invalid country code specified $digits")
      return
    }

    store.update {
      it.copy(
        countryPrefixIndex = matchingIndex,
        phoneNumberRegionCode = supportedCountryPrefixes[matchingIndex].regionCode,
        country = Country(
          name = PhoneNumberFormatter.getRegionDisplayName(supportedCountryPrefixes[matchingIndex].regionCode).orElse(""),
          emoji = CountryUtils.countryToEmoji(supportedCountryPrefixes[matchingIndex].regionCode),
          countryCode = digits.toString()
        )
      )
    }
  }

  fun parsePhoneNumber(state: EnterPhoneNumberState): PhoneNumber {
    return PhoneNumberUtil.getInstance().parse(state.phoneNumber, supportedCountryPrefixes[state.countryPrefixIndex].regionCode)
  }

  fun isEnteredNumberPossible(state: EnterPhoneNumberState): Boolean {
    return try {
      PhoneNumberUtil.getInstance().isPossibleNumber(parsePhoneNumber(state))
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
          phoneNumberRegionCode = PhoneNumberUtil.getInstance().getRegionCodeForNumber(value) ?: it.phoneNumberRegionCode,
          phoneNumber = value.nationalNumber.toString()
        )
      }
    }
  }

  private fun countryCodeToAdapterIndex(countryCode: Int): Int {
    return supportedCountryPrefixes.indexOfFirst { prefix -> prefix.digits == countryCode }
  }

  fun clearError() {
    setError(EnterPhoneNumberState.Error.NONE)
  }

  fun setError(error: EnterPhoneNumberState.Error) {
    store.update {
      it.copy(error = error)
    }
  }
}
