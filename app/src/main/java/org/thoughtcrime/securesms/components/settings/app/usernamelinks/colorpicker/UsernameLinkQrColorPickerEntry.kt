/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.colorpicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.usernamecolorpicker.UsernameLinkQrColorPickerScreen

/**
 * Hosts [UsernameLinkQrColorPickerScreen]. The view model navigates by sending events to [parentEventEmitter], so this
 * works from whichever host owns the back stack.
 */
@Composable
fun UsernameLinkQrColorPickerEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: UsernameLinkQrColorPickerViewModel = viewModel { UsernameLinkQrColorPickerViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  UsernameLinkQrColorPickerScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
