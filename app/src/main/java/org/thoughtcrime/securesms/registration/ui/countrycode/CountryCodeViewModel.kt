/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.countrycode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * View model to support [CountryCodeFragment] and track the countries
 */
class CountryCodeViewModel : ViewModel() {

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
            country.name.contains(filterBy, ignoreCase = true) ||
              country.countryCode.toString().contains(filterBy.removePrefix("+")) ||
              (filterBy.equals("usa", ignoreCase = true) && country.name.equals("United States", ignoreCase = true))
          }
        )
      }
    }
  }

  fun loadCountries(initialCountry: Country? = null) {
    viewModelScope.launch(Dispatchers.IO) {
      val countryList = CountryUtils.getCountries()
      val commonCountryList = CountryUtils.getCommonCountries()
      val startingIndex = if (initialCountry == null || commonCountryList.contains(initialCountry)) {
        0
      } else {
        countryList.indexOf(initialCountry) + commonCountryList.size
      }

      internalState.update {
        it.copy(
          countryList = countryList,
          commonCountryList = commonCountryList,
          startingIndex = startingIndex
        )
      }
    }
  }
}
