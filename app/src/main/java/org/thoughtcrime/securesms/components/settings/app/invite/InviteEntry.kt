/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.invite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.invite.InviteAction
import org.signal.appsettings.invite.InviteScreen
import org.signal.appsettings.invite.InviteViewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.signal.core.util.invite.InviteActions
import org.signal.core.util.R as CoreUtilsR

/**
 * The invite screen, wired to its view model and to the share sheet.
 */
@Composable
fun InviteEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val context = LocalContext.current
  val defaultInviteText = stringResource(CoreUtilsR.string.Invite__lets_switch_to_signal, stringResource(CoreUtilsR.string.install_url))
  val viewModel: InviteViewModel = viewModel { InviteViewModel(defaultInviteText = defaultInviteText, parentEventEmitter = parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  CollectActions(viewModel.actions) { action ->
    when (action) {
      is InviteAction.ShareInvite -> InviteActions.inviteUserToSignal(context, context::startActivity, action.inviteText)
    }
  }

  InviteScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
