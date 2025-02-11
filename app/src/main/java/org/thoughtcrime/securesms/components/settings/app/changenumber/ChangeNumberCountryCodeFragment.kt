/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.ui.countrycode.Country
import org.thoughtcrime.securesms.registration.ui.countrycode.CountryCodeSelectScreen
import org.thoughtcrime.securesms.registration.ui.countrycode.CountryCodeViewModel

/**
 * Country code picker specific to change number flow.
 */
class ChangeNumberCountryCodeFragment : ComposeFragment() {

  companion object {
    const val RESULT_KEY = "result_key"
    const val REQUEST_KEY_COUNTRY = "request_key_country"
    const val REQUEST_COUNTRY = "country"
    const val RESULT_COUNTRY = "country"
  }

  private val viewModel: CountryCodeViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val resultKey = arguments?.getString(RESULT_KEY) ?: REQUEST_KEY_COUNTRY

    CountryCodeSelectScreen(
      state = state,
      title = stringResource(R.string.CountryCodeFragment__your_country),
      onSearch = { search -> viewModel.filterCountries(search) },
      onDismissed = { findNavController().popBackStack() },
      onClick = { country ->
        setFragmentResult(
          resultKey,
          bundleOf(
            RESULT_COUNTRY to country
          )
        )
        findNavController().popBackStack()
      }
    )
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val initialCountry = arguments?.getParcelableCompat(REQUEST_COUNTRY, Country::class.java)
    viewModel.loadCountries(initialCountry)
  }
}
