/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.labs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.labs.LabsSettingsEvent
import org.signal.appsettings.labs.LabsSettingsScreen
import org.signal.appsettings.labs.LabsSettingsState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [LabsSettingsScreen].
 */
class LabsSettingsViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<LabsSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(LabsSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(LabsSettingsRepository.getState())

  val state: StateFlow<LabsSettingsState> = _state.asStateFlow()

  override suspend fun processEvent(event: LabsSettingsEvent) {
    when (event) {
      LabsSettingsEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
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
