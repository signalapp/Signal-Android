/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.sounds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.database.RecipientTable.NotificationSetting
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver
import org.thoughtcrime.securesms.recipients.RecipientId

class SoundsAndNotificationsSettingsViewModel2(
  private val recipientId: RecipientId
) : ViewModel(), RecipientForeverObserver {

  private val _state = MutableStateFlow(SoundsAndNotificationsSettingsState2())
  val state: StateFlow<SoundsAndNotificationsSettingsState2> = _state

  private val liveRecipient = Recipient.live(recipientId)

  init {
    liveRecipient.observeForever(this)
    onRecipientChanged(liveRecipient.get())

    viewModelScope.launch(Dispatchers.IO) {
      if (NotificationChannels.supported()) {
        NotificationChannels.getInstance().ensureCustomChannelConsistency()
      }
      _state.update { it.copy(channelConsistencyCheckComplete = true) }
    }
  }

  override fun onRecipientChanged(recipient: Recipient) {
    _state.update {
      it.copy(
        recipientId = recipientId,
        muteUntil = if (recipient.isMuted) recipient.muteUntil else 0L,
        mentionSetting = recipient.mentionSetting,
        callNotificationSetting = recipient.callNotificationSetting,
        replyNotificationSetting = recipient.replyNotificationSetting,
        hasMentionsSupport = recipient.isPushV2Group,
        hasCustomNotificationSettings = recipient.notificationChannel != null || !NotificationChannels.supported()
      )
    }
  }

  override fun onCleared() {
    liveRecipient.removeForeverObserver(this)
  }

  fun onEvent(event: SoundsAndNotificationsEvent) {
    when (event) {
      is SoundsAndNotificationsEvent.SetMuteUntil -> applySetMuteUntil(event.muteUntil)
      is SoundsAndNotificationsEvent.Unmute -> applySetMuteUntil(0L)
      is SoundsAndNotificationsEvent.SetMentionSetting -> applySetMentionSetting(event.setting)
      is SoundsAndNotificationsEvent.SetCallNotificationSetting -> applySetCallNotificationSetting(event.setting)
      is SoundsAndNotificationsEvent.SetReplyNotificationSetting -> applySetReplyNotificationSetting(event.setting)
      is SoundsAndNotificationsEvent.NavigateToCustomNotifications -> Unit // Navigation handled by UI
    }
  }

  private fun applySetMuteUntil(muteUntil: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      SignalDatabase.recipients.setMuted(recipientId, muteUntil)
    }
  }

  private fun applySetMentionSetting(setting: NotificationSetting) {
    viewModelScope.launch(Dispatchers.IO) {
      SignalDatabase.recipients.setMentionSetting(recipientId, setting)
    }
  }

  private fun applySetCallNotificationSetting(setting: NotificationSetting) {
    viewModelScope.launch(Dispatchers.IO) {
      SignalDatabase.recipients.setCallNotificationSetting(recipientId, setting)
    }
  }

  private fun applySetReplyNotificationSetting(setting: NotificationSetting) {
    viewModelScope.launch(Dispatchers.IO) {
      SignalDatabase.recipients.setReplyNotificationSetting(recipientId, setting)
    }
  }

  class Factory(private val recipientId: RecipientId) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(SoundsAndNotificationsSettingsViewModel2(recipientId)))
    }
  }
}
