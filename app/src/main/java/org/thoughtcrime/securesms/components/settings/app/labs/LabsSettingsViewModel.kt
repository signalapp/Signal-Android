/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.labs

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.thoughtcrime.securesms.keyvalue.SignalStore

class LabsSettingsViewModel : ViewModel() {

  private val _state = MutableStateFlow(loadState())
  val state: StateFlow<LabsSettingsState> = _state

  fun onEvent(event: LabsSettingsEvents) {
    when (event) {
      is LabsSettingsEvents.ToggleIndividualChatPlaintextExport -> {
        SignalStore.labs.individualChatPlaintextExport = event.enabled
        _state.value = _state.value.copy(individualChatPlaintextExport = event.enabled)
      }
      is LabsSettingsEvents.ToggleStoryArchive -> {
        SignalStore.labs.storyArchive = event.enabled
        _state.value = _state.value.copy(storyArchive = event.enabled)
      }
      is LabsSettingsEvents.ToggleIncognito -> {
        SignalStore.labs.incognito = event.enabled
        _state.value = _state.value.copy(incognito = event.enabled)
      }
      is LabsSettingsEvents.ToggleBetterSearch -> {
        SignalStore.labs.betterSearch = event.enabled
        _state.value = _state.value.copy(betterSearch = event.enabled)
      }
      is LabsSettingsEvents.ToggleStarredMessages -> {
        SignalStore.labs.starredMessages = event.enabled
        _state.value = _state.value.copy(starredMessages = event.enabled)
      }
      is LabsSettingsEvents.ToggleStickerReplies -> {
        SignalStore.labs.stickerReplies = event.enabled
        _state.value = _state.value.copy(stickerReplies = event.enabled)
      }
      is LabsSettingsEvents.ToggleMuteBreakthroughNotifications -> {
        SignalStore.labs.muteBreakthroughNotifications = event.enabled
        _state.value = _state.value.copy(muteBreakthroughNotifications = event.enabled)
      }
    }
  }

  private fun loadState(): LabsSettingsState {
    return LabsSettingsState(
      individualChatPlaintextExport = SignalStore.labs.individualChatPlaintextExport,
      storyArchive = SignalStore.labs.storyArchive,
      incognito = SignalStore.labs.incognito,
      betterSearch = SignalStore.labs.betterSearch,
      starredMessages = SignalStore.labs.starredMessages,
      stickerReplies = SignalStore.labs.stickerReplies,
      muteBreakthroughNotifications = SignalStore.labs.muteBreakthroughNotifications
    )
  }
}
