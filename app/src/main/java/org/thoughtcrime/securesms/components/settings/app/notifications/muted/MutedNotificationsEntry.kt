/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.muted

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.mutednotifications.MutedNotificationsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.models.recipients.RecipientId

/**
 * Which notifications still come through while muted, wired to its view model: the defaults for every muted chat when
 * [recipientId] is null, and that chat's own settings otherwise.
 */
@Composable
fun MutedNotificationsEntry(
  recipientId: RecipientId?,
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: MutedNotificationsViewModel = viewModel { MutedNotificationsViewModel(recipientId, parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  MutedNotificationsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
