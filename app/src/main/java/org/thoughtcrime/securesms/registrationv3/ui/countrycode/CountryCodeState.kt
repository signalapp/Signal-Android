/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.countrycode

/**
 * State managed by [CountryCodeViewModel]. Includes country list and allows for searching
 */
data class CountryCodeState(
  val query: String = "",
  val countryList: List<Country> = emptyList(),
  val commonCountryList: List<Country> = emptyList(),
  val filteredList: List<Country> = emptyList()
)
