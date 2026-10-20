/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance.appicon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.appearance.appicon.AppIconSelectionAction
import org.signal.appsettings.appearance.appicon.AppIconSelectionScreen
import org.signal.appsettings.appearance.appicon.AppIconSelectionViewModel
import org.signal.appsettings.appearance.appicon.AppIconUtility
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.viewModel
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class AppIconSelectionFragment : ComposeFragment() {

  private val viewModel: AppIconSelectionViewModel by viewModel { AppIconSelectionViewModel(AppIconUtility(requireContext())) }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    AppIconSelectionScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: AppIconSelectionAction) {
    when (action) {
      AppIconSelectionAction.NavigateBack -> findNavController().popBackStack()
      AppIconSelectionAction.NavigateToTutorial -> findNavController().safeNavigate(R.id.action_appIconSelectionFragment_to_appIconTutorialFragment)
    }
  }
}
