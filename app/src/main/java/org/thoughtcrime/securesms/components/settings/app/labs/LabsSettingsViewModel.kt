/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.labs

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.labs.LabsSettingsAction
import org.signal.appsettings.labs.LabsSettingsEvent
import org.signal.appsettings.labs.LabsSettingsScreen
import org.signal.appsettings.labs.LabsSettingsState
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [LabsSettingsScreen].
 */
class LabsSettingsViewModel : EventDrivenViewModel<LabsSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(LabsSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(LabsSettingsRepository.getState())
  private val _actions = Channel<LabsSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<LabsSettingsState> = _state.asStateFlow()
  val actions: Flow<LabsSettingsAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: LabsSettingsEvent) {
    when (event) {
      LabsSettingsEvent.NavigateBackClicked -> _actions.send(LabsSettingsAction.NavigateBack)
      is LabsSettingsEvent.IndividualChatPlaintextExportToggled -> {
        LabsSettingsRepository.setIndividualChatPlaintextExport(event.enabled)
        _state.update { it.copy(individualChatPlaintextExport = event.enabled) }
      }
      is LabsSettingsEvent.StoryArchiveToggled -> {
        LabsSettingsRepository.setStoryArchive(event.enabled)
        _state.update { it.copy(storyArchive = event.enabled) }
      }
      is LabsSettingsEvent.IncognitoToggled -> {
        LabsSettingsRepository.setIncognito(event.enabled)
        _state.update { it.copy(incognito = event.enabled) }
      }
      is LabsSettingsEvent.BetterSearchToggled -> {
        LabsSettingsRepository.setBetterSearch(event.enabled)
        _state.update { it.copy(betterSearch = event.enabled) }
      }
      is LabsSettingsEvent.StarredMessagesToggled -> {
        LabsSettingsRepository.setStarredMessages(event.enabled)
        _state.update { it.copy(starredMessages = event.enabled) }
      }
      is LabsSettingsEvent.ImprovedMessageDeletionToggled -> {
        LabsSettingsRepository.setImprovedMessageDeletion(event.enabled)
        _state.update { it.copy(improvedMessageDeletion = event.enabled) }
      }
    }
  }
}
