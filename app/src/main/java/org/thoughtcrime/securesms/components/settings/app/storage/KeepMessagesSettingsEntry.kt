/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.managestorage.KeepMessagesSettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent

@Composable
fun KeepMessagesSettingsEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: KeepMessagesSettingsViewModel = viewModel { KeepMessagesSettingsViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  KeepMessagesSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
