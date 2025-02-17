/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.findby

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.ui.countrycode.Country
import org.thoughtcrime.securesms.registration.ui.countrycode.CountryUtils

/**
 * State for driving find by number/username screen.
 */
data class FindByState(
  val mode: FindByMode,
  val userEntry: String = "",
  val supportedCountries: List<Country> = CountryUtils.getCountries(),
  val filteredCountries: List<Country> = emptyList(),
  val selectedCountry: Country = supportedCountries.first(),
  val isLookupInProgress: Boolean = false,
  val query: String = ""
) {
  companion object {
    fun startingState(self: Recipient, mode: FindByMode): FindByState {
      val countryCode: Int = try {
        PhoneNumberUtil.getInstance()
          .parse(self.e164.orNull(), null)
          .countryCode
      } catch (e: NumberParseException) {
        -1
      }

      val state = FindByState(mode = mode)
      return state.copy(
        selectedCountry = state.supportedCountries.firstOrNull { it.countryCode == countryCode } ?: state.supportedCountries.first()
      )
    }
  }
}
