/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.notificationprofilecreated.NotificationProfileCreatedAction
import org.signal.appsettings.notificationprofilecreated.NotificationProfileCreatedEvent
import org.signal.appsettings.notificationprofilecreated.NotificationProfileCreatedScreen
import org.signal.appsettings.notificationprofilecreated.NotificationProfileCreatedState
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [NotificationProfileCreatedScreen].
 */
class NotificationProfileCreatedViewModel(
  private val profileId: Long
) : EventDrivenViewModel<NotificationProfileCreatedEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(NotificationProfileCreatedViewModel::class)
  }

  private val _state = MutableStateFlow(NotificationProfileCreatedState())
  private val _actions = Channel<NotificationProfileCreatedAction>(Channel.BUFFERED)

  val state: StateFlow<NotificationProfileCreatedState> = _state.asStateFlow()
  val actions: Flow<NotificationProfileCreatedAction> = _actions.receiveAsFlow()

  init {
    viewModelScope.launch {
      val hasSchedule = NotificationProfileCreatedRepository.hasSchedule(profileId)
      _state.update { it.copy(hasSchedule = hasSchedule) }
    }
  }

  override suspend fun processEvent(event: NotificationProfileCreatedEvent) {
    when (event) {
      NotificationProfileCreatedEvent.DoneClicked -> _actions.send(NotificationProfileCreatedAction.NavigateToProfileDetails(profileId))
    }
  }
}
