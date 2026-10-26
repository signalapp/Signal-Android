/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.name

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.profilename.EditProfileNameScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.emoji.LocalEmojiTextInput
import org.thoughtcrime.securesms.components.emoji.SignalEmojiTextInput

/**
 * Hosts [EditProfileNameScreen]. The view model navigates by sending events to [parentEventEmitter], so this works from
 * whichever host owns the back stack.
 */
@Composable
fun EditProfileNameEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: EditProfileNameViewModel = viewModel { EditProfileNameViewModel(parentEventEmitter, createSavedStateHandle()) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  CompositionLocalProvider(LocalEmojiTextInput provides SignalEmojiTextInput) {
    EditProfileNameScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }
}
