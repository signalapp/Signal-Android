/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.labs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.labs.LabsSettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent

/**
 * Labs settings, wired to their view model.
 */
@Composable
fun LabsSettingsEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: LabsSettingsViewModel = viewModel { LabsSettingsViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  LabsSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
