/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.expire

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.expiretimer.ExpireTimerSettingsAction
import org.signal.appsettings.expiretimer.ExpireTimerSettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions

/**
 * The disappearing messages timer picker, wired to its view model. In [ExpireTimerSettingsViewModel.Config.forResultMode]
 * the chosen timer is returned to the calling activity as [ExpireTimerSettingsFragment.FOR_RESULT_VALUE].
 */
@Composable
fun ExpireTimerSettingsEntry(
  config: ExpireTimerSettingsViewModel.Config,
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val activity = LocalActivity.current
  val viewModel: ExpireTimerSettingsViewModel = viewModel { ExpireTimerSettingsViewModel(config, parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  CollectActions(viewModel.actions) { action ->
    when (action) {
      is ExpireTimerSettingsAction.ReturnResult -> {
        activity?.setResult(Activity.RESULT_OK, Intent().putExtra(ExpireTimerSettingsFragment.FOR_RESULT_VALUE, action.timer.inWholeSeconds.toInt()))
        activity?.finish()
      }
    }
  }

  ExpireTimerSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
