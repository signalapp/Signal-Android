/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.navigation.LocalResultEventBus
import org.signal.emoji.LocalEmojiTextInput
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.picker.AvatarEditorResult
import org.thoughtcrime.securesms.components.emoji.SignalEmojiTextInput

/**
 * Hosts [TextAvatarCreationScreen], starting from [initialAvatar] and sending the finished avatar to the host's result
 * bus as an [AvatarEditorResult].
 */
@Composable
fun TextAvatarCreationEntry(initialAvatar: Avatar.Text, parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: TextAvatarCreationViewModel = viewModel { TextAvatarCreationViewModel(initialAvatar) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val resultBus = LocalResultEventBus.current

  CollectActions(viewModel.actions) { action ->
    when (action) {
      is TextAvatarCreationAction.Finish -> {
        resultBus.sendResult(result = AvatarEditorResult(action.avatar))
        parentEventEmitter(AppSettingsNavigationEvent.NavigateBack())
      }

      TextAvatarCreationAction.Close -> parentEventEmitter(AppSettingsNavigationEvent.NavigateBack())
    }
  }

  CompositionLocalProvider(LocalEmojiTextInput provides SignalEmojiTextInput) {
    TextAvatarCreationScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }
}
