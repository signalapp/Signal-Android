/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.countrycode

sealed interface CountryCodePickerScreenEvents {
  data class Search(val query: String) : CountryCodePickerScreenEvents
  data class CountrySelected(val country: Country) : CountryCodePickerScreenEvents
  data object Dismissed : CountryCodePickerScreenEvents
}
