/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications

import android.media.Ringtone
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.signal.appsettings.notifications.CallNotificationsState
import org.signal.appsettings.notifications.MessageNotificationsState
import org.signal.appsettings.notifications.NotificationsSettingsState
import org.signal.appsettings.notifications.SoundSummary
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.DeviceSpecificNotificationConfig
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.SlowNotificationHeuristics
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.RingtoneUtil

/**
 * All of the storage and platform access behind [NotificationsSettingsViewModel].
 */
object NotificationsSettingsRepository {

  private val TAG = Log.tag(NotificationsSettingsRepository::class)

  /** Looking a sound's name up queries the media store, so each sound is only looked up once. */
  private val soundSummaries = mutableMapOf<Uri, SoundSummary>()

  /** Emits whenever the local user's recipient changes, such as when settings sync in from another device. */
  fun observeSelfChanges(): Flow<Unit> {
    return Recipient.self().live().observable().asFlow().map { }
  }

  /** Copies the sound and vibration the message notification channel currently has, which may have been changed in the system's settings. */
  fun syncFromNotificationChannel() {
    if (NotificationChannels.supported()) {
      SignalStore.settings.messageNotificationSound = NotificationChannels.getInstance().messageRingtone
      SignalStore.settings.isMessageVibrateEnabled = NotificationChannels.getInstance().messageVibrate
    }
  }

  /**
   * Reads the current settings into [currentState], keeping its dialog, snackbar and troubleshooting flag, which aren't
   * stored settings.
   */
  fun getState(currentState: NotificationsSettingsState): NotificationsSettingsState {
    val canEnableNotifications = canEnableNotifications()

    return currentState.copy(
      messageNotificationsState = MessageNotificationsState(
        notificationsEnabled = SignalStore.settings.isMessageNotificationsEnabled && canEnableNotifications,
        canEnableNotifications = canEnableNotifications,
        sound = getSoundSummary(SignalStore.settings.messageNotificationSound),
        vibrateEnabled = SignalStore.settings.isMessageVibrateEnabled,
        ledColor = SignalStore.settings.messageLedColor,
        ledBlink = SignalStore.settings.messageLedBlinkPattern,
        inChatSoundsEnabled = SignalStore.settings.isMessageNotificationsInChatSoundsEnabled,
        repeatAlerts = SignalStore.settings.messageNotificationsRepeatAlerts,
        messagePrivacy = SignalStore.settings.messageNotificationsPrivacy.toString(),
        priority = SignalStore.settings.messageNotificationPriority,
        troubleshootNotifications = currentState.messageNotificationsState.troubleshootNotifications,
        reactionNotificationEnabled = SignalStore.settings.reactionNotifications,
        unreadReminderEnabled = SignalStore.settings.unreadReminderEnabled,
        allowCallsWhileMuted = SignalStore.settings.allowCallsWhileMuted,
        allowMentionsWhileMuted = SignalStore.settings.allowMentionsWhileMuted,
        allowRepliesWhileMuted = SignalStore.settings.allowRepliesWhileMuted
      ),
      callNotificationsState = CallNotificationsState(
        notificationsEnabled = SignalStore.settings.isCallNotificationsEnabled && canEnableNotifications,
        canEnableNotifications = canEnableNotifications,
        ringtone = getSoundSummary(SignalStore.settings.callRingtone),
        vibrateEnabled = SignalStore.settings.isCallVibrateEnabled
      ),
      notifyWhenContactJoinsSignal = SignalStore.settings.isNotifyWhenContactJoinsSignal
    )
  }

  /** Whether notifications look delayed enough to offer troubleshooting. Slow to work out, and not thread-safe. */
  suspend fun isTroubleshootingRecommended(): Boolean {
    return withContext(SignalDispatchers.Default) {
      (SlowNotificationHeuristics.isBatteryOptimizationsOn() && SlowNotificationHeuristics.isHavingDelayedNotifications()) ||
        SlowNotificationHeuristics.getDeviceSpecificShowCondition() == DeviceSpecificNotificationConfig.ShowCondition.ALWAYS
    }
  }

  fun setMessageNotificationsEnabled(enabled: Boolean) {
    SignalStore.settings.isMessageNotificationsEnabled = enabled
  }

  fun setMessageNotificationsSound(sound: Uri?) {
    val messageSound = sound ?: Uri.EMPTY
    SignalStore.settings.messageNotificationSound = messageSound
    NotificationChannels.getInstance().updateMessageRingtone(messageSound)
  }

  fun setMessageNotificationVibration(enabled: Boolean) {
    SignalStore.settings.isMessageVibrateEnabled = enabled
    NotificationChannels.getInstance().updateMessageVibrate(enabled)
  }

  fun setMessageNotificationLedColor(color: String) {
    SignalStore.settings.messageLedColor = color
    NotificationChannels.getInstance().updateMessagesLedColor(color)
  }

  fun setMessageNotificationLedBlink(blink: String) {
    SignalStore.settings.messageLedBlinkPattern = blink
  }

  fun setMessageNotificationInChatSoundsEnabled(enabled: Boolean) {
    SignalStore.settings.isMessageNotificationsInChatSoundsEnabled = enabled
  }

  fun setMessageRepeatAlerts(repeats: Int) {
    SignalStore.settings.messageNotificationsRepeatAlerts = repeats
  }

  fun setMessageNotificationPrivacy(preference: String) {
    SignalStore.settings.messageNotificationsPrivacy = NotificationPrivacyPreference(preference)
  }

  fun setMessageNotificationPriority(priority: Int) {
    SignalStore.settings.messageNotificationPriority = priority
  }

  fun setCallNotificationsEnabled(enabled: Boolean) {
    SignalStore.settings.isCallNotificationsEnabled = enabled
  }

  fun setCallRingtone(ringtone: Uri?) {
    SignalStore.settings.callRingtone = ringtone ?: Uri.EMPTY
  }

  fun setCallVibrateEnabled(enabled: Boolean) {
    SignalStore.settings.isCallVibrateEnabled = enabled
  }

  suspend fun setNotifyWhenContactJoinsSignal(enabled: Boolean) {
    SignalStore.settings.isNotifyWhenContactJoinsSignal = enabled
    markSelfNeedsSync()
  }

  suspend fun setReactionNotificationsEnabled(enabled: Boolean) {
    SignalStore.settings.reactionNotifications = enabled
    markSelfNeedsSync()
  }

  suspend fun setUnreadReminderEnabled(enabled: Boolean) {
    SignalStore.settings.unreadReminderEnabled = enabled
    markSelfNeedsSync()
  }

  /** Puts every notification setting back to its default, including each chat's own notification settings. */
  suspend fun resetSettings() {
    Log.i(TAG, "Resetting all notifications.")
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

    withContext(SignalDispatchers.Default) {
      SignalDatabase.recipients.resetAllChatNotificationSettings()
    }

    markSelfNeedsSync()
  }

  private suspend fun markSelfNeedsSync() {
    withContext(SignalDispatchers.Default) {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  private fun getSoundSummary(uri: Uri): SoundSummary {
    return synchronized(soundSummaries) {
      soundSummaries.getOrPut(uri) { lookUpSoundSummary(uri) }
    }
  }

  private fun lookUpSoundSummary(uri: Uri): SoundSummary {
    if (uri.toString().isBlank()) {
      return SoundSummary.Silent
    }

    val context = AppDependencies.application
    val tone: Ringtone = RingtoneUtil.getRingtone(context, uri) ?: return SoundSummary.Default

    return try {
      tone.getTitle(context)?.let { SoundSummary.Named(it) } ?: SoundSummary.Unknown
    } catch (e: SecurityException) {
      Log.w(TAG, "Unable to get title for ringtone", e)
      SoundSummary.Unknown
    }
  }

  private fun canEnableNotifications(): Boolean {
    val areNotificationsDisabledBySystem = Build.VERSION.SDK_INT >= 26 &&
      (
        !NotificationChannels.getInstance().isMessageChannelEnabled ||
          !NotificationChannels.getInstance().isMessagesChannelGroupEnabled ||
          !NotificationChannels.getInstance().areNotificationsEnabled()
        )

    return !areNotificationsDisabledBySystem
  }
}
