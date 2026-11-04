/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent

/**
 * The schedule of notification profile [profileId], as the create flow's last step when [createMode] and on its own
 * otherwise, wired to its view model.
 */
@Composable
fun EditNotificationProfileScheduleEntry(
  profileId: Long,
  createMode: Boolean,
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: EditNotificationProfileScheduleViewModel = viewModel { EditNotificationProfileScheduleViewModel(profileId, createMode, parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  EditNotificationProfileScheduleScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
