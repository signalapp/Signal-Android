package org.thoughtcrime.securesms.delete

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
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.ui.countrycode.CountryCodeSelectScreen
import org.thoughtcrime.securesms.registration.ui.countrycode.CountryCodeViewModel

/**
 * Country code picker specific to deleting an account.
 */
class DeleteAccountCountryCodeFragment : ComposeFragment() {

  companion object {
    const val RESULT_KEY = "result_key"
    const val RESULT_COUNTRY = "result_country"
  }

  private val viewModel: CountryCodeViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CountryCodeSelectScreen(
      state = state,
      title = stringResource(R.string.CountryCodeFragment__your_country),
      onSearch = { search -> viewModel.filterCountries(search) },
      onDismissed = { findNavController().popBackStack() },
      onClick = { country ->
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_COUNTRY to country))
        findNavController().popBackStack()
      }
    )
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    viewModel.loadCountries()
  }
}
