/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.vector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.navigation.LocalResultEventBus
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.picker.AvatarEditorResult

/**
 * Hosts [VectorAvatarCreationScreen], starting from [initialAvatar] and sending the finished avatar to the host's
 * result bus as an [AvatarEditorResult].
 */
@Composable
fun VectorAvatarCreationEntry(initialAvatar: Avatar.Vector, parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: VectorAvatarCreationViewModel = viewModel { VectorAvatarCreationViewModel(initialAvatar) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val resultBus = LocalResultEventBus.current

  CollectActions(viewModel.actions) { action ->
    when (action) {
      is VectorAvatarCreationAction.Finish -> {
        resultBus.sendResult(result = AvatarEditorResult(action.avatar))
        parentEventEmitter(AppSettingsNavigationEvent.NavigateBack())
      }

      VectorAvatarCreationAction.Close -> parentEventEmitter(AppSettingsNavigationEvent.NavigateBack())
    }
  }

  VectorAvatarCreationScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
