/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.help.LicenseAction
import org.signal.appsettings.help.LicenseScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment

class LicenseFragment : ComposeFragment() {

  private val viewModel: LicenseViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    LicenseScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: LicenseAction) {
    when (action) {
      LicenseAction.NavigateBack -> findNavController().popBackStack()
    }
  }
}
