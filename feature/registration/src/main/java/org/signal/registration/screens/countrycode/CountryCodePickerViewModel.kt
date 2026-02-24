/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.countrycode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.screens.util.navigateBack

/**
 * View model for the country code picker screen.
 * Handles search filtering and country selection, emitting results back via [ResultEventBus].
 */
class CountryCodePickerViewModel(
  private val repository: CountryCodePickerRepository,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val resultBus: ResultEventBus,
  private val resultKey: String,
  initialCountry: Country? = null
) : ViewModel() {

  private val _state = MutableStateFlow(CountryCodeState())
  val state: StateFlow<CountryCodeState> = _state.asStateFlow()

  init {
    loadCountries(initialCountry)
  }

  fun onEvent(event: CountryCodePickerScreenEvents) {
    when (event) {
      is CountryCodePickerScreenEvents.Search -> applySearchEvent(event.query)
      is CountryCodePickerScreenEvents.CountrySelected -> {
        resultBus.sendResult(resultKey, event.country)
        parentEventEmitter.navigateBack()
      }
      is CountryCodePickerScreenEvents.Dismissed -> {
        parentEventEmitter.navigateBack()
      }
    }
  }

  private fun applySearchEvent(filterBy: String) {
    if (filterBy.isEmpty()) {
      _state.update {
        it.copy(
          query = filterBy,
          filteredList = emptyList()
        )
      }
    } else {
      _state.update {
        it.copy(
          query = filterBy,
          filteredList = _state.value.countryList.filter { country: Country ->
            country.name.contains(filterBy, ignoreCase = true) ||
              country.countryCode.toString().contains(filterBy.removePrefix("+")) ||
              (filterBy.equals("usa", ignoreCase = true) && country.name.equals("United States", ignoreCase = true))
          }
        )
      }
    }
  }

  private fun loadCountries(initialCountry: Country? = null) {
    viewModelScope.launch {
      val countryList = repository.getCountries()
      val commonCountryList = repository.getCommonCountries()
      val startingIndex = if (initialCountry == null || commonCountryList.contains(initialCountry)) {
        0
      } else {
        countryList.indexOf(initialCountry) + commonCountryList.size
      }

      _state.update {
        it.copy(
          countryList = countryList,
          commonCountryList = commonCountryList,
          startingIndex = startingIndex
        )
      }
    }
  }

  class Factory(
    private val repository: CountryCodePickerRepository,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val resultBus: ResultEventBus,
    private val resultKey: String,
    private val initialCountry: Country? = null
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return CountryCodePickerViewModel(repository, parentEventEmitter, resultBus, resultKey, initialCountry) as T
    }
  }
}
