/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.phonenumber

import android.telephony.PhoneNumberFormattingTextWatcher
import android.text.TextWatcher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.util.CountryPrefix

/**
 * ViewModel for the phone number entry screen.
 */
class EnterPhoneNumberViewModel : ViewModel() {

  private val TAG = Log.tag(EnterPhoneNumberViewModel::class.java)

  private val store = MutableStateFlow(EnterPhoneNumberState())
  val uiState = store.asLiveData()

  val formatter: TextWatcher?
    get() = store.value.phoneNumberFormatter

  val phoneNumber: PhoneNumber?
    get() = try {
      parsePhoneNumber(store.value)
    } catch (ex: NumberParseException) {
      Log.w(TAG, "Could not parse phone number in current state.", ex)
      null
    }

  val supportedCountryPrefixes: List<CountryPrefix> = PhoneNumberUtil.getInstance().supportedCallingCodes
    .map { CountryPrefix(it, PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(it)) }
    .sortedBy { it.digits }

  var mode: RegistrationRepository.Mode
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
      it.copy(countryPrefixIndex = matchingIndex)
    }

    viewModelScope.launch {
      withContext(Dispatchers.Default) {
        val regionCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(digits)
        val textWatcher = PhoneNumberFormattingTextWatcher(regionCode)

        store.update {
          Log.d(TAG, "Updating phone number formatter in state")
          it.copy(phoneNumberFormatter = textWatcher)
        }
      }
    }
  }

  fun parsePhoneNumber(state: EnterPhoneNumberState): PhoneNumber {
    return PhoneNumberUtil.getInstance().parse(state.phoneNumber, supportedCountryPrefixes[state.countryPrefixIndex].regionCode)
  }

  fun isEnteredNumberValid(state: EnterPhoneNumberState): Boolean {
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
    setError(EnterPhoneNumberState.Error.NONE)
  }

  fun setError(error: EnterPhoneNumberState.Error) {
    store.update {
      it.copy(error = error)
    }
  }
}
