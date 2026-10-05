/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.invites

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.invite.InviteAction
import org.signal.appsettings.invite.InviteEvent
import org.signal.appsettings.invite.InviteScreen
import org.signal.appsettings.invite.InviteViewModel
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.viewModel
import org.thoughtcrime.securesms.R
import org.signal.appsettings.R as AppSettingsR
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
        is InviteAction.ShareInvite -> onShare(action.inviteText)
        InviteAction.NavigateBack -> findNavController().popBackStack()
      }
    }

    Scaffolds.Settings(
      title = stringResource(id = AppSettingsR.string.InviteScreen__invite_friends),
      onNavigationClick = { viewModel.onEvent(InviteEvent.NavigateBackClicked) },
      navigationIcon = SignalIcons.ArrowStart.imageVector,
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) { contentPadding: PaddingValues ->
      InviteScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = Modifier.padding(contentPadding)
      )
    }
  }

  private fun onShare(inviteText: String) {
    val sendIntent = Intent()
      .setAction(Intent.ACTION_SEND)
      .putExtra(Intent.EXTRA_TEXT, inviteText)
      .setType("text/plain")

    try {
      startActivity(Intent.createChooser(sendIntent, getString(AppSettingsR.string.InviteScreen__invite_to_signal)))
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(requireContext(), AppSettingsR.string.InviteScreen__no_app_to_share_to, Toast.LENGTH_LONG).show()
    }
  }
}
