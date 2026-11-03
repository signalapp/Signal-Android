/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.notifications.NotificationsSettingsAction
import org.signal.appsettings.notifications.NotificationsSettingsEvent
import org.signal.appsettings.notifications.NotificationsSettingsScreen
import org.signal.appsettings.notifications.NotificationsSettingsState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [NotificationsSettingsScreen].
 */
class NotificationsSettingsViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<NotificationsSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(NotificationsSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(
    NotificationsSettingsRepository.run {
      syncFromNotificationChannel()
      getState(NotificationsSettingsState())
    }
  )
  private val _actions = Channel<NotificationsSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<NotificationsSettingsState> = _state.asStateFlow()
  val actions: Flow<NotificationsSettingsAction> = _actions.receiveAsFlow()

  init {
    viewModelScope.launch {
      val troubleshoot = NotificationsSettingsRepository.isTroubleshootingRecommended()
      _state.update { it.copy(messageNotificationsState = it.messageNotificationsState.copy(troubleshootNotifications = troubleshoot)) }
    }

    viewModelScope.launch {
      NotificationsSettingsRepository.observeSelfChanges().collect { refresh() }
    }
  }

  override suspend fun processEvent(event: NotificationsSettingsEvent) {
    when (event) {
      NotificationsSettingsEvent.ScreenResumed -> refresh()
      NotificationsSettingsEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      NotificationsSettingsEvent.TurnOnNotificationsClicked -> _actions.send(NotificationsSettingsAction.ShowTurnOnNotificationsSheet)
      is NotificationsSettingsEvent.MessageNotificationsToggled -> changeSetting { setMessageNotificationsEnabled(event.enabled) }
      is NotificationsSettingsEvent.MessagePrivacySelected -> changeSetting { setMessageNotificationPrivacy(event.value) }
      NotificationsSettingsEvent.WhileMutedClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.NotificationsRoute.MutedNotifications)
      is NotificationsSettingsEvent.ReactionNotificationsToggled -> changeSetting { setReactionNotificationsEnabled(event.enabled) }
      is NotificationsSettingsEvent.UnreadRemindersToggled -> changeSetting { setUnreadReminderEnabled(event.enabled) }
      is NotificationsSettingsEvent.ContactJoinsSignalToggled -> changeSetting { setNotifyWhenContactJoinsSignal(event.enabled) }
      is NotificationsSettingsEvent.RepeatAlertsSelected -> changeSetting { setMessageRepeatAlerts(event.value.toInt()) }
      NotificationsSettingsEvent.CustomizeClicked -> _actions.send(NotificationsSettingsAction.OpenMessageChannelSettings)
      NotificationsSettingsEvent.MessageSoundClicked -> _actions.send(NotificationsSettingsAction.LaunchMessageSoundPicker)
      is NotificationsSettingsEvent.MessageSoundPicked -> changeSetting { setMessageNotificationsSound(event.sound) }
      is NotificationsSettingsEvent.MessageVibrateToggled -> changeSetting { setMessageNotificationVibration(event.enabled) }
      is NotificationsSettingsEvent.LedColorSelected -> changeSetting { setMessageNotificationLedColor(event.value) }
      is NotificationsSettingsEvent.LedBlinkSelected -> changeSetting { setMessageNotificationLedBlink(event.value) }
      is NotificationsSettingsEvent.InChatSoundsToggled -> changeSetting { setMessageNotificationInChatSoundsEnabled(event.enabled) }
      NotificationsSettingsEvent.TroubleshootClicked -> _actions.send(NotificationsSettingsAction.ShowTroubleshootNotificationsDialog)
      NotificationsSettingsEvent.PriorityClicked -> _actions.send(NotificationsSettingsAction.LaunchNotificationPrioritySettings)
      is NotificationsSettingsEvent.PrioritySelected -> changeSetting { setMessageNotificationPriority(event.value.toInt()) }
      is NotificationsSettingsEvent.CallNotificationsToggled -> changeSetting { setCallNotificationsEnabled(event.enabled) }
      NotificationsSettingsEvent.CallRingtoneClicked -> _actions.send(NotificationsSettingsAction.LaunchCallRingtonePicker)
      is NotificationsSettingsEvent.CallRingtonePicked -> changeSetting { setCallRingtone(event.ringtone) }
      is NotificationsSettingsEvent.CallVibrateToggled -> changeSetting { setCallVibrateEnabled(event.enabled) }
      NotificationsSettingsEvent.SoundPickerUnavailable -> _state.update { it.copy(snackbar = NotificationsSettingsState.Snackbar.SoundPickerUnavailable) }
      NotificationsSettingsEvent.NotificationProfilesClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.NotificationsRoute.NotificationProfiles)
      NotificationsSettingsEvent.ResetClicked -> _state.update { it.copy(dialog = NotificationsSettingsState.Dialog.ConfirmReset) }
      NotificationsSettingsEvent.ResetConfirmed -> {
        _state.update { it.copy(dialog = NotificationsSettingsState.Dialog.None) }
        changeSetting { resetSettings() }
      }
      NotificationsSettingsEvent.DialogDismissed -> _state.update { it.copy(dialog = NotificationsSettingsState.Dialog.None) }
      NotificationsSettingsEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = NotificationsSettingsState.Snackbar.None) }
    }
  }

  /** Applies [change] to the stored settings, then reads them back. */
  private inline fun changeSetting(change: NotificationsSettingsRepository.() -> Unit) {
    NotificationsSettingsRepository.change()
    refresh()
  }

  private fun refresh() {
    _state.update { NotificationsSettingsRepository.getState(it) }
  }
}
