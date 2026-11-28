/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.thoughtcrime.securesms.components.settings.app.subscription.MessageBackupsCheckoutLauncher.rememberBackupsCheckoutLauncher
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Top-level backups settings, wired to its view model and to the checkout flow it can start.
 */
@Composable
fun BackupsSettingsEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  launchCheckoutFlow: Boolean = false
) {
  val context = LocalContext.current
  val viewModel: BackupsSettingsViewModel = viewModel {
    BackupsSettingsViewModel(
      parentEventEmitter = parentEventEmitter,
      launchCheckoutFlow = launchCheckoutFlow,
      savedStateHandle = createSavedStateHandle()
    )
  }
  val state by viewModel.state.collectAsStateWithLifecycle()

  val checkoutLauncher = rememberBackupsCheckoutLauncher {
    viewModel.onEvent(BackupsSettingsEvent.CheckoutCompleted)
  }

  CollectActions(viewModel.actions) { action ->
    when (action) {
      BackupsSettingsAction.LaunchCheckout -> checkoutLauncher.launch(null)
      is BackupsSettingsAction.OpenSupportArticle -> CommunicationActions.openBrowserLink(context, action.url)
    }
  }

  BackupsSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
