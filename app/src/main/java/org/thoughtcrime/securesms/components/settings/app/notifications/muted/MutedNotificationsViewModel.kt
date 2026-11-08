/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.muted

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.mutednotifications.MutedNotificationsEvent
import org.signal.appsettings.mutednotifications.MutedNotificationsScreen
import org.signal.appsettings.mutednotifications.MutedNotificationsState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [MutedNotificationsScreen], for the defaults of every muted chat when [recipientId] is null, and for that chat's
 * own settings otherwise.
 */
class MutedNotificationsViewModel(
  private val recipientId: RecipientId?,
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<MutedNotificationsEvent>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(MutedNotificationsViewModel::class)
  }

  private val _state = MutableStateFlow(
    if (recipientId == null) MutedNotificationsRepository.getDefaultSettings() else MutedNotificationsState(isGlobal = false)
  )

  val state: StateFlow<MutedNotificationsState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      MutedNotificationsRepository.observeSettings(recipientId).collect { settings -> _state.value = settings }
    }
  }

  override suspend fun processEvent(event: MutedNotificationsEvent) {
    when (event) {
      MutedNotificationsEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      is MutedNotificationsEvent.CallsToggled -> {
        MutedNotificationsRepository.setAllowCalls(recipientId, event.allowCalls)
        _state.update { it.copy(allowCalls = event.allowCalls) }
      }
      is MutedNotificationsEvent.MentionsToggled -> {
        MutedNotificationsRepository.setAllowMentions(recipientId, event.allowMentions)
        _state.update { it.copy(allowMentions = event.allowMentions) }
      }
      is MutedNotificationsEvent.RepliesToggled -> {
        MutedNotificationsRepository.setAllowReplies(recipientId, event.allowReplies)
        _state.update { it.copy(allowReplies = event.allowReplies) }
      }
    }
  }
}
