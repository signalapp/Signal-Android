package org.thoughtcrime.securesms.components.settings.app.notifications

import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.DeviceSpecificNotificationConfig
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.SlowNotificationHeuristics
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.livedata.Store

class NotificationsSettingsViewModel(private val sharedPreferences: SharedPreferences) : ViewModel() {

  private val store = Store(getState())

  val state: LiveData<NotificationsSettingsState> = store.stateLiveData

  init {
    if (NotificationChannels.supported()) {
      SignalStore.settings.messageNotificationSound = NotificationChannels.getInstance().messageRingtone
      SignalStore.settings.isMessageVibrateEnabled = NotificationChannels.getInstance().messageVibrate
    }

    store.update { getState(calculateSlowNotifications = true) }
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
    sharedPreferences.edit().putString(TextSecurePreferences.NOTIFICATION_PRIORITY_PREF, priority.toString()).apply()
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
    refresh()
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
      priority = TextSecurePreferences.getNotificationPriority(AppDependencies.application),
      troubleshootNotifications = if (calculateSlowNotifications) {
        (SlowNotificationHeuristics.isBatteryOptimizationsOn() && SlowNotificationHeuristics.isHavingDelayedNotifications()) ||
          SlowNotificationHeuristics.getDeviceSpecificShowCondition() == DeviceSpecificNotificationConfig.ShowCondition.ALWAYS
      } else if (currentState != null) {
        currentState.messageNotificationsState.troubleshootNotifications
      } else {
        false
      }
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
    val areNotificationsDisabledBySystem = Build.VERSION.SDK_INT >= 26 && (
      !NotificationChannels.getInstance().isMessageChannelEnabled ||
        !NotificationChannels.getInstance().isMessagesChannelGroupEnabled ||
        !NotificationChannels.getInstance().areNotificationsEnabled()
      )

    return !areNotificationsDisabledBySystem
  }

  class Factory(private val sharedPreferences: SharedPreferences) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(NotificationsSettingsViewModel(sharedPreferences)))
    }
  }
}
