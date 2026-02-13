/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.countrycode

/**
 * State managed by [CountryCodePickerViewModel]. Includes country list and allows for searching
 */
data class CountryCodeState(
  val query: String = "",
  val countryList: List<Country> = emptyList(),
  val commonCountryList: List<Country> = emptyList(),
  val filteredList: List<Country> = emptyList(),
  val startingIndex: Int = 0
)
