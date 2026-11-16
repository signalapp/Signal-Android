/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.remoteconfig

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.CollectActions
import org.signal.core.util.AppUtil

/**
 * The remote config override screen, wired to its view model, leaving through [parentEventEmitter].
 */
@Composable
fun InternalRemoteConfigEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val context = LocalContext.current
  val viewModel: InternalRemoteConfigViewModel = viewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()

  CollectActions(viewModel.actions) { action ->
    when (action) {
      InternalRemoteConfigAction.Exit -> parentEventEmitter.navigateBack()
      InternalRemoteConfigAction.RestartApp -> AppUtil.restart(context)
    }
  }

  InternalRemoteConfigScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
