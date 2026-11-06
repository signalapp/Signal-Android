/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.delete

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.countrycode.Country
import org.thoughtcrime.securesms.registration.ui.countrycode.CountryCodeSelectScreen
import org.thoughtcrime.securesms.registration.ui.countrycode.CountryCodeViewModel

/**
 * The country picker for deleting an account. The host decides how the chosen country gets back to the delete screen.
 */
@Composable
fun DeleteAccountCountryCodeEntry(
  onCountrySelected: (Country) -> Unit,
  onDismissed: () -> Unit
) {
  val viewModel: CountryCodeViewModel = viewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()

  LaunchedEffect(viewModel) {
    viewModel.loadCountries()
  }

  CountryCodeSelectScreen(
    state = state,
    title = stringResource(R.string.CountryCodeFragment__your_country),
    onSearch = { search -> viewModel.filterCountries(search) },
    onDismissed = onDismissed,
    onClick = onCountrySelected
  )
}
