/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.created

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.notificationprofilecreated.NotificationProfileCreatedScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent

/**
 * The end of the create flow for notification profile [profileId], wired to its view model.
 */
@Composable
fun NotificationProfileCreatedEntry(
  profileId: Long,
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: NotificationProfileCreatedViewModel = viewModel { NotificationProfileCreatedViewModel(profileId, parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  NotificationProfileCreatedScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
