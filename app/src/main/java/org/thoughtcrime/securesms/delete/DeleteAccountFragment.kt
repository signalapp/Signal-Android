/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.delete

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import org.signal.appsettings.deleteaccount.DeleteAccountAction
import org.signal.appsettings.deleteaccount.DeleteAccountEvent
import org.signal.appsettings.deleteaccount.DeleteAccountScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.countrycode.Country
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.signal.appsettings.R as AppSettingsR

/**
 * Lets a user delete their account. Carries out the [DeleteAccountAction]s that need an Activity or the nav graph.
 */
class DeleteAccountFragment : ComposeFragment() {

  private val viewModel: DeleteAccountViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    parentFragmentManager.setFragmentResultListener(DeleteAccountCountryCodeFragment.RESULT_KEY, this) { _, bundle ->
      val country: Country? = bundle.getParcelable(DeleteAccountCountryCodeFragment.RESULT_COUNTRY)
      if (country != null) {
        viewModel.onEvent(DeleteAccountEvent.CountrySelected(country.regionCode))
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    DeleteAccountScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: DeleteAccountAction) {
    when (action) {
      DeleteAccountAction.NavigateBack -> findNavController().popBackStack()
      DeleteAccountAction.NavigateToCountryPicker -> findNavController().safeNavigate(R.id.action_deleteAccountFragment_to_deleteAccountCountryFragment)
      DeleteAccountAction.ShowNoCountryCode -> snackbar(AppSettingsR.string.DeleteAccountFragment__no_country_code)
      DeleteAccountAction.ShowNoNationalNumber -> snackbar(AppSettingsR.string.DeleteAccountFragment__no_number)
      DeleteAccountAction.LaunchAppSettings -> {
        startActivity(
          Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireActivity().packageName, null)
          }
        )
      }
    }
  }

  private fun snackbar(@StringRes message: Int) {
    Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show()
  }
}
