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
import java.util.Locale
import org.signal.registration.screens.countrycode.CountryUtils as RegistrationCountryUtils

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
    private const val DEFAULT_REGION_CODE = "US"

    fun startingState(self: Recipient, mode: FindByMode): FindByState {
      val regionCode: String = selfRegionCode(self)
        ?: RegistrationCountryUtils.localeToRegionCode(Locale.getDefault())
        ?: DEFAULT_REGION_CODE

      val state = FindByState(mode = mode)
      return state.copy(
        selectedCountry = state.supportedCountries.firstOrNull { it.regionCode == regionCode } ?: state.supportedCountries.first()
      )
    }

    private fun selfRegionCode(self: Recipient): String? {
      return try {
        PhoneNumberUtil.getInstance()
          .getRegionCodeForNumber(PhoneNumberUtil.getInstance().parse(self.e164.orNull(), null))
      } catch (e: NumberParseException) {
        null
      }
    }
  }
}
