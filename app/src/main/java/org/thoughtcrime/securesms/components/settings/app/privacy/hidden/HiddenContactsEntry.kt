/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.hidden

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.hiddencontacts.HiddenContactsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.thoughtcrime.securesms.avatar.AvatarImage

/**
 * Hosts [HiddenContactsScreen]. The view model navigates by sending events to [parentEventEmitter], so this works from
 * whichever host owns the app settings back stack.
 */
@Composable
fun HiddenContactsEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: HiddenContactsViewModel = viewModel { HiddenContactsViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  HiddenContactsScreen(
    state = state,
    onEvent = viewModel::onEvent,
    avatar = { recipientId -> AvatarImage(recipientId = recipientId, modifier = Modifier.fillMaxSize()) }
  )
}
