/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.dataandstorage.DataAndStorageSettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent

/**
 * The data and storage section, wired to its view model.
 */
@Composable
fun DataAndStorageSettingsEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: DataAndStorageSettingsViewModel = viewModel { DataAndStorageSettingsViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  DataAndStorageSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
