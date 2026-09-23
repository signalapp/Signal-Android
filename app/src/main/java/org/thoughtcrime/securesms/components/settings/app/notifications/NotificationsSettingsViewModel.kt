package org.thoughtcrime.securesms.components.settings.app.notifications

import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.DeviceSpecificNotificationConfig
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.SlowNotificationHeuristics
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver
import org.thoughtcrime.securesms.storage.StorageSyncHelper

class NotificationsSettingsViewModel : ViewModel(), RecipientForeverObserver {

  companion object {
    private val TAG = Log.tag(NotificationsSettingsViewModel::class)
  }

  private val store = MutableStateFlow(getState())

  val state: StateFlow<NotificationsSettingsState> = store

  private val self = Recipient.self().live()

  init {
    if (NotificationChannels.supported()) {
      SignalStore.settings.messageNotificationSound = NotificationChannels.getInstance().messageRingtone
      SignalStore.settings.isMessageVibrateEnabled = NotificationChannels.getInstance().messageVibrate
    }

    // Calculating slow notification stuff isn't thread-safe, so we do it without to start off so we have most state populated, then fetch it in the background.
    store.update { getState(calculateSlowNotifications = false) }
    viewModelScope.launch(Dispatchers.Default) {
      store.update { getState(calculateSlowNotifications = true) }
    }

    self.observeForever(this)
  }

  override fun onRecipientChanged(recipient: Recipient) {
    refresh()
  }

  override fun onCleared() {
    self.removeForeverObserver(this)
  }

  fun refresh() {
    store.update { getState(currentState = it) }
  }

  fun setMessageNotificationsEnabled(enabled: Boolean) {
    SignalStore.settings.isMessageNotificationsEnabled = enabled
    refresh()
  }

  fun setMessageNotificationsSound(sound: Uri?) {
    val messageSound = sound ?: Uri.EMPTY
    SignalStore.settings.messageNotificationSound = messageSound
    NotificationChannels.getInstance().updateMessageRingtone(messageSound)
    refresh()
  }

  fun setMessageNotificationVibration(enabled: Boolean) {
    SignalStore.settings.isMessageVibrateEnabled = enabled
    NotificationChannels.getInstance().updateMessageVibrate(enabled)
    refresh()
  }

  fun setMessageNotificationLedColor(color: String) {
    SignalStore.settings.messageLedColor = color
    NotificationChannels.getInstance().updateMessagesLedColor(color)
    refresh()
  }

  fun setMessageNotificationLedBlink(blink: String) {
    SignalStore.settings.messageLedBlinkPattern = blink
    refresh()
  }

  fun setMessageNotificationInChatSoundsEnabled(enabled: Boolean) {
    SignalStore.settings.isMessageNotificationsInChatSoundsEnabled = enabled
    refresh()
  }

  fun setMessageRepeatAlerts(repeats: Int) {
    SignalStore.settings.messageNotificationsRepeatAlerts = repeats
    refresh()
  }

  fun setMessageNotificationPrivacy(preference: String) {
    SignalStore.settings.messageNotificationsPrivacy = NotificationPrivacyPreference(preference)
    refresh()
  }

  fun setMessageNotificationPriority(priority: Int) {
    SignalStore.settings.messageNotificationPriority = priority
    refresh()
  }

  fun setCallNotificationsEnabled(enabled: Boolean) {
    SignalStore.settings.isCallNotificationsEnabled = enabled
    refresh()
  }

  fun setCallRingtone(ringtone: Uri?) {
    SignalStore.settings.callRingtone = ringtone ?: Uri.EMPTY
    refresh()
  }

  fun setCallVibrateEnabled(enabled: Boolean) {
    SignalStore.settings.isCallVibrateEnabled = enabled
    refresh()
  }

  fun setNotifyWhenContactJoinsSignal(enabled: Boolean) {
    SignalStore.settings.isNotifyWhenContactJoinsSignal = enabled
    markSelfNeedsSync()
    refresh()
  }

  fun setReactionNotificationEnabled(enabled: Boolean) {
    SignalStore.settings.reactionNotifications = enabled
    markSelfNeedsSync()
    refresh()
  }

  fun setUnreadReminderEnabled(enabled: Boolean) {
    SignalStore.settings.unreadReminderEnabled = enabled
    markSelfNeedsSync()
    refresh()
  }

  fun resetSettings() {
    Log.i(TAG, "Resetting all notifications.")
    // Global
    setMessageNotificationsSound(Settings.System.DEFAULT_NOTIFICATION_URI)
    SignalStore.settings.isMessageNotificationsInChatSoundsEnabled = true
    SignalStore.settings.messageNotificationsPrivacy = NotificationPrivacyPreference("all")
    SignalStore.settings.allowCallsWhileMuted = false
    SignalStore.settings.allowMentionsWhileMuted = true
    SignalStore.settings.allowRepliesWhileMuted = true
    SignalStore.settings.reactionNotifications = true
    SignalStore.settings.unreadReminderEnabled = true
    SignalStore.settings.isNotifyWhenContactJoinsSignal = false
    SignalStore.settings.messageNotificationsRepeatAlerts = 0

    // Per-chat
    viewModelScope.launch(SignalDispatchers.Default) {
      SignalDatabase.recipients.resetAllChatNotificationSettings()
    }

    markSelfNeedsSync()
    refresh()
  }

  private fun markSelfNeedsSync() {
    viewModelScope.launch(SignalDispatchers.Default) {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  /**
   * @param currentState If provided and [calculateSlowNotifications] = false, then we will copy the slow notification state from it
   * @param calculateSlowNotifications If true, calculate the true slow notification state (this is not main-thread safe). Otherwise, it will copy from
   * [currentState] or default to false.
   */
  private fun getState(currentState: NotificationsSettingsState? = null, calculateSlowNotifications: Boolean = false): NotificationsSettingsState = NotificationsSettingsState(
    messageNotificationsState = MessageNotificationsState(
      notificationsEnabled = SignalStore.settings.isMessageNotificationsEnabled && canEnableNotifications(),
      canEnableNotifications = canEnableNotifications(),
      sound = SignalStore.settings.messageNotificationSound,
      vibrateEnabled = SignalStore.settings.isMessageVibrateEnabled,
      ledColor = SignalStore.settings.messageLedColor,
      ledBlink = SignalStore.settings.messageLedBlinkPattern,
      inChatSoundsEnabled = SignalStore.settings.isMessageNotificationsInChatSoundsEnabled,
      repeatAlerts = SignalStore.settings.messageNotificationsRepeatAlerts,
      messagePrivacy = SignalStore.settings.messageNotificationsPrivacy.toString(),
      priority = SignalStore.settings.messageNotificationPriority,
      troubleshootNotifications = if (calculateSlowNotifications) {
        (SlowNotificationHeuristics.isBatteryOptimizationsOn() && SlowNotificationHeuristics.isHavingDelayedNotifications()) ||
          SlowNotificationHeuristics.getDeviceSpecificShowCondition() == DeviceSpecificNotificationConfig.ShowCondition.ALWAYS
      } else if (currentState != null) {
        currentState.messageNotificationsState.troubleshootNotifications
      } else {
        false
      },
      reactionNotificationEnabled = SignalStore.settings.reactionNotifications,
      unreadReminderEnabled = SignalStore.settings.unreadReminderEnabled,
      allowCallsWhileMuted = SignalStore.settings.allowCallsWhileMuted,
      allowMentionsWhileMuted = SignalStore.settings.allowMentionsWhileMuted,
      allowRepliesWhileMuted = SignalStore.settings.allowRepliesWhileMuted
    ),
    callNotificationsState = CallNotificationsState(
      notificationsEnabled = SignalStore.settings.isCallNotificationsEnabled && canEnableNotifications(),
      canEnableNotifications = canEnableNotifications(),
      ringtone = SignalStore.settings.callRingtone,
      vibrateEnabled = SignalStore.settings.isCallVibrateEnabled
    ),
    notifyWhenContactJoinsSignal = SignalStore.settings.isNotifyWhenContactJoinsSignal
  )

  private fun canEnableNotifications(): Boolean {
    val areNotificationsDisabledBySystem = Build.VERSION.SDK_INT >= 26 &&
      (
        !NotificationChannels.getInstance().isMessageChannelEnabled ||
          !NotificationChannels.getInstance().isMessagesChannelGroupEnabled ||
          !NotificationChannels.getInstance().areNotificationsEnabled()
        )

    return !areNotificationsDisabledBySystem
  }

  class Factory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(NotificationsSettingsViewModel()))
    }
  }
}
