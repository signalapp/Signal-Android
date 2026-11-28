/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.about.EditAboutScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.emoji.LocalEmojiTextInput
import org.thoughtcrime.securesms.components.emoji.SignalEmojiTextInput
import org.thoughtcrime.securesms.mediakeyboard.SignalEmojiPicker

/**
 * Hosts [EditAboutScreen]. The view model navigates by sending events to [parentEventEmitter], so this works from
 * whichever host owns the back stack.
 */
@Composable
fun EditAboutEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: EditAboutViewModel = viewModel { EditAboutViewModel(parentEventEmitter, createSavedStateHandle()) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  CompositionLocalProvider(LocalEmojiTextInput provides SignalEmojiTextInput) {
    EditAboutScreen(
      state = state,
      onEvent = viewModel::onEvent,
      emojiPicker = { onEmojiSelected -> SignalEmojiPicker(onEmojiSelected) }
    )
  }
}
