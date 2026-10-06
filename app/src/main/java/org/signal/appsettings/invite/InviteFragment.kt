/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.invite

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.viewModel
import org.signal.core.util.invite.InviteActions
import org.signal.appsettings.R as AppSettingsR
import org.signal.core.ui.R as CoreUiR
import org.signal.core.util.R as CoreUtilsR

/**
 * Fragment when inviting someone to use Signal
 */
class InviteFragment : ComposeFragment() {
  private val viewModel: InviteViewModel by viewModel {
    InviteViewModel(
      defaultInviteText = getString(
        CoreUtilsR.string.Invite__lets_switch_to_signal,
        getString(CoreUtilsR.string.install_url)
      )
    )
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action ->
      when (action) {
        is InviteAction.ShareInvite -> InviteActions.inviteUserToSignal(requireContext(), ::startActivity, action.inviteText)
        InviteAction.NavigateBack -> findNavController().popBackStack()
      }
    }

    Scaffolds.Settings(
      title = stringResource(id = AppSettingsR.string.InviteScreen__invite_friends),
      onNavigationClick = { viewModel.onEvent(InviteEvent.NavigateBackClicked) },
      navigationIcon = SignalIcons.ArrowStart.imageVector,
      navigationContentDescription = stringResource(id = CoreUiR.string.CloseButton__content_description)
    ) { contentPadding: PaddingValues ->
      InviteScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = Modifier.padding(contentPadding)
      )
    }
  }
}
