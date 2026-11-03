/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent

/**
 * The details of notification profile [profileId], wired to its view model.
 */
@Composable
fun NotificationProfileDetailsEntry(
  profileId: Long,
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: NotificationProfileDetailsViewModel = viewModel { NotificationProfileDetailsViewModel(profileId, parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  NotificationProfileDetailsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
