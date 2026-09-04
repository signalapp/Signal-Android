/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.totpnameentry.TotpNameEntryAction
import org.signal.appsettings.totpnameentry.TotpNameEntryScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.viewModel
import org.signal.appsettings.R as AppSettingsR

/**
 * Wrapper around [TotpNameEntryScreen].
 */
class TotpNameEntryFragment : ComposeFragment() {

  /** The name the user gives an authenticator app is not a credential, so there's nothing here worth offering to save. */
  override val autofillEnabled: Boolean = false

  private val viewModel: TotpNameEntryViewModel by viewModel {
    TotpNameEntryViewModel(
      appId = TotpNavArgs.appId(arguments) ?: TotpNavArgs.NO_APP_ID,
      renamedApp = TotpNavArgs.renamedApp(arguments)
    )
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    TotpNameEntryScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: TotpNameEntryAction) {
    when (action) {
      TotpNameEntryAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
      TotpNameEntryAction.NavigateToAccountSettings -> findNavController().popBackStack(R.id.accountSettingsFragment, false)
      TotpNameEntryAction.ShowTotpAppSetUp -> toast(AppSettingsR.string.TotpNameEntryScreen__authenticator_app_set_up)
      TotpNameEntryAction.ShowTotpAppRenamed -> toast(AppSettingsR.string.TotpNameEntryScreen__authenticator_app_renamed)
      TotpNameEntryAction.ShowNameNotSaved -> toast(AppSettingsR.string.TotpNameEntryScreen__couldnt_save_name)
    }
  }

  private fun toast(@StringRes message: Int) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
  }
}
