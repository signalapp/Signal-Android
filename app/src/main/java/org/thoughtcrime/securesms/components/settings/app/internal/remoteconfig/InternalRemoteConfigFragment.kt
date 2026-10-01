/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.remoteconfig

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.AppUtil

class InternalRemoteConfigFragment : ComposeFragment() {

  private val viewModel: InternalRemoteConfigViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    InternalRemoteConfigScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: InternalRemoteConfigAction) {
    when (action) {
      InternalRemoteConfigAction.Exit -> requireActivity().onBackPressedDispatcher.onBackPressed()
      InternalRemoteConfigAction.RestartApp -> AppUtil.restart(requireContext())
    }
  }
}
