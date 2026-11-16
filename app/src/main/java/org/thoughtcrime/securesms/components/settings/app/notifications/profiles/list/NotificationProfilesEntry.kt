/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent

/**
 * The list of notification profiles, wired to its view model.
 */
@Composable
fun NotificationProfilesEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: NotificationProfilesViewModel = viewModel { NotificationProfilesViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  NotificationProfilesScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
