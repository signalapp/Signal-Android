/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.screenlock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.screenlock.ScreenLockSettingsAction
import org.signal.appsettings.screenlock.ScreenLockSettingsEvent
import org.signal.appsettings.screenlock.ScreenLockSettingsScreen
import org.signal.core.ui.compose.CollectActions

/**
 * Hosts [ScreenLockSettingsScreen], navigating back through [parentEventEmitter].
 */
@Composable
fun ScreenLockSettingsEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: ScreenLockSettingsViewModel = viewModel { ScreenLockSettingsViewModel() }
  val state by viewModel.state.collectAsStateWithLifecycle()

  LifecycleResumeEffect(Unit) {
    viewModel.onEvent(ScreenLockSettingsEvent.ScreenResumed)
    onPauseOrDispose { }
  }

  CollectActions(viewModel.actions) { action ->
    when (action) {
      ScreenLockSettingsAction.NavigateBack -> parentEventEmitter.navigateBack()
    }
  }

  ScreenLockSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
