/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.created

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.notificationprofilecreated.NotificationProfileCreatedEvent
import org.signal.appsettings.notificationprofilecreated.NotificationProfileCreatedScreen
import org.signal.appsettings.notificationprofilecreated.NotificationProfileCreatedState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.PopUpTo
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [NotificationProfileCreatedScreen].
 */
class NotificationProfileCreatedViewModel(
  private val profileId: Long,
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<NotificationProfileCreatedEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(NotificationProfileCreatedViewModel::class)
  }

  private val _state = MutableStateFlow(NotificationProfileCreatedState())

  val state: StateFlow<NotificationProfileCreatedState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      val hasSchedule = NotificationProfileCreatedRepository.hasSchedule(profileId)
      _state.update { it.copy(hasSchedule = hasSchedule) }
    }
  }

  override suspend fun processEvent(event: NotificationProfileCreatedEvent) {
    when (event) {
      NotificationProfileCreatedEvent.DoneClicked -> parentEventEmitter.navigateTo(
        route = AppSettingsRoute.NotificationsRoute.ProfileDetails(profileId),
        popUpTo = PopUpTo(AppSettingsRoute.NotificationsRoute.Created::class, inclusive = true)
      )
    }
  }
}
