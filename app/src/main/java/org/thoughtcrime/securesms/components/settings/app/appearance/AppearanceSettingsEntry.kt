/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.appearance.AppearanceSettingsAction
import org.signal.appsettings.appearance.AppearanceSettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.thoughtcrime.securesms.util.SplashScreenUtil

/**
 * Hosts [AppearanceSettingsScreen] and carries out its [AppearanceSettingsAction]s. The view model navigates by sending
 * events to [parentEventEmitter], so this works from whichever host owns the app settings back stack.
 */
@Composable
fun AppearanceSettingsEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: AppearanceSettingsViewModel = viewModel { AppearanceSettingsViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val activity = LocalActivity.current

  CollectActions(viewModel.actions) { action ->
    when (action) {
      is AppearanceSettingsAction.ApplySplashScreenTheme -> SplashScreenUtil.setSplashScreenThemeIfNecessary(activity, action.theme.toSettingsTheme())
    }
  }

  AppearanceSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
