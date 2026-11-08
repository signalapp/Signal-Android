/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.help.HelpSettingsAction
import org.signal.appsettings.help.HelpSettingsScreen
import org.signal.appsettings.help.HelpSettingsViewModel
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.viewModel
import org.signal.core.util.Util
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.signal.appsettings.R as AppSettingsR

class HelpSettingsFragment : ComposeFragment() {

  private val viewModel: HelpSettingsViewModel by viewModel { HelpSettingsViewModel(versionName = BuildConfig.VERSION_NAME) }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    HelpSettingsScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: HelpSettingsAction) {
    when (action) {
      HelpSettingsAction.NavigateBack -> findNavController().popBackStack()
      HelpSettingsAction.OpenSupportCenter -> CommunicationActions.openBrowserLink(requireContext(), getString(R.string.support_center_url))
      HelpSettingsAction.NavigateToContactUs -> findNavController().safeNavigate(R.id.action_helpSettingsFragment_to_helpFragment)
      is HelpSettingsAction.CopyVersionToClipboard -> {
        Util.copyToClipboard(requireContext(), action.versionName)
        Toast.makeText(requireContext(), AppSettingsR.string.HelpSettingsFragment__copied_to_clipboard, Toast.LENGTH_SHORT).show()
      }
      HelpSettingsAction.NavigateToDebugLog -> findNavController().safeNavigate(R.id.action_helpSettingsFragment_to_submitDebugLogActivity)
      HelpSettingsAction.NavigateToLicenses -> findNavController().safeNavigate(R.id.action_helpSettingsFragment_to_licenseFragment)
      HelpSettingsAction.OpenTermsAndPrivacyPolicy -> CommunicationActions.openBrowserLink(requireContext(), getString(R.string.terms_and_privacy_policy_url))
    }
  }
}
