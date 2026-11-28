/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.thoughtcrime.securesms.mediakeyboard.SignalEmojiPicker

/**
 * Names notification profile [profileId], or a new one when it is [EditNotificationProfileViewModel.NEW_PROFILE], wired
 * to its view model.
 */
@Composable
fun EditNotificationProfileEntry(
  profileId: Long,
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: EditNotificationProfileViewModel = viewModel { EditNotificationProfileViewModel(profileId, parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val keyboard = LocalSoftwareKeyboardController.current

  DisposableEffect(keyboard) {
    onDispose { keyboard?.hide() }
  }

  EditNotificationProfileScreen(
    state = state,
    onEvent = viewModel::onEvent,
    emojiPicker = { onEmojiSelected -> SignalEmojiPicker(onEmojiSelected) }
  )
}
