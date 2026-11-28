/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.allowedmembers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent

/**
 * Choosing who notification profile [profileId] lets through during the create flow, wired to its view model.
 */
@Composable
fun AddAllowedMembersEntry(
  profileId: Long,
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: AddAllowedMembersViewModel = viewModel { AddAllowedMembersViewModel(profileId, parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  AddAllowedMembersScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
