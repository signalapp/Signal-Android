/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.countrycode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter
import java.util.Locale

/**
 * View model to support [CountryCodeFragment] and track the countries
 */
class CountryCodeViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(CountryCodeViewModel::class.java)
  }

  private val internalState = MutableStateFlow(CountryCodeState())
  val state = internalState.asStateFlow()

  fun filterCountries(filterBy: String) {
    if (filterBy.isEmpty()) {
      internalState.update {
        it.copy(
          query = filterBy,
          filteredList = emptyList()
        )
      }
    } else {
      internalState.update {
        it.copy(
          query = filterBy,
          filteredList = state.value.countryList.filter { country: Country ->
            country.name.contains(filterBy, ignoreCase = true) || country.countryCode.contains(filterBy)
          }
        )
      }
    }
  }

  fun loadCountries() {
    loadCommonCountryList()
    viewModelScope.launch(Dispatchers.IO) {
      val regions = PhoneNumberUtil.getInstance().supportedRegions
      val countries = mutableListOf<Country>()

      for (region in regions) {
        val c = Country(
          name = PhoneNumberFormatter.getRegionDisplayName(region).orElse(""),
          emoji = CountryUtils.countryToEmoji(region),
          countryCode = "+" + PhoneNumberUtil.getInstance().getCountryCodeForRegion(region)
        )
        countries.add(c)
      }

      val sortedCountries = countries.sortedWith { lhs, rhs ->
        lhs.name.lowercase(Locale.getDefault()).compareTo(rhs.name.lowercase(Locale.getDefault()))
      }

      internalState.update {
        it.copy(
          countryList = sortedCountries
        )
      }
    }
  }

  private fun loadCommonCountryList() {
    viewModelScope.launch(Dispatchers.IO) {
      val countries = mutableListOf<Country>()
      for (region in CountryUtils.COMMON_COUNTRIES) {
        val c = Country(
          name = PhoneNumberFormatter.getRegionDisplayName(region).orElse(""),
          emoji = CountryUtils.countryToEmoji(region),
          countryCode = "+" + PhoneNumberUtil.getInstance().getCountryCodeForRegion(region)
        )
        countries.add(c)
      }
      internalState.update {
        it.copy(
          commonCountryList = countries
        )
      }
    }
  }
}
