package org.thoughtcrime.securesms.keyvalue

import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.annotation.ArrayRes
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import org.signal.core.util.StringStringSerializer
import org.signal.core.util.logging.Log
import org.signal.mediasend.SentMediaQuality
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.databaseprotos.SignalStoreList
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.SingleLiveEvent
import org.thoughtcrime.securesms.webrtc.CallDataMode
import java.util.Arrays
import java.util.Random
import kotlin.math.abs

@Suppress("DEPRECATION")
class SettingsValues internal constructor(store: KeyValueStore, private val context: Context) : SignalStoreValues(store) {

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
    private const val MESSAGE_LED_BLINK_PATTERN_CUSTOM = "settings.message.led.blink.custom"
    private const val MESSAGE_NOTIFICATION_PRIORITY = "settings.message.notification.priority"
    private const val MEDIA_KEYBOARD_MODE = "settings.media.keyboard.mode"
    private const val LOCAL_BACKUP_NEXT_TIME = "settings.backups.next.time"
    private const val MEDIA_DOWNLOAD_MOBILE = "settings.media.download.mobile"
    private const val MEDIA_DOWNLOAD_WIFI = "settings.media.download.wifi"
    private const val MEDIA_DOWNLOAD_ROAMING = "settings.media.download.roaming"
    private const val AUTOPLAY_STICKERS_AND_GIFS = "settings.chats.autoplayStickersAndGifs"

    const val SCREEN_SECURITY_ENABLED = "settings.screen.security.enabled"
    const val INCOGNITO_KEYBOARD_ENABLED = "settings.incognito.keyboard.enabled"
    const val READ_RECEIPTS_ENABLED = "settings.read.receipts.enabled"
    const val TYPING_INDICATORS_ENABLED = "settings.typing.indicators.enabled"
    const val SHOW_UNIDENTIFIED_DELIVERY_INDICATORS = "settings.show.unidentified.delivery.indicators"
    const val UNIVERSAL_UNIDENTIFIED_ACCESS = "settings.universal.unidentified.access"
    const val ALWAYS_RELAY_CALLS = "settings.always.relay.calls"

    const val BACKUP_DEFAULT_HOUR = 2
    const val BACKUP_DEFAULT_MINUTE = 0
  }

  private val configurationSettingChanged = SingleLiveEvent<String>()

  init {
    if (!store.containsKey(SCREEN_LOCK_ENABLED) && !Environment.IS_INSTRUMENTATION) {
      migrateFromSharedPrefsV1(context)
    }

    if (!store.containsKey(SCREEN_SECURITY_ENABLED) && !Environment.IS_INSTRUMENTATION) {
      migrateFromSharedPrefsV2(context)
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
    SCREEN_LOCK_TIMEOUT,
    SCREEN_SECURITY_ENABLED,
    INCOGNITO_KEYBOARD_ENABLED,
    READ_RECEIPTS_ENABLED,
    TYPING_INDICATORS_ENABLED,
    SHOW_UNIDENTIFIED_DELIVERY_INDICATORS,
    UNIVERSAL_UNIDENTIFIED_ACCESS,
    ALWAYS_RELAY_CALLS,
    MEDIA_DOWNLOAD_MOBILE,
    MEDIA_DOWNLOAD_WIFI,
    MEDIA_DOWNLOAD_ROAMING,
    AUTOPLAY_STICKERS_AND_GIFS
  )

  val onConfigurationSettingChanged: LiveData<String>
    get() = configurationSettingChanged

  var isLinkPreviewsEnabled: Boolean by booleanValue(LINK_PREVIEWS, false)

  var isAutoplayStickersAndGifsEnabled: Boolean by booleanValue(AUTOPLAY_STICKERS_AND_GIFS, true)

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
    get() = Theme.deserialize(getString(THEME, if (DynamicTheme.systemThemeAvailable()) "system" else "light"))
    set(value) {
      putString(THEME, value.serialize())
      configurationSettingChanged.postValue(THEME)
    }

  var messageFontSize: Int by integerValue(MESSAGE_FONT_SIZE, 16)

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

  var isPreferSystemEmoji: Boolean by booleanValue(PREFER_SYSTEM_EMOJI, false)

  var isEnterKeySends: Boolean by booleanValue(ENTER_KEY_SENDS, false)

  var isBackupEnabled: Boolean by booleanValue(BACKUPS_ENABLED, false)

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

  var localBackupNextTime: Long by longValue(LOCAL_BACKUP_NEXT_TIME, -1)

  var isSmsDeliveryReportsEnabled: Boolean by booleanValue(SMS_DELIVERY_REPORTS_ENABLED, false)

  var isWifiCallingCompatibilityModeEnabled: Boolean by booleanValue(WIFI_CALLING_COMPATIBILITY_MODE_ENABLED, false)

  var isMessageNotificationsEnabled: Boolean by booleanValue(MESSAGE_NOTIFICATIONS_ENABLED, true)

  var messageNotificationSound: Uri
    get() {
      var result = getString(MESSAGE_NOTIFICATION_SOUND, Settings.System.DEFAULT_NOTIFICATION_URI.toString())

      if (result.startsWith("file:")) {
        result = Settings.System.DEFAULT_NOTIFICATION_URI.toString()
      }

      return Uri.parse(result)
    }
    set(value) {
      putString(MESSAGE_NOTIFICATION_SOUND, value.toString())
    }

  var isMessageVibrateEnabled: Boolean by booleanValue(MESSAGE_VIBRATE_ENABLED, true)

  var messageLedColor: String by stringValue(MESSAGE_LED_COLOR, "blue")

  var messageLedBlinkPattern: String by stringValue(MESSAGE_LED_BLINK_PATTERN, "500,2000")

  val messageLedBlinkPatternCustom: String
    get() = getString(MESSAGE_LED_BLINK_PATTERN_CUSTOM, "500,2000")

  var isMessageNotificationsInChatSoundsEnabled: Boolean by booleanValue(MESSAGE_IN_CHAT_SOUNDS_ENABLED, true)

  var messageNotificationsRepeatAlerts: Int by integerValue(MESSAGE_REPEAT_ALERTS, 0)

  var messageNotificationPriority: Int by integerValue(MESSAGE_NOTIFICATION_PRIORITY, NotificationCompat.PRIORITY_HIGH)

  var messageNotificationsPrivacy: NotificationPrivacyPreference
    get() = NotificationPrivacyPreference(getString(MESSAGE_NOTIFICATION_PRIVACY, "all"))
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

  var isCallNotificationsEnabled: Boolean by booleanValue(CALL_NOTIFICATIONS_ENABLED, true)

  var callRingtone: Uri
    get() {
      var result = getString(CALL_RINGTONE, Settings.System.DEFAULT_RINGTONE_URI.toString())

      if (result != null && result.startsWith("file:")) {
        result = Settings.System.DEFAULT_RINGTONE_URI.toString()
      }

      return Uri.parse(result)
    }
    set(value) {
      putString(CALL_RINGTONE, value.toString())
    }

  var isCallVibrateEnabled: Boolean by booleanValue(CALL_VIBRATE_ENABLED, true)

  var isNotifyWhenContactJoinsSignal: Boolean by booleanValue(NOTIFY_WHEN_CONTACT_JOINS_SIGNAL, false)

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

  var isScreenSecurityEnabled: Boolean by booleanValue(SCREEN_SECURITY_ENABLED, false)

  var isIncognitoKeyboardEnabled: Boolean by booleanValue(INCOGNITO_KEYBOARD_ENABLED, false)

  var isReadReceiptsEnabled: Boolean by booleanValue(READ_RECEIPTS_ENABLED, false)

  var isTypingIndicatorsEnabled: Boolean by booleanValue(TYPING_INDICATORS_ENABLED, false)

  var isShowUnidentifiedDeliveryIndicatorsEnabled: Boolean by booleanValue(SHOW_UNIDENTIFIED_DELIVERY_INDICATORS, false)

  var isUniversalUnidentifiedAccess: Boolean by booleanValue(UNIVERSAL_UNIDENTIFIED_ACCESS, false)

  var isTurnOnly: Boolean by booleanValue(ALWAYS_RELAY_CALLS, false)

  var automaticVerificationEnabled: Boolean
    get() = getBoolean(AUTOMATIC_VERIFICATION_ENABLED, true)
    set(value) {
      Log.i(TAG, "Setting key transparency enabled to $value")
      putBoolean(AUTOMATIC_VERIFICATION_ENABLED, value)
    }

  var forceWebsocketMode: ForceWebsocketMode
    get() {
      return if (store.containsKey(FORCE_WEBSOCKET_MODE)) {
        ForceWebsocketMode.deserialize(getInteger(FORCE_WEBSOCKET_MODE, ForceWebsocketMode.DISABLED_AUTOMATICALLY.serialize()))
      } else if (getBoolean(FORCE_WEBSOCKET_MODE, false)) {
        ForceWebsocketMode.ENABLED_BY_USER
      } else {
        ForceWebsocketMode.DISABLED_AUTOMATICALLY
      }
    }
    set(value) {
      putInteger(FORCE_WEBSOCKET_MODE, value.serialize())
    }

  var mediaKeyboardMode: MediaKeyboardMode
    get() = MediaKeyboardMode.valueOf(getString(MEDIA_KEYBOARD_MODE, MediaKeyboardMode.EMOJI.name))
    set(value) {
      putString(MEDIA_KEYBOARD_MODE, value.name)
    }

  var mobileMediaDownloadAllowed: Set<String>
    get() = getMediaDownloadAllowed(MEDIA_DOWNLOAD_MOBILE, R.array.pref_media_download_mobile_data_default)
    set(value) {
      setMediaDownloadAllowed(MEDIA_DOWNLOAD_MOBILE, value)
    }

  var wifiMediaDownloadAllowed: Set<String>
    get() = getMediaDownloadAllowed(MEDIA_DOWNLOAD_WIFI, R.array.pref_media_download_wifi_default)
    set(value) {
      setMediaDownloadAllowed(MEDIA_DOWNLOAD_WIFI, value)
    }

  var roamingMediaDownloadAllowed: Set<String>
    get() = getMediaDownloadAllowed(MEDIA_DOWNLOAD_ROAMING, R.array.pref_media_download_roaming_default)
    set(value) {
      setMediaDownloadAllowed(MEDIA_DOWNLOAD_ROAMING, value)
    }

  private fun getMediaDownloadAllowed(key: String, @ArrayRes defaultValuesRes: Int): Set<String> {
    if (!store.containsKey(key)) {
      return context.resources.getStringArray(defaultValuesRes).toSet()
    }

    return getList(key, StringStringSerializer).requireNoNulls().toSet()
  }

  private fun setMediaDownloadAllowed(key: String, allowed: Set<String>) {
    putList(key, allowed.toList(), StringStringSerializer)
  }

  private fun getUri(key: String): Uri? {
    val uri = getString(key, "")

    return if (uri.isNullOrEmpty()) {
      null
    } else {
      Uri.parse(uri)
    }
  }

  /**
   * V1 backups made before these settings moved into this store carry them as shared prefs rather than key-values. The shared-prefs
   * migration has already run by the time a restore happens, so the restored prefs need to be re-read afterwards or the user silently
   * loses their privacy and security choices.
   */
  fun restoreLegacySharedPrefsAfterBackupRestore() {
    Log.i(TAG, "Restoring legacy privacy settings from shared prefs.")

    val writer = store.beginWrite()

    val booleanKeys = mapOf(
      "pref_screen_security" to SCREEN_SECURITY_ENABLED,
      "pref_incognito_keyboard" to INCOGNITO_KEYBOARD_ENABLED,
      "pref_read_receipts" to READ_RECEIPTS_ENABLED,
      "pref_typing_indicators" to TYPING_INDICATORS_ENABLED,
      "pref_show_unidentifed_delivery_indicators" to SHOW_UNIDENTIFIED_DELIVERY_INDICATORS,
      "pref_universal_unidentified_access" to UNIVERSAL_UNIDENTIFIED_ACCESS,
      "pref_turn_only" to ALWAYS_RELAY_CALLS
    )

    for ((legacyKey, key) in booleanKeys) {
      if (LegacySharedPrefs.contains(context, legacyKey)) {
        writer.putBoolean(key, LegacySharedPrefs.getBoolean(context, legacyKey, false))
      }
    }

    if (LegacySharedPrefs.contains(context, "pref_notification_privacy")) {
      writer.putString(MESSAGE_NOTIFICATION_PRIVACY, LegacySharedPrefs.getString(context, "pref_notification_privacy", "all"))
    }

    writer.commit()
  }

  private fun migrateFromSharedPrefsV1(context: Context) {
    Log.i(TAG, "[V1] Migrating screen lock values from shared prefs.")

    putBoolean(PASSPHRASE_DISABLED, LegacySharedPrefs.getBoolean(context, "pref_disable_passphrase", true))
    putBoolean(PASSPHRASE_TIMEOUT_ENABLED, LegacySharedPrefs.getBoolean(context, "pref_timeout_passphrase", false))
    putInteger(PASSPHRASE_TIMEOUT, LegacySharedPrefs.getInteger(context, "pref_timeout_interval", 5 * 60))
    putBoolean(SCREEN_LOCK_ENABLED, LegacySharedPrefs.getBoolean(context, "pref_android_screen_lock", false))
    putLong(SCREEN_LOCK_TIMEOUT, LegacySharedPrefs.getLong(context, "pref_android_screen_lock_timeout", 0))
  }

  /**
   * These settings used to live in shared prefs. Some of them were already mirrored into this store, but with a shared-prefs read as
   * their default, so for those we only migrate the ones this store doesn't already have a value for.
   */
  private fun migrateFromSharedPrefsV2(context: Context) {
    Log.i(TAG, "[V2] Migrating settings from shared prefs.")

    val writer = store.beginWrite()

    if (!store.containsKey(THEME)) {
      writer.putString(THEME, LegacySharedPrefs.getString(context, "pref_theme", if (DynamicTheme.systemThemeAvailable()) "system" else "light"))
    }
    if (!store.containsKey(MESSAGE_FONT_SIZE)) {
      writer.putInteger(MESSAGE_FONT_SIZE, LegacySharedPrefs.getIntegerFromString(context, "pref_message_body_text_size", 16))
    }
    if (!store.containsKey(PREFER_SYSTEM_EMOJI)) {
      writer.putBoolean(PREFER_SYSTEM_EMOJI, LegacySharedPrefs.getBoolean(context, "pref_system_emoji", false))
    }
    if (!store.containsKey(ENTER_KEY_SENDS)) {
      writer.putBoolean(ENTER_KEY_SENDS, LegacySharedPrefs.getBoolean(context, "pref_enter_sends", false))
    }
    if (!store.containsKey(BACKUPS_ENABLED)) {
      writer.putBoolean(BACKUPS_ENABLED, LegacySharedPrefs.getBoolean(context, "pref_backup_enabled", false))
    }
    if (!store.containsKey(SMS_DELIVERY_REPORTS_ENABLED)) {
      writer.putBoolean(SMS_DELIVERY_REPORTS_ENABLED, LegacySharedPrefs.getBoolean(context, "pref_delivery_report_sms", false))
    }
    if (!store.containsKey(WIFI_CALLING_COMPATIBILITY_MODE_ENABLED)) {
      writer.putBoolean(WIFI_CALLING_COMPATIBILITY_MODE_ENABLED, LegacySharedPrefs.getBoolean(context, "pref_wifi_sms", false))
    }
    if (!store.containsKey(MESSAGE_NOTIFICATIONS_ENABLED)) {
      writer.putBoolean(MESSAGE_NOTIFICATIONS_ENABLED, LegacySharedPrefs.getBoolean(context, "pref_key_enable_notifications", true))
    }
    if (!store.containsKey(MESSAGE_NOTIFICATION_SOUND)) {
      writer.putString(MESSAGE_NOTIFICATION_SOUND, LegacySharedPrefs.getRingtone(context, "pref_key_ringtone", Settings.System.DEFAULT_NOTIFICATION_URI))
    }
    if (!store.containsKey(MESSAGE_VIBRATE_ENABLED)) {
      writer.putBoolean(MESSAGE_VIBRATE_ENABLED, LegacySharedPrefs.getBoolean(context, "pref_key_vibrate", true))
    }
    if (!store.containsKey(MESSAGE_LED_COLOR)) {
      writer.putString(MESSAGE_LED_COLOR, LegacySharedPrefs.getString(context, "pref_led_color", "blue"))
    }
    if (!store.containsKey(MESSAGE_LED_BLINK_PATTERN)) {
      writer.putString(MESSAGE_LED_BLINK_PATTERN, LegacySharedPrefs.getString(context, "pref_led_blink", "500,2000"))
    }
    if (!store.containsKey(MESSAGE_IN_CHAT_SOUNDS_ENABLED)) {
      writer.putBoolean(MESSAGE_IN_CHAT_SOUNDS_ENABLED, LegacySharedPrefs.getBoolean(context, "pref_key_inthread_notifications", true))
    }
    if (!store.containsKey(MESSAGE_REPEAT_ALERTS)) {
      writer.putInteger(MESSAGE_REPEAT_ALERTS, LegacySharedPrefs.getIntegerFromString(context, "pref_repeat_alerts", 0))
    }
    if (!store.containsKey(MESSAGE_NOTIFICATION_PRIVACY)) {
      writer.putString(MESSAGE_NOTIFICATION_PRIVACY, LegacySharedPrefs.getString(context, "pref_notification_privacy", "all"))
    }
    if (!store.containsKey(CALL_NOTIFICATIONS_ENABLED)) {
      writer.putBoolean(CALL_NOTIFICATIONS_ENABLED, LegacySharedPrefs.getBoolean(context, "pref_call_notifications", true))
    }
    if (!store.containsKey(CALL_RINGTONE)) {
      writer.putString(CALL_RINGTONE, LegacySharedPrefs.getRingtone(context, "pref_call_ringtone", Settings.System.DEFAULT_RINGTONE_URI))
    }
    if (!store.containsKey(CALL_VIBRATE_ENABLED)) {
      val systemVibrateWhenRinging = Settings.System.getInt(context.contentResolver, Settings.System.VIBRATE_WHEN_RINGING, 1) == 1
      writer.putBoolean(CALL_VIBRATE_ENABLED, LegacySharedPrefs.getBoolean(context, "pref_call_vibrate", systemVibrateWhenRinging))
    }
    if (!store.containsKey(NOTIFY_WHEN_CONTACT_JOINS_SIGNAL)) {
      writer.putBoolean(NOTIFY_WHEN_CONTACT_JOINS_SIGNAL, LegacySharedPrefs.getBoolean(context, "pref_enable_new_contacts_notifications", false))
    }

    writer.putString(MESSAGE_LED_BLINK_PATTERN_CUSTOM, LegacySharedPrefs.getString(context, "pref_led_blink_custom", "500,2000"))
    writer.putInteger(MESSAGE_NOTIFICATION_PRIORITY, LegacySharedPrefs.getIntegerFromString(context, "pref_notification_priority", NotificationCompat.PRIORITY_HIGH))
    writer.putString(MEDIA_KEYBOARD_MODE, LegacySharedPrefs.getString(context, "pref_media_keyboard_mode", MediaKeyboardMode.EMOJI.name))
    writer.putLong(LOCAL_BACKUP_NEXT_TIME, LegacySharedPrefs.getLong(context, "pref_backup_next_time", -1))
    writer.putBoolean(INCOGNITO_KEYBOARD_ENABLED, LegacySharedPrefs.getBoolean(context, "pref_incognito_keyboard", false))
    writer.putBoolean(READ_RECEIPTS_ENABLED, LegacySharedPrefs.getBoolean(context, "pref_read_receipts", false))
    writer.putBoolean(TYPING_INDICATORS_ENABLED, LegacySharedPrefs.getBoolean(context, "pref_typing_indicators", false))
    writer.putBoolean(SHOW_UNIDENTIFIED_DELIVERY_INDICATORS, LegacySharedPrefs.getBoolean(context, "pref_show_unidentifed_delivery_indicators", false))
    writer.putBoolean(UNIVERSAL_UNIDENTIFIED_ACCESS, LegacySharedPrefs.getBoolean(context, "pref_universal_unidentified_access", false))
    writer.putBoolean(ALWAYS_RELAY_CALLS, LegacySharedPrefs.getBoolean(context, "pref_turn_only", false))

    // Only migrate an explicit choice -- when there was none, the getters fall back to the same resource defaults shared prefs used to.
    val mediaDownloadKeys = mapOf(
      "pref_media_download_mobile" to MEDIA_DOWNLOAD_MOBILE,
      "pref_media_download_wifi" to MEDIA_DOWNLOAD_WIFI,
      "pref_media_download_roaming" to MEDIA_DOWNLOAD_ROAMING
    )

    for ((legacyKey, key) in mediaDownloadKeys) {
      val legacyValue = LegacySharedPrefs.getStringSet(context, legacyKey, null)

      if (legacyValue != null) {
        writer.putBlob(key, SignalStoreList(contents = legacyValue.toList()).encode())
      }
    }

    // Written last so that it acts as the marker for this migration having run.
    writer.putBoolean(SCREEN_SECURITY_ENABLED, LegacySharedPrefs.getBoolean(context, "pref_screen_security", false))

    writer.commit()
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
    DISABLED_AUTOMATICALLY(0),
    ENABLED_BY_USER(1),
    ENABLED_AUTOMATICALLY(2),
    DISABLED_BY_USER(3);

    val isEnabled: Boolean
      get() = this == ENABLED_BY_USER || this == ENABLED_AUTOMATICALLY

    fun serialize(): Int = value

    companion object {
      @JvmStatic
      fun deserialize(value: Int): ForceWebsocketMode {
        return entries.firstOrNull { it.value == value } ?: throw IllegalArgumentException("Bad value: $value")
      }
    }
  }

  /** NEVER rename these -- they're persisted by name. */
  enum class MediaKeyboardMode {
    EMOJI,
    STICKER,
    GIF
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
