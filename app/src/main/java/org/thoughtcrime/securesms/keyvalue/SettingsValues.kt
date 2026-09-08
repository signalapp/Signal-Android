package org.thoughtcrime.securesms.keyvalue

import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.LiveData
import org.signal.core.util.logging.Log
import org.signal.mediasend.SentMediaQuality
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.SingleLiveEvent
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.webrtc.CallDataMode
import java.util.Arrays
import java.util.Random
import kotlin.math.abs

@Suppress("DEPRECATION")
class SettingsValues internal constructor(store: KeyValueStore, context: Context) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(SettingsValues::class.java)

    const val LINK_PREVIEWS = "settings.link_previews"
    const val KEEP_MESSAGES_DURATION = "settings.keep_messages_duration"

    const val PREFER_SYSTEM_CONTACT_PHOTOS = "settings.prefer.system.contact.photos"

    private const val SIGNAL_BACKUP_DIRECTORY = "settings.signal.backup.directory"
    private const val SIGNAL_LATEST_BACKUP_DIRECTORY = "settings.signal.backup.directory,latest"
    private const val CALL_DATA_MODE = "settings.signal.call.bandwidth.mode"

    const val THREAD_TRIM_LENGTH = "pref_trim_length"
    const val THREAD_TRIM_ENABLED = "pref_trim_threads"

    const val THEME = "settings.theme"
    const val MESSAGE_FONT_SIZE = "settings.message.font.size"
    const val LANGUAGE = "settings.language"
    const val PREFER_SYSTEM_EMOJI = "settings.use.system.emoji"
    const val ENTER_KEY_SENDS = "settings.enter.key.sends"
    const val BACKUPS_ENABLED = "settings.backups.enabled"
    const val BACKUPS_SCHEDULE_HOUR = "settings.backups.schedule.hour"
    const val BACKUPS_SCHEDULE_MINUTE = "settings.backups.schedule.minute"
    const val SIGNAL_BACKUPS_SCHEDULE_HOUR = "settings.signal.backups.schedule.hour"
    const val SIGNAL_BACKUPS_SCHEDULE_MINUTE = "settings.signal.backups.schedule.minute"
    const val SMS_DELIVERY_REPORTS_ENABLED = "settings.sms.delivery.reports.enabled"
    const val WIFI_CALLING_COMPATIBILITY_MODE_ENABLED = "settings.wifi.calling.compatibility.mode.enabled"
    const val MESSAGE_NOTIFICATIONS_ENABLED = "settings.message.notifications.enabled"
    const val MESSAGE_NOTIFICATION_SOUND = "settings.message.notifications.sound"
    const val MESSAGE_VIBRATE_ENABLED = "settings.message.vibrate.enabled"
    const val MESSAGE_LED_COLOR = "settings.message.led.color"
    const val MESSAGE_LED_BLINK_PATTERN = "settings.message.led.blink"
    const val MESSAGE_IN_CHAT_SOUNDS_ENABLED = "settings.message.in.chats.sounds.enabled"
    const val MESSAGE_REPEAT_ALERTS = "settings.message.repeat.alerts"
    const val MESSAGE_NOTIFICATION_PRIVACY = "settings.message.notification.privacy"
    const val MESSAGE_NOTIFICATION_REACTION = "settings.message.notification.reaction"
    const val MESSAGE_NOTIFICATION_MUTED_CALLS = "settings.message.notifications.muted.call"
    const val MESSAGE_NOTIFICATION_MUTED_MENTIONS = "settings.message.notifications.muted.mentions"
    const val MESSAGE_NOTIFICATION_MUTED_REPLIES = "settings.message.notifications.muted.replies"
    const val UNREAD_REMINDER_ENABLED = "settings.message.notifications.unread.reminder"
    const val UNREAD_REMINDER_NEXT_NOTIFY_TIME = "settings.message.notifications.unread.reminder.next.notify.time"
    const val UNREAD_BADGE_TYPE = "settings.notifications.badge.type"
    const val INCLUDE_MUTED_IN_BADGE_COUNT = "settings.notifications.include.muted.badge.count"
    const val CALL_NOTIFICATIONS_ENABLED = "settings.call.notifications.enabled"
    const val CALL_RINGTONE = "settings.call.ringtone"
    const val CALL_VIBRATE_ENABLED = "settings.call.vibrate.enabled"
    const val NOTIFY_WHEN_CONTACT_JOINS_SIGNAL = "settings.notify.when.contact.joins.signal"
    private const val UNIVERSAL_EXPIRE_TIMER = "settings.universal.expire.timer"
    private const val SENT_MEDIA_QUALITY = "settings.sentMediaQuality"
    private const val CENSORSHIP_CIRCUMVENTION_ENABLED = "settings.censorshipCircumventionEnabled"
    private const val KEEP_MUTED_CHATS_ARCHIVED = "settings.keepMutedChatsArchived"
    private const val USE_COMPACT_NAVIGATION_BAR = "settings.useCompactNavigationBar"
    private const val THREAD_TRIM_SYNC_TO_LINKED_DEVICES = "settings.storage.syncThreadTrimDeletes"
    private const val PASSPHRASE_DISABLED = "settings.passphrase.disabled"
    private const val PASSPHRASE_TIMEOUT_ENABLED = "settings.passphrase.timeout.enabled"
    private const val PASSPHRASE_TIMEOUT = "settings.passphrase.timeout"
    private const val SCREEN_LOCK_ENABLED = "settings.screen.lock.enabled"
    private const val SCREEN_LOCK_TIMEOUT = "settings.screen.lock.timeout"
    private const val AUTOMATIC_VERIFICATION_ENABLED = "settings.automatic.verification.enabled"
    private const val FORCE_WEBSOCKET_MODE = "settings.force.websocket.mode.2"

    const val BACKUP_DEFAULT_HOUR = 2
    const val BACKUP_DEFAULT_MINUTE = 0
  }

  private val configurationSettingChanged = SingleLiveEvent<String>()

  init {
    if (!store.containsKey(SCREEN_LOCK_ENABLED) && !Environment.IS_INSTRUMENTATION) {
      migrateFromSharedPrefsV1(context)
    }
  }

  public override fun onFirstEverAppLaunch() {
    if (!store.containsKey(LINK_PREVIEWS)) {
      isLinkPreviewsEnabled = true
    }
    if (!store.containsKey(BACKUPS_SCHEDULE_HOUR)) {
      // Initialize backup time to a 5min interval between 1-5am
      setBackupSchedule(Random().nextInt(5) + 1, Random().nextInt(12) * 5)
    }
    if (!store.containsKey(SIGNAL_BACKUPS_SCHEDULE_HOUR)) {
      initSignalBackupsSchedule()
    }
  }

  public override fun getKeysToIncludeInBackup(): List<String> = listOf(
    LINK_PREVIEWS,
    KEEP_MESSAGES_DURATION,
    PREFER_SYSTEM_CONTACT_PHOTOS,
    CALL_DATA_MODE,
    THREAD_TRIM_LENGTH,
    THREAD_TRIM_ENABLED,
    LANGUAGE,
    THEME,
    MESSAGE_FONT_SIZE,
    PREFER_SYSTEM_EMOJI,
    ENTER_KEY_SENDS,
    BACKUPS_ENABLED,
    MESSAGE_NOTIFICATIONS_ENABLED,
    MESSAGE_NOTIFICATION_SOUND,
    MESSAGE_VIBRATE_ENABLED,
    MESSAGE_LED_COLOR,
    MESSAGE_LED_BLINK_PATTERN,
    MESSAGE_IN_CHAT_SOUNDS_ENABLED,
    MESSAGE_REPEAT_ALERTS,
    MESSAGE_NOTIFICATION_PRIVACY,
    CALL_NOTIFICATIONS_ENABLED,
    CALL_RINGTONE,
    CALL_VIBRATE_ENABLED,
    NOTIFY_WHEN_CONTACT_JOINS_SIGNAL,
    UNIVERSAL_EXPIRE_TIMER,
    SENT_MEDIA_QUALITY,
    KEEP_MUTED_CHATS_ARCHIVED,
    USE_COMPACT_NAVIGATION_BAR,
    THREAD_TRIM_SYNC_TO_LINKED_DEVICES,
    PASSPHRASE_DISABLED,
    PASSPHRASE_TIMEOUT_ENABLED,
    PASSPHRASE_TIMEOUT,
    SCREEN_LOCK_ENABLED,
    SCREEN_LOCK_TIMEOUT
  )

  val onConfigurationSettingChanged: LiveData<String>
    get() = configurationSettingChanged

  var isLinkPreviewsEnabled: Boolean by booleanValue(LINK_PREVIEWS, false)

  var keepMessagesDuration: KeepMessagesDuration
    get() = KeepMessagesDuration.fromId(getInteger(KEEP_MESSAGES_DURATION, 0))
    set(value) {
      putInteger(KEEP_MESSAGES_DURATION, value.id)
    }

  var isTrimByLengthEnabled: Boolean by booleanValue(THREAD_TRIM_ENABLED, false)

  var threadTrimLength: Int by integerValue(THREAD_TRIM_LENGTH, 500)

  var syncThreadTrimDeletes: Boolean
    get() {
      if (!store.containsKey(THREAD_TRIM_SYNC_TO_LINKED_DEVICES)) {
        syncThreadTrimDeletes = !isTrimByLengthEnabled && keepMessagesDuration == KeepMessagesDuration.FOREVER
      }

      return getBoolean(THREAD_TRIM_SYNC_TO_LINKED_DEVICES, true) && SignalStore.account.isPrimaryDevice
    }
    set(value) {
      putBoolean(THREAD_TRIM_SYNC_TO_LINKED_DEVICES, value)
    }

  var signalBackupDirectory: Uri?
    get() = getUri(SIGNAL_BACKUP_DIRECTORY)
    set(value) {
      putString(SIGNAL_BACKUP_DIRECTORY, value?.toString())

      // The latest directory is intentionally left in place when clearing so we can re-offer the last-used location.
      if (value != null) {
        putString(SIGNAL_LATEST_BACKUP_DIRECTORY, value.toString())
      }
    }

  val latestSignalBackupDirectory: Uri?
    get() = getUri(SIGNAL_LATEST_BACKUP_DIRECTORY)

  var isPreferSystemContactPhotos: Boolean by booleanValue(PREFER_SYSTEM_CONTACT_PHOTOS, false)

  var callDataMode: CallDataMode
    get() = CallDataMode.fromCode(getInteger(CALL_DATA_MODE, CallDataMode.HIGH_ALWAYS.code))
    set(value) {
      putInteger(CALL_DATA_MODE, value.code)
    }

  var theme: Theme
    get() = Theme.deserialize(getString(THEME, TextSecurePreferences.getTheme(AppDependencies.application)))
    set(value) {
      putString(THEME, value.serialize())
      configurationSettingChanged.postValue(THEME)
    }

  var messageFontSize: Int
    get() = getInteger(MESSAGE_FONT_SIZE, TextSecurePreferences.getMessageBodyTextSize(AppDependencies.application))
    set(value) {
      putInteger(MESSAGE_FONT_SIZE, value)
    }

  fun getMessageQuoteFontSize(context: Context): Int {
    val currentMessageSize = messageFontSize
    val possibleMessageSizes = context.resources.getIntArray(R.array.pref_message_font_size_values)
    val possibleQuoteSizes = context.resources.getIntArray(R.array.pref_message_font_quote_size_values)
    var sizeIndex = Arrays.binarySearch(possibleMessageSizes, currentMessageSize)

    if (sizeIndex < 0) {
      val newSize = possibleMessageSizes.minBy { abs(it - currentMessageSize) }
      Log.w(TAG, "Using non-standard font size of $currentMessageSize. Closest match was $newSize. Updating.")

      messageFontSize = newSize
      sizeIndex = Arrays.binarySearch(possibleMessageSizes, newSize)
    }

    return possibleQuoteSizes[sizeIndex]
  }

  var language: String
    get() = TextSecurePreferences.getLanguage(AppDependencies.application)
    set(value) {
      TextSecurePreferences.setLanguage(AppDependencies.application, value)
      configurationSettingChanged.postValue(LANGUAGE)
    }

  var isPreferSystemEmoji: Boolean
    get() = getBoolean(PREFER_SYSTEM_EMOJI, TextSecurePreferences.isSystemEmojiPreferred(AppDependencies.application))
    set(value) {
      putBoolean(PREFER_SYSTEM_EMOJI, value)
    }

  var isEnterKeySends: Boolean
    get() = getBoolean(ENTER_KEY_SENDS, TextSecurePreferences.isEnterSendsEnabled(AppDependencies.application))
    set(value) {
      putBoolean(ENTER_KEY_SENDS, value)
    }

  var isBackupEnabled: Boolean
    get() = getBoolean(BACKUPS_ENABLED, TextSecurePreferences.isBackupEnabled(AppDependencies.application))
    set(value) {
      putBoolean(BACKUPS_ENABLED, value)
    }

  val backupHour: Int
    get() = getInteger(BACKUPS_SCHEDULE_HOUR, BACKUP_DEFAULT_HOUR)

  val backupMinute: Int
    get() = getInteger(BACKUPS_SCHEDULE_MINUTE, BACKUP_DEFAULT_MINUTE)

  val signalBackupHour: Int
    get() {
      val hour = getInteger(SIGNAL_BACKUPS_SCHEDULE_HOUR, -1)
      return if (hour < 0) {
        initSignalBackupsSchedule()
        getInteger(SIGNAL_BACKUPS_SCHEDULE_HOUR, BACKUP_DEFAULT_HOUR)
      } else {
        hour
      }
    }

  val signalBackupMinute: Int
    get() {
      val minute = getInteger(SIGNAL_BACKUPS_SCHEDULE_MINUTE, -1)
      return if (minute < 0) {
        initSignalBackupsSchedule()
        getInteger(SIGNAL_BACKUPS_SCHEDULE_MINUTE, BACKUP_DEFAULT_MINUTE)
      } else {
        minute
      }
    }

  fun setBackupSchedule(hour: Int, minute: Int) {
    putInteger(BACKUPS_SCHEDULE_HOUR, hour)
    putInteger(BACKUPS_SCHEDULE_MINUTE, minute)
  }

  private fun initSignalBackupsSchedule() {
    setSignalBackupSchedule(Random().nextInt(5) + 1, Random().nextInt(12) * 5)
  }

  fun setSignalBackupSchedule(hour: Int, minute: Int) {
    putInteger(SIGNAL_BACKUPS_SCHEDULE_HOUR, hour)
    putInteger(SIGNAL_BACKUPS_SCHEDULE_MINUTE, minute)
  }

  var isSmsDeliveryReportsEnabled: Boolean
    get() = getBoolean(SMS_DELIVERY_REPORTS_ENABLED, TextSecurePreferences.isSmsDeliveryReportsEnabled(AppDependencies.application))
    set(value) {
      putBoolean(SMS_DELIVERY_REPORTS_ENABLED, value)
    }

  var isWifiCallingCompatibilityModeEnabled: Boolean
    get() = getBoolean(WIFI_CALLING_COMPATIBILITY_MODE_ENABLED, TextSecurePreferences.isWifiSmsEnabled(AppDependencies.application))
    set(value) {
      putBoolean(WIFI_CALLING_COMPATIBILITY_MODE_ENABLED, value)
    }

  var isMessageNotificationsEnabled: Boolean
    get() = getBoolean(MESSAGE_NOTIFICATIONS_ENABLED, TextSecurePreferences.isNotificationsEnabled(AppDependencies.application))
    set(value) {
      putBoolean(MESSAGE_NOTIFICATIONS_ENABLED, value)
    }

  var messageNotificationSound: Uri
    get() {
      var result = getString(MESSAGE_NOTIFICATION_SOUND, TextSecurePreferences.getNotificationRingtone(AppDependencies.application).toString())

      if (result.startsWith("file:")) {
        result = Settings.System.DEFAULT_NOTIFICATION_URI.toString()
      }

      return Uri.parse(result)
    }
    set(value) {
      putString(MESSAGE_NOTIFICATION_SOUND, value.toString())
    }

  var isMessageVibrateEnabled: Boolean
    get() = getBoolean(MESSAGE_VIBRATE_ENABLED, TextSecurePreferences.isNotificationVibrateEnabled(AppDependencies.application))
    set(value) {
      putBoolean(MESSAGE_VIBRATE_ENABLED, value)
    }

  var messageLedColor: String
    get() = getString(MESSAGE_LED_COLOR, TextSecurePreferences.getNotificationLedColor(AppDependencies.application))
    set(value) {
      putString(MESSAGE_LED_COLOR, value)
    }

  var messageLedBlinkPattern: String
    get() = getString(MESSAGE_LED_BLINK_PATTERN, TextSecurePreferences.getNotificationLedPattern(AppDependencies.application))
    set(value) {
      putString(MESSAGE_LED_BLINK_PATTERN, value)
    }

  var isMessageNotificationsInChatSoundsEnabled: Boolean
    get() = getBoolean(MESSAGE_IN_CHAT_SOUNDS_ENABLED, TextSecurePreferences.isInThreadNotifications(AppDependencies.application))
    set(value) {
      putBoolean(MESSAGE_IN_CHAT_SOUNDS_ENABLED, value)
    }

  var messageNotificationsRepeatAlerts: Int
    get() = getInteger(MESSAGE_REPEAT_ALERTS, TextSecurePreferences.getRepeatAlertsCount(AppDependencies.application))
    set(value) {
      putInteger(MESSAGE_REPEAT_ALERTS, value)
    }

  var messageNotificationsPrivacy: NotificationPrivacyPreference
    get() = NotificationPrivacyPreference(getString(MESSAGE_NOTIFICATION_PRIVACY, TextSecurePreferences.getNotificationPrivacy(AppDependencies.application).toString()))
    set(value) {
      putString(MESSAGE_NOTIFICATION_PRIVACY, value.toString())
    }

  var reactionNotifications: Boolean by booleanValue(MESSAGE_NOTIFICATION_REACTION, true)

  var allowCallsWhileMuted: Boolean by booleanValue(MESSAGE_NOTIFICATION_MUTED_CALLS, false)

  var allowMentionsWhileMuted: Boolean by booleanValue(MESSAGE_NOTIFICATION_MUTED_MENTIONS, true)

  var allowRepliesWhileMuted: Boolean by booleanValue(MESSAGE_NOTIFICATION_MUTED_REPLIES, true)

  var unreadReminderEnabled: Boolean by booleanValue(UNREAD_REMINDER_ENABLED, true)

  // TODO(michelle): Use in unread job
  var unreadReminderNextNotifyTime: Long by longValue(UNREAD_REMINDER_NEXT_NOTIFY_TIME, 0L)

  val unreadBadgeType: UnreadBadgeType
    get() = UnreadBadgeType.deserialize(getInteger(UNREAD_BADGE_TYPE, UnreadBadgeType.UNREAD_MESSAGES.serialize()))

  /** Only used to round trip ios/desktop settings. */
  fun setUnreadBadgeType(type: Int) {
    putInteger(UNREAD_BADGE_TYPE, type)
  }

  val includeMutedInBadgeCount: Boolean?
    get() = if (store.containsKey(INCLUDE_MUTED_IN_BADGE_COUNT)) getBoolean(INCLUDE_MUTED_IN_BADGE_COUNT, false) else null

  /** Only used to round trip ios/desktop settings. */
  fun setIncludeMutedInBadgeCount(include: Boolean) {
    putBoolean(INCLUDE_MUTED_IN_BADGE_COUNT, include)
  }

  var isCallNotificationsEnabled: Boolean
    get() = getBoolean(CALL_NOTIFICATIONS_ENABLED, TextSecurePreferences.isCallNotificationsEnabled(AppDependencies.application))
    set(value) {
      putBoolean(CALL_NOTIFICATIONS_ENABLED, value)
    }

  var callRingtone: Uri
    get() {
      var result = getString(CALL_RINGTONE, TextSecurePreferences.getCallNotificationRingtone(AppDependencies.application).toString())

      if (result != null && result.startsWith("file:")) {
        result = Settings.System.DEFAULT_RINGTONE_URI.toString()
      }

      return Uri.parse(result)
    }
    set(value) {
      putString(CALL_RINGTONE, value.toString())
    }

  var isCallVibrateEnabled: Boolean
    get() = getBoolean(CALL_VIBRATE_ENABLED, TextSecurePreferences.isCallNotificationVibrateEnabled(AppDependencies.application))
    set(value) {
      putBoolean(CALL_VIBRATE_ENABLED, value)
    }

  var isNotifyWhenContactJoinsSignal: Boolean
    get() = getBoolean(NOTIFY_WHEN_CONTACT_JOINS_SIGNAL, TextSecurePreferences.isNewContactsNotificationEnabled(AppDependencies.application))
    set(value) {
      putBoolean(NOTIFY_WHEN_CONTACT_JOINS_SIGNAL, value)
    }

  var universalExpireTimer: Int by integerValue(UNIVERSAL_EXPIRE_TIMER, 0)

  var sentMediaQuality: SentMediaQuality
    get() = SentMediaQuality.fromCode(getInteger(SENT_MEDIA_QUALITY, SentMediaQuality.STANDARD.code))
    set(value) {
      putInteger(SENT_MEDIA_QUALITY, value.code)
    }

  val censorshipCircumventionEnabled: CensorshipCircumventionEnabled
    get() = CensorshipCircumventionEnabled.deserialize(getInteger(CENSORSHIP_CIRCUMVENTION_ENABLED, CensorshipCircumventionEnabled.DEFAULT.serialize()))

  fun setCensorshipCircumventionEnabled(enabled: Boolean) {
    Log.i(TAG, "Changing censorship circumvention state to: $enabled", Throwable())
    putInteger(CENSORSHIP_CIRCUMVENTION_ENABLED, if (enabled) CensorshipCircumventionEnabled.ENABLED.serialize() else CensorshipCircumventionEnabled.DISABLED.serialize())
  }

  var keepMutedChatsArchived: Boolean by booleanValue(KEEP_MUTED_CHATS_ARCHIVED, false)

  var useCompactNavigationBar: Boolean by booleanValue(USE_COMPACT_NAVIGATION_BAR, false)

  var passphraseDisabled: Boolean by booleanValue(PASSPHRASE_DISABLED, true)

  var passphraseTimeoutEnabled: Boolean by booleanValue(PASSPHRASE_TIMEOUT_ENABLED, false)

  var passphraseTimeout: Int by integerValue(PASSPHRASE_TIMEOUT, 0)

  var screenLockEnabled: Boolean by booleanValue(SCREEN_LOCK_ENABLED, false)

  var screenLockTimeout: Long by longValue(SCREEN_LOCK_TIMEOUT, 0)

  var automaticVerificationEnabled: Boolean
    get() = getBoolean(AUTOMATIC_VERIFICATION_ENABLED, true)
    set(value) {
      Log.i(TAG, "Setting key transparency enabled to $value")
      putBoolean(AUTOMATIC_VERIFICATION_ENABLED, value)
    }

  var forceWebsocketMode: ForceWebsocketMode
    get() {
      return if (store.containsKey(FORCE_WEBSOCKET_MODE)) {
        ForceWebsocketMode.deserialize(getInteger(FORCE_WEBSOCKET_MODE, ForceWebsocketMode.DISABLED.serialize()))
      } else if (getBoolean(FORCE_WEBSOCKET_MODE, false)) {
        ForceWebsocketMode.ENABLED_BY_USER
      } else {
        ForceWebsocketMode.DISABLED
      }
    }
    set(value) {
      putInteger(FORCE_WEBSOCKET_MODE, value.serialize())
    }

  private fun getUri(key: String): Uri? {
    val uri = getString(key, "")

    return if (uri.isNullOrEmpty()) {
      null
    } else {
      Uri.parse(uri)
    }
  }

  private fun migrateFromSharedPrefsV1(context: Context) {
    Log.i(TAG, "[V1] Migrating screen lock values from shared prefs.")

    putBoolean(PASSPHRASE_DISABLED, TextSecurePreferences.getBooleanPreference(context, "pref_disable_passphrase", true))
    putBoolean(PASSPHRASE_TIMEOUT_ENABLED, TextSecurePreferences.getBooleanPreference(context, "pref_timeout_passphrase", false))
    putInteger(PASSPHRASE_TIMEOUT, TextSecurePreferences.getIntegerPreference(context, "pref_timeout_interval", 5 * 60))
    putBoolean(SCREEN_LOCK_ENABLED, TextSecurePreferences.getBooleanPreference(context, "pref_android_screen_lock", false))
    putLong(SCREEN_LOCK_TIMEOUT, TextSecurePreferences.getLongPreference(context, "pref_android_screen_lock_timeout", 0))
  }

  enum class CensorshipCircumventionEnabled(private val value: Int) {
    DEFAULT(0),
    ENABLED(1),
    DISABLED(2);

    fun serialize(): Int = value

    companion object {
      @JvmStatic
      fun deserialize(value: Int): CensorshipCircumventionEnabled {
        return entries.firstOrNull { it.value == value } ?: throw IllegalArgumentException("Bad value: $value")
      }
    }
  }

  enum class ForceWebsocketMode(private val value: Int) {
    DISABLED(0),
    ENABLED_BY_USER(1),
    ENABLED_AUTOMATICALLY(2);

    val isEnabled: Boolean
      get() = this != DISABLED

    fun serialize(): Int = value

    companion object {
      @JvmStatic
      fun deserialize(value: Int): ForceWebsocketMode {
        return entries.firstOrNull { it.value == value } ?: throw IllegalArgumentException("Bad value: $value")
      }
    }
  }

  enum class Theme(private val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun serialize(): String = value

    companion object {
      @JvmStatic
      fun deserialize(value: String): Theme {
        return entries.firstOrNull { it.value == value } ?: throw IllegalArgumentException("Unrecognized value $value")
      }
    }
  }

  enum class UnreadBadgeType(private val value: Int) {
    UNKNOWN_BADGE_TYPE(0),
    UNREAD_MESSAGES(1),
    UNREAD_CHATS(2);

    fun serialize(): Int = value

    companion object {
      @JvmStatic
      fun deserialize(value: Int): UnreadBadgeType {
        return entries.firstOrNull { it.value == value } ?: throw IllegalArgumentException("Bad value: $value")
      }
    }
  }
}
