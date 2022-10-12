package org.session.libsession.utilities

import android.content.Context
import android.hardware.Camera
import android.net.Uri
import android.provider.Settings
import androidx.annotation.ArrayRes
import androidx.annotation.StyleRes
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.session.libsession.BuildConfig
import org.session.libsession.R
import org.session.libsession.utilities.TextSecurePreferences.Companion.AUTOPLAY_AUDIO_MESSAGES
import org.session.libsession.utilities.TextSecurePreferences.Companion.CALL_NOTIFICATIONS_ENABLED
import org.session.libsession.utilities.TextSecurePreferences.Companion.CLASSIC_DARK
import org.session.libsession.utilities.TextSecurePreferences.Companion.CLASSIC_LIGHT
import org.session.libsession.utilities.TextSecurePreferences.Companion.FOLLOW_SYSTEM_SETTINGS
import org.session.libsession.utilities.TextSecurePreferences.Companion.LAST_VACUUM_TIME
import org.session.libsession.utilities.TextSecurePreferences.Companion.LEGACY_PREF_KEY_SELECTED_UI_MODE
import org.session.libsession.utilities.TextSecurePreferences.Companion.OCEAN_DARK
import org.session.libsession.utilities.TextSecurePreferences.Companion.OCEAN_LIGHT
import org.session.libsession.utilities.TextSecurePreferences.Companion.SELECTED_STYLE
import org.session.libsession.utilities.TextSecurePreferences.Companion.SHOWN_CALL_NOTIFICATION
import org.session.libsession.utilities.TextSecurePreferences.Companion.SHOWN_CALL_WARNING
import org.session.libsignal.utilities.Log
import java.io.IOException
import java.util.Arrays
import java.util.Date
import javax.inject.Inject

interface TextSecurePreferences {

    fun getLastConfigurationSyncTime(): Long
    fun setLastConfigurationSyncTime(value: Long)
    fun getConfigurationMessageSynced(): Boolean
    fun setConfigurationMessageSynced(value: Boolean)
    fun isUsingFCM(): Boolean
    fun setIsUsingFCM(value: Boolean)
    fun getFCMToken(): String?
    fun setFCMToken(value: String)
    fun getLastFCMUploadTime(): Long
    fun setLastFCMUploadTime(value: Long)
    fun isScreenLockEnabled(): Boolean
    fun setScreenLockEnabled(value: Boolean)
    fun getScreenLockTimeout(): Long
    fun setScreenLockTimeout(value: Long)
    fun setBackupPassphrase(passphrase: String?)
    fun getBackupPassphrase(): String?
    fun setEncryptedBackupPassphrase(encryptedPassphrase: String?)
    fun getEncryptedBackupPassphrase(): String?
    fun setBackupEnabled(value: Boolean)
    fun isBackupEnabled(): Boolean
    fun setNextBackupTime(time: Long)
    fun getNextBackupTime(): Long
    fun setBackupSaveDir(dirUri: String?)
    fun getBackupSaveDir(): String?
    fun getNeedsSqlCipherMigration(): Boolean
    fun setAttachmentEncryptedSecret(secret: String)
    fun setAttachmentUnencryptedSecret(secret: String?)
    fun getAttachmentEncryptedSecret(): String?
    fun getAttachmentUnencryptedSecret(): String?
    fun setDatabaseEncryptedSecret(secret: String)
    fun setDatabaseUnencryptedSecret(secret: String?)
    fun getDatabaseUnencryptedSecret(): String?
    fun getDatabaseEncryptedSecret(): String?
    fun isIncognitoKeyboardEnabled(): Boolean
    fun isReadReceiptsEnabled(): Boolean
    fun setReadReceiptsEnabled(enabled: Boolean)
    fun isTypingIndicatorsEnabled(): Boolean
    fun setTypingIndicatorsEnabled(enabled: Boolean)
    fun isLinkPreviewsEnabled(): Boolean
    fun setLinkPreviewsEnabled(enabled: Boolean)
    fun hasSeenGIFMetaDataWarning(): Boolean
    fun setHasSeenGIFMetaDataWarning()
    fun isGifSearchInGridLayout(): Boolean
    fun setIsGifSearchInGridLayout(isGrid: Boolean)
    fun getProfileKey(): String?
    fun setProfileKey(key: String?)
    fun setProfileName(name: String?)
    fun getProfileName(): String?
    fun setProfileAvatarId(id: Int)
    fun getProfileAvatarId(): Int
    fun setProfilePictureURL(url: String?)
    fun getProfilePictureURL(): String?
    fun getNotificationPriority(): Int
    fun getMessageBodyTextSize(): Int
    fun setDirectCaptureCameraId(value: Int)
    fun getDirectCaptureCameraId(): Int
    fun getNotificationPrivacy(): NotificationPrivacyPreference
    fun getRepeatAlertsCount(): Int
    fun getLocalRegistrationId(): Int
    fun setLocalRegistrationId(registrationId: Int)
    fun isInThreadNotifications(): Boolean
    fun isUniversalUnidentifiedAccess(): Boolean
    fun getUpdateApkRefreshTime(): Long
    fun setUpdateApkRefreshTime(value: Long)
    fun setUpdateApkDownloadId(value: Long)
    fun getUpdateApkDownloadId(): Long
    fun setUpdateApkDigest(value: String?)
    fun getUpdateApkDigest(): String?
    fun getLocalNumber(): String?
    fun setLocalNumber(localNumber: String)
    fun removeLocalNumber()
    fun isEnterSendsEnabled(): Boolean
    fun isPasswordDisabled(): Boolean
    fun setPasswordDisabled(disabled: Boolean)
    fun isScreenSecurityEnabled(): Boolean
    fun getLastVersionCode(): Int
    fun setLastVersionCode(versionCode: Int)
    fun isPassphraseTimeoutEnabled(): Boolean
    fun getPassphraseTimeoutInterval(): Int
    fun getLanguage(): String?
    fun hasSeenWelcomeScreen(): Boolean
    fun setHasSeenWelcomeScreen(value: Boolean)
    fun isNotificationsEnabled(): Boolean
    fun getNotificationRingtone(): Uri
    fun removeNotificationRingtone()
    fun setNotificationRingtone(ringtone: String?)
    fun setNotificationVibrateEnabled(enabled: Boolean)
    fun isNotificationVibrateEnabled(): Boolean
    fun getNotificationLedColor(): Int
    fun isThreadLengthTrimmingEnabled(): Boolean
    fun isSystemEmojiPreferred(): Boolean
    fun getMobileMediaDownloadAllowed(): Set<String>?
    fun getWifiMediaDownloadAllowed(): Set<String>?
    fun getRoamingMediaDownloadAllowed(): Set<String>?
    fun getMediaDownloadAllowed(key: String, @ArrayRes defaultValuesRes: Int): Set<String>?
    fun getLogEncryptedSecret(): String?
    fun setLogEncryptedSecret(base64Secret: String?)
    fun getLogUnencryptedSecret(): String?
    fun setLogUnencryptedSecret(base64Secret: String?)
    fun getNotificationChannelVersion(): Int
    fun setNotificationChannelVersion(version: Int)
    fun getNotificationMessagesChannelVersion(): Int
    fun setNotificationMessagesChannelVersion(version: Int)
    fun getBooleanPreference(key: String?, defaultValue: Boolean): Boolean
    fun setBooleanPreference(key: String?, value: Boolean)
    fun getStringPreference(key: String, defaultValue: String?): String?
    fun setStringPreference(key: String?, value: String?)
    fun getIntegerPreference(key: String, defaultValue: Int): Int
    fun setIntegerPreference(key: String, value: Int)
    fun setIntegerPreferenceBlocking(key: String, value: Int): Boolean
    fun getLongPreference(key: String, defaultValue: Long): Long
    fun setLongPreference(key: String, value: Long)
    fun removePreference(key: String)
    fun getStringSetPreference(key: String, defaultValues: Set<String>): Set<String>?
    fun getHasViewedSeed(): Boolean
    fun setHasViewedSeed(hasViewedSeed: Boolean)
    fun setRestorationTime(time: Long)
    fun getRestorationTime(): Long
    fun getLastProfilePictureUpload(): Long
    fun setLastProfilePictureUpload(newValue: Long)
    fun getLastSnodePoolRefreshDate(): Long
    fun setLastSnodePoolRefreshDate(date: Date)
    fun shouldUpdateProfile(profileUpdateTime: Long): Boolean
    fun setLastProfileUpdateTime(profileUpdateTime: Long)
    fun getLastOpenTimeDate(): Long
    fun setLastOpenDate()
    fun hasSeenLinkPreviewSuggestionDialog(): Boolean
    fun setHasSeenLinkPreviewSuggestionDialog()
    fun hasHiddenMessageRequests(): Boolean
    fun setHasHiddenMessageRequests()
    fun setShownCallWarning(): Boolean
    fun setShownCallNotification(): Boolean
    fun isCallNotificationsEnabled(): Boolean
    fun getLastVacuum(): Long
    fun setLastVacuumNow()
    fun getFingerprintKeyGenerated(): Boolean
    fun setFingerprintKeyGenerated()
    @StyleRes fun getAccentColorStyle(): Int?
    fun setAccentColorStyle(@StyleRes newColorStyle: Int?)
    fun getThemeStyle(): String
    fun getFollowSystemSettings(): Boolean
    fun setThemeStyle(themeStyle: String)
    fun setFollowSystemSettings(followSystemSettings: Boolean)
    fun autoplayAudioMessages(): Boolean
    fun hasPreference(key: String): Boolean
    fun clearAll()

    companion object {
        val TAG = TextSecurePreferences::class.simpleName

        internal val _events = MutableSharedFlow<String>(0, 64, BufferOverflow.DROP_OLDEST)
        val events get() = _events.asSharedFlow()

        const val DISABLE_PASSPHRASE_PREF = "pref_disable_passphrase"
        const val LANGUAGE_PREF = "pref_language"
        const val THREAD_TRIM_NOW = "pref_trim_now"
        const val LAST_VERSION_CODE_PREF = "last_version_code"
        const val RINGTONE_PREF = "pref_key_ringtone"
        const val VIBRATE_PREF = "pref_key_vibrate"
        const val NOTIFICATION_PREF = "pref_key_enable_notifications"
        const val LED_COLOR_PREF = "pref_led_color"
        const val LED_COLOR_PREF_PRIMARY = "pref_led_color_primary"
        const val LED_BLINK_PREF = "pref_led_blink"
        const val LED_BLINK_PREF_CUSTOM = "pref_led_blink_custom"
        const val PASSPHRASE_TIMEOUT_INTERVAL_PREF = "pref_timeout_interval"
        const val PASSPHRASE_TIMEOUT_PREF = "pref_timeout_passphrase"
        const val SCREEN_SECURITY_PREF = "pref_screen_security"
        const val ENTER_SENDS_PREF = "pref_enter_sends"
        const val THREAD_TRIM_ENABLED = "pref_trim_threads"
        const val LOCAL_NUMBER_PREF = "pref_local_number"
        const val REGISTERED_GCM_PREF = "pref_gcm_registered"
        const val SEEN_WELCOME_SCREEN_PREF = "pref_seen_welcome_screen"
        const val UPDATE_APK_REFRESH_TIME_PREF = "pref_update_apk_refresh_time"
        const val UPDATE_APK_DOWNLOAD_ID = "pref_update_apk_download_id"
        const val UPDATE_APK_DIGEST = "pref_update_apk_digest"
        const val IN_THREAD_NOTIFICATION_PREF = "pref_key_inthread_notifications"
        const val IN_APP_NOTIFICATION_SOUNDS = "pref_sound_when_app_open"
        const val MESSAGE_BODY_TEXT_SIZE_PREF = "pref_message_body_text_size"
        const val LOCAL_REGISTRATION_ID_PREF = "pref_local_registration_id"
        const val REPEAT_ALERTS_PREF = "pref_repeat_alerts"
        const val NOTIFICATION_PRIVACY_PREF = "pref_notification_privacy"
        const val NOTIFICATION_PRIORITY_PREF = "pref_notification_priority"
        const val MEDIA_DOWNLOAD_MOBILE_PREF = "pref_media_download_mobile"
        const val MEDIA_DOWNLOAD_WIFI_PREF = "pref_media_download_wifi"
        const val MEDIA_DOWNLOAD_ROAMING_PREF = "pref_media_download_roaming"
        const val SYSTEM_EMOJI_PREF = "pref_system_emoji"
        const val DIRECT_CAPTURE_CAMERA_ID = "pref_direct_capture_camera_id"
        const val PROFILE_KEY_PREF = "pref_profile_key"
        const val PROFILE_NAME_PREF = "pref_profile_name"
        const val PROFILE_AVATAR_ID_PREF = "pref_profile_avatar_id"
        const val PROFILE_AVATAR_URL_PREF = "pref_profile_avatar_url"
        const val READ_RECEIPTS_PREF = "pref_read_receipts"
        const val INCOGNITO_KEYBORAD_PREF = "pref_incognito_keyboard"
        const val DATABASE_ENCRYPTED_SECRET = "pref_database_encrypted_secret"
        const val DATABASE_UNENCRYPTED_SECRET = "pref_database_unencrypted_secret"
        const val ATTACHMENT_ENCRYPTED_SECRET = "pref_attachment_encrypted_secret"
        const val ATTACHMENT_UNENCRYPTED_SECRET = "pref_attachment_unencrypted_secret"
        const val NEEDS_SQLCIPHER_MIGRATION = "pref_needs_sql_cipher_migration"
        const val BACKUP_ENABLED = "pref_backup_enabled_v3"
        const val BACKUP_PASSPHRASE = "pref_backup_passphrase"
        const val ENCRYPTED_BACKUP_PASSPHRASE = "pref_encrypted_backup_passphrase"
        const val BACKUP_TIME = "pref_backup_next_time"
        const val BACKUP_NOW = "pref_backup_create"
        const val BACKUP_SAVE_DIR = "pref_save_dir"
        const val SCREEN_LOCK = "pref_android_screen_lock"
        const val SCREEN_LOCK_TIMEOUT = "pref_android_screen_lock_timeout"
        const val LOG_ENCRYPTED_SECRET = "pref_log_encrypted_secret"
        const val LOG_UNENCRYPTED_SECRET = "pref_log_unencrypted_secret"
        const val NOTIFICATION_CHANNEL_VERSION = "pref_notification_channel_version"
        const val NOTIFICATION_MESSAGES_CHANNEL_VERSION = "pref_notification_messages_channel_version"
        const val UNIVERSAL_UNIDENTIFIED_ACCESS = "pref_universal_unidentified_access"
        const val TYPING_INDICATORS = "pref_typing_indicators"
        const val LINK_PREVIEWS = "pref_link_previews"
        const val GIF_METADATA_WARNING = "has_seen_gif_metadata_warning"
        const val GIF_GRID_LAYOUT = "pref_gif_grid_layout"
        const val IS_USING_FCM = "pref_is_using_fcm"
        const val FCM_TOKEN = "pref_fcm_token"
        const val LAST_FCM_TOKEN_UPLOAD_TIME = "pref_last_fcm_token_upload_time_2"
        const val LAST_CONFIGURATION_SYNC_TIME = "pref_last_configuration_sync_time"
        const val CONFIGURATION_SYNCED = "pref_configuration_synced"
        const val LAST_PROFILE_UPDATE_TIME = "pref_last_profile_update_time"
        const val LAST_OPEN_DATE = "pref_last_open_date"
        const val HAS_HIDDEN_MESSAGE_REQUESTS = "pref_message_requests_hidden"
        const val CALL_NOTIFICATIONS_ENABLED = "pref_call_notifications_enabled"
        const val SHOWN_CALL_WARNING = "pref_shown_call_warning" // call warning is user-facing warning of enabling calls
        const val SHOWN_CALL_NOTIFICATION = "pref_shown_call_notification" // call notification is a prompt to check privacy settings
        const val LAST_VACUUM_TIME = "pref_last_vacuum_time"
        const val AUTOPLAY_AUDIO_MESSAGES = "pref_autoplay_audio"
        const val FINGERPRINT_KEY_GENERATED = "fingerprint_key_generated"
        const val SELECTED_ACCENT_COLOR = "selected_accent_color"
        const val GREEN_ACCENT = "accent_green"
        const val BLUE_ACCENT = "accent_blue"
        const val PURPLE_ACCENT = "accent_purple"
        const val PINK_ACCENT = "accent_pink"
        const val RED_ACCENT = "accent_red"
        const val ORANGE_ACCENT = "accent_orange"
        const val YELLOW_ACCENT = "accent_yellow"

        const val SELECTED_STYLE = "pref_selected_style" // classic_dark/light, ocean_dark/light
        const val FOLLOW_SYSTEM_SETTINGS = "pref_follow_system" // follow system day/night

        const val LEGACY_PREF_KEY_SELECTED_UI_MODE = "SELECTED_UI_MODE" // this will be cleared upon launching app, for users migrating to theming build
        const val CLASSIC_DARK = "classic.dark"
        const val CLASSIC_LIGHT = "classic.light"
        const val OCEAN_DARK = "ocean.dark"
        const val OCEAN_LIGHT = "ocean.light"

        @JvmStatic
        fun getLastConfigurationSyncTime(context: Context): Long {
            return getLongPreference(context, LAST_CONFIGURATION_SYNC_TIME, 0)
        }

        @JvmStatic
        fun setLastConfigurationSyncTime(context: Context, value: Long) {
            setLongPreference(context, LAST_CONFIGURATION_SYNC_TIME, value)
        }

        @JvmStatic
        fun getConfigurationMessageSynced(context: Context): Boolean {
            return getBooleanPreference(context, CONFIGURATION_SYNCED, false)
        }

        @JvmStatic
        fun setConfigurationMessageSynced(context: Context, value: Boolean) {
            setBooleanPreference(context, CONFIGURATION_SYNCED, value)
            _events.tryEmit(CONFIGURATION_SYNCED)
        }

        @JvmStatic
        fun isUsingFCM(context: Context): Boolean {
            return getBooleanPreference(context, IS_USING_FCM, false)
        }

        @JvmStatic
        fun setIsUsingFCM(context: Context, value: Boolean) {
            setBooleanPreference(context, IS_USING_FCM, value)
        }

        @JvmStatic
        fun getFCMToken(context: Context): String? {
            return getStringPreference(context, FCM_TOKEN, "")
        }

        @JvmStatic
        fun setFCMToken(context: Context, value: String) {
            setStringPreference(context, FCM_TOKEN, value)
        }

        fun getLastFCMUploadTime(context: Context): Long {
            return getLongPreference(context, LAST_FCM_TOKEN_UPLOAD_TIME, 0)
        }

        fun setLastFCMUploadTime(context: Context, value: Long) {
            setLongPreference(context, LAST_FCM_TOKEN_UPLOAD_TIME, value)
        }

        // endregion
        @JvmStatic
        fun isScreenLockEnabled(context: Context): Boolean {
            return getBooleanPreference(context, SCREEN_LOCK, false)
        }

        @JvmStatic
        fun setScreenLockEnabled(context: Context, value: Boolean) {
            setBooleanPreference(context, SCREEN_LOCK, value)
        }

        @JvmStatic
        fun getScreenLockTimeout(context: Context): Long {
            return getLongPreference(context, SCREEN_LOCK_TIMEOUT, 0)
        }

        @JvmStatic
        fun setScreenLockTimeout(context: Context, value: Long) {
            setLongPreference(context, SCREEN_LOCK_TIMEOUT, value)
        }

        @JvmStatic
        fun setBackupPassphrase(context: Context, passphrase: String?) {
            setStringPreference(context, BACKUP_PASSPHRASE, passphrase)
        }

        @JvmStatic
        fun getBackupPassphrase(context: Context): String? {
            return getStringPreference(context, BACKUP_PASSPHRASE, null)
        }

        @JvmStatic
        fun setEncryptedBackupPassphrase(context: Context, encryptedPassphrase: String?) {
            setStringPreference(context, ENCRYPTED_BACKUP_PASSPHRASE, encryptedPassphrase)
        }

        @JvmStatic
        fun getEncryptedBackupPassphrase(context: Context): String? {
            return getStringPreference(context, ENCRYPTED_BACKUP_PASSPHRASE, null)
        }

        fun setBackupEnabled(context: Context, value: Boolean) {
            setBooleanPreference(context, BACKUP_ENABLED, value)
        }

        @JvmStatic
        fun isBackupEnabled(context: Context): Boolean {
            return getBooleanPreference(context, BACKUP_ENABLED, false)
        }

        @JvmStatic
        fun setNextBackupTime(context: Context, time: Long) {
            setLongPreference(context, BACKUP_TIME, time)
        }

        @JvmStatic
        fun getNextBackupTime(context: Context): Long {
            return getLongPreference(context, BACKUP_TIME, -1)
        }

        fun setBackupSaveDir(context: Context, dirUri: String?) {
            setStringPreference(context, BACKUP_SAVE_DIR, dirUri)
        }

        fun getBackupSaveDir(context: Context): String? {
            return getStringPreference(context, BACKUP_SAVE_DIR, null)
        }

        @JvmStatic
        fun getNeedsSqlCipherMigration(context: Context): Boolean {
            return getBooleanPreference(context, NEEDS_SQLCIPHER_MIGRATION, false)
        }

        @JvmStatic
        fun setAttachmentEncryptedSecret(context: Context, secret: String) {
            setStringPreference(context, ATTACHMENT_ENCRYPTED_SECRET, secret)
        }

        @JvmStatic
        fun setAttachmentUnencryptedSecret(context: Context, secret: String?) {
            setStringPreference(context, ATTACHMENT_UNENCRYPTED_SECRET, secret)
        }

        @JvmStatic
        fun getAttachmentEncryptedSecret(context: Context): String? {
            return getStringPreference(context, ATTACHMENT_ENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun getAttachmentUnencryptedSecret(context: Context): String? {
            return getStringPreference(context, ATTACHMENT_UNENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun setDatabaseEncryptedSecret(context: Context, secret: String) {
            setStringPreference(context, DATABASE_ENCRYPTED_SECRET, secret)
        }

        @JvmStatic
        fun setDatabaseUnencryptedSecret(context: Context, secret: String?) {
            setStringPreference(context, DATABASE_UNENCRYPTED_SECRET, secret)
        }

        @JvmStatic
        fun getDatabaseUnencryptedSecret(context: Context): String? {
            return getStringPreference(context, DATABASE_UNENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun getDatabaseEncryptedSecret(context: Context): String? {
            return getStringPreference(context, DATABASE_ENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun isIncognitoKeyboardEnabled(context: Context): Boolean {
            return getBooleanPreference(context, INCOGNITO_KEYBORAD_PREF, true)
        }

        @JvmStatic
        fun isReadReceiptsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, READ_RECEIPTS_PREF, false)
        }

        fun setReadReceiptsEnabled(context: Context, enabled: Boolean) {
            setBooleanPreference(context, READ_RECEIPTS_PREF, enabled)
        }

        @JvmStatic
        fun isTypingIndicatorsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, TYPING_INDICATORS, false)
        }

        @JvmStatic
        fun setTypingIndicatorsEnabled(context: Context, enabled: Boolean) {
            setBooleanPreference(context, TYPING_INDICATORS, enabled)
        }

        @JvmStatic
        fun isLinkPreviewsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, LINK_PREVIEWS, false)
        }

        @JvmStatic
        fun setLinkPreviewsEnabled(context: Context, enabled: Boolean) {
            setBooleanPreference(context, LINK_PREVIEWS, enabled)
        }

        @JvmStatic
        fun hasSeenGIFMetaDataWarning(context: Context): Boolean {
            return getBooleanPreference(context, GIF_METADATA_WARNING, false)
        }

        @JvmStatic
        fun setHasSeenGIFMetaDataWarning(context: Context) {
            setBooleanPreference(context, GIF_METADATA_WARNING, true)
        }

        @JvmStatic
        fun isGifSearchInGridLayout(context: Context): Boolean {
            return getBooleanPreference(context, GIF_GRID_LAYOUT, false)
        }

        @JvmStatic
        fun setIsGifSearchInGridLayout(context: Context, isGrid: Boolean) {
            setBooleanPreference(context, GIF_GRID_LAYOUT, isGrid)
        }

        @JvmStatic
        fun getProfileKey(context: Context): String? {
            return getStringPreference(context, PROFILE_KEY_PREF, null)
        }

        @JvmStatic
        fun setProfileKey(context: Context, key: String?) {
            setStringPreference(context, PROFILE_KEY_PREF, key)
        }

        @JvmStatic
        fun setProfileName(context: Context, name: String?) {
            setStringPreference(context, PROFILE_NAME_PREF, name)
            _events.tryEmit(PROFILE_NAME_PREF)
        }

        @JvmStatic
        fun getProfileName(context: Context): String? {
            return getStringPreference(context, PROFILE_NAME_PREF, null)
        }

        @JvmStatic
        fun setProfileAvatarId(context: Context, id: Int) {
            setIntegerPreference(context, PROFILE_AVATAR_ID_PREF, id)
        }

        @JvmStatic
        fun getProfileAvatarId(context: Context): Int {
            return getIntegerPreference(context, PROFILE_AVATAR_ID_PREF, 0)
        }

        fun setProfilePictureURL(context: Context, url: String?) {
            setStringPreference(context, PROFILE_AVATAR_URL_PREF, url)
        }

        @JvmStatic
        fun getProfilePictureURL(context: Context): String? {
            return getStringPreference(context, PROFILE_AVATAR_URL_PREF, null)
        }

        @JvmStatic
        fun getNotificationPriority(context: Context): Int {
            return getStringPreference(context, NOTIFICATION_PRIORITY_PREF, NotificationCompat.PRIORITY_HIGH.toString())!!.toInt()
        }

        @JvmStatic
        fun getMessageBodyTextSize(context: Context): Int {
            return getStringPreference(context, MESSAGE_BODY_TEXT_SIZE_PREF, "16")!!.toInt()
        }

        @JvmStatic
        fun setDirectCaptureCameraId(context: Context, value: Int) {
            setIntegerPreference(context, DIRECT_CAPTURE_CAMERA_ID, value)
        }

        @JvmStatic
        fun getDirectCaptureCameraId(context: Context): Int {
            return getIntegerPreference(context, DIRECT_CAPTURE_CAMERA_ID, Camera.CameraInfo.CAMERA_FACING_BACK)
        }

        @JvmStatic
        fun getNotificationPrivacy(context: Context): NotificationPrivacyPreference {
            return NotificationPrivacyPreference(getStringPreference(context, NOTIFICATION_PRIVACY_PREF, "all"))
        }

        @JvmStatic
        fun getRepeatAlertsCount(context: Context): Int {
            return try {
                getStringPreference(context, REPEAT_ALERTS_PREF, "0")!!.toInt()
            } catch (e: NumberFormatException) {
                Log.w(TAG, e)
                0
            }
        }

        fun getLocalRegistrationId(context: Context): Int {
            return getIntegerPreference(context, LOCAL_REGISTRATION_ID_PREF, 0)
        }

        fun setLocalRegistrationId(context: Context, registrationId: Int) {
            setIntegerPreference(context, LOCAL_REGISTRATION_ID_PREF, registrationId)
        }

        @JvmStatic
        fun isInThreadNotifications(context: Context): Boolean {
            return getBooleanPreference(context, IN_THREAD_NOTIFICATION_PREF, true)
        }

        @JvmStatic
        fun isUniversalUnidentifiedAccess(context: Context): Boolean {
            return getBooleanPreference(context, UNIVERSAL_UNIDENTIFIED_ACCESS, false)
        }

        @JvmStatic
        fun getUpdateApkRefreshTime(context: Context): Long {
            return getLongPreference(context, UPDATE_APK_REFRESH_TIME_PREF, 0L)
        }

        @JvmStatic
        fun setUpdateApkRefreshTime(context: Context, value: Long) {
            setLongPreference(context, UPDATE_APK_REFRESH_TIME_PREF, value)
        }

        @JvmStatic
        fun setUpdateApkDownloadId(context: Context, value: Long) {
            setLongPreference(context, UPDATE_APK_DOWNLOAD_ID, value)
        }

        @JvmStatic
        fun getUpdateApkDownloadId(context: Context): Long {
            return getLongPreference(context, UPDATE_APK_DOWNLOAD_ID, -1)
        }

        @JvmStatic
        fun setUpdateApkDigest(context: Context, value: String?) {
            setStringPreference(context, UPDATE_APK_DIGEST, value)
        }

        @JvmStatic
        fun getUpdateApkDigest(context: Context): String? {
            return getStringPreference(context, UPDATE_APK_DIGEST, null)
        }

        @JvmStatic
        fun getLocalNumber(context: Context): String? {
            return getStringPreference(context, LOCAL_NUMBER_PREF, null)
        }

        fun setLocalNumber(context: Context, localNumber: String) {
            setStringPreference(context, LOCAL_NUMBER_PREF, localNumber.toLowerCase())
        }

        fun removeLocalNumber(context: Context) {
            removePreference(context, LOCAL_NUMBER_PREF)
        }

        @JvmStatic
        fun isEnterSendsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, ENTER_SENDS_PREF, false)
        }

        @JvmStatic
        fun isPasswordDisabled(context: Context): Boolean {
            return getBooleanPreference(context, DISABLE_PASSPHRASE_PREF, true)
        }

        fun setPasswordDisabled(context: Context, disabled: Boolean) {
            setBooleanPreference(context, DISABLE_PASSPHRASE_PREF, disabled)
        }

        @JvmStatic
        fun isScreenSecurityEnabled(context: Context): Boolean {
            return getBooleanPreference(context, SCREEN_SECURITY_PREF, !BuildConfig.DEBUG)
        }

        fun getLastVersionCode(context: Context): Int {
            return getIntegerPreference(context, LAST_VERSION_CODE_PREF, 0)
        }

        @Throws(IOException::class)
        fun setLastVersionCode(context: Context, versionCode: Int) {
            if (!setIntegerPreferenceBlocking(context, LAST_VERSION_CODE_PREF, versionCode)) {
                throw IOException("couldn't write version code to sharedpreferences")
            }
        }

        @JvmStatic
        fun isPassphraseTimeoutEnabled(context: Context): Boolean {
            return getBooleanPreference(context, PASSPHRASE_TIMEOUT_PREF, false)
        }

        @JvmStatic
        fun getPassphraseTimeoutInterval(context: Context): Int {
            return getIntegerPreference(context, PASSPHRASE_TIMEOUT_INTERVAL_PREF, 5 * 60)
        }

        @JvmStatic
        fun getLanguage(context: Context): String? {
            return getStringPreference(context, LANGUAGE_PREF, "zz")
        }

        @JvmStatic
        fun hasSeenWelcomeScreen(context: Context): Boolean {
            return getBooleanPreference(context, SEEN_WELCOME_SCREEN_PREF, false)
        }

        fun setHasSeenWelcomeScreen(context: Context, value: Boolean) {
            setBooleanPreference(context, SEEN_WELCOME_SCREEN_PREF, value)
        }

        @JvmStatic
        fun isNotificationsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, NOTIFICATION_PREF, true)
        }

        @JvmStatic
        fun getNotificationRingtone(context: Context): Uri {
            var result = getStringPreference(context, RINGTONE_PREF, Settings.System.DEFAULT_NOTIFICATION_URI.toString())
            if (result != null && result.startsWith("file:")) {
                result = Settings.System.DEFAULT_NOTIFICATION_URI.toString()
            }
            return Uri.parse(result)
        }

        @JvmStatic
        fun removeNotificationRingtone(context: Context) {
            removePreference(context, RINGTONE_PREF)
        }

        @JvmStatic
        fun setNotificationRingtone(context: Context, ringtone: String?) {
            setStringPreference(context, RINGTONE_PREF, ringtone)
        }

        @JvmStatic
        fun setNotificationVibrateEnabled(context: Context, enabled: Boolean) {
            setBooleanPreference(context, VIBRATE_PREF, enabled)
        }

        @JvmStatic
        fun isNotificationVibrateEnabled(context: Context): Boolean {
            return getBooleanPreference(context, VIBRATE_PREF, true)
        }

        @JvmStatic
        fun getNotificationLedColor(context: Context): Int {
            return getIntegerPreference(context, LED_COLOR_PREF_PRIMARY, ThemeUtil.getThemedColor(context, R.attr.colorAccent))
        }

        @JvmStatic
        fun isThreadLengthTrimmingEnabled(context: Context): Boolean {
            return getBooleanPreference(context, THREAD_TRIM_ENABLED, true)
        }

        @JvmStatic
        fun isSystemEmojiPreferred(context: Context): Boolean {
            return getBooleanPreference(context, SYSTEM_EMOJI_PREF, false)
        }

        @JvmStatic
        fun getMobileMediaDownloadAllowed(context: Context): Set<String>? {
            return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_MOBILE_PREF, R.array.pref_media_download_mobile_data_default)
        }

        @JvmStatic
        fun getWifiMediaDownloadAllowed(context: Context): Set<String>? {
            return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_WIFI_PREF, R.array.pref_media_download_wifi_default)
        }

        @JvmStatic
        fun getRoamingMediaDownloadAllowed(context: Context): Set<String>? {
            return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_ROAMING_PREF, R.array.pref_media_download_roaming_default)
        }

        private fun getMediaDownloadAllowed(context: Context, key: String, @ArrayRes defaultValuesRes: Int): Set<String>? {
            return getStringSetPreference(context, key, HashSet(Arrays.asList(*context.resources.getStringArray(defaultValuesRes))))
        }

        @JvmStatic
        fun getLogEncryptedSecret(context: Context): String? {
            return getStringPreference(context, LOG_ENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun setLogEncryptedSecret(context: Context, base64Secret: String?) {
            setStringPreference(context, LOG_ENCRYPTED_SECRET, base64Secret)
        }

        @JvmStatic
        fun getLogUnencryptedSecret(context: Context): String? {
            return getStringPreference(context, LOG_UNENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun setLogUnencryptedSecret(context: Context, base64Secret: String?) {
            setStringPreference(context, LOG_UNENCRYPTED_SECRET, base64Secret)
        }

        @JvmStatic
        fun getNotificationChannelVersion(context: Context): Int {
            return getIntegerPreference(context, NOTIFICATION_CHANNEL_VERSION, 1)
        }

        @JvmStatic
        fun setNotificationChannelVersion(context: Context, version: Int) {
            setIntegerPreference(context, NOTIFICATION_CHANNEL_VERSION, version)
        }

        @JvmStatic
        fun getNotificationMessagesChannelVersion(context: Context): Int {
            return getIntegerPreference(context, NOTIFICATION_MESSAGES_CHANNEL_VERSION, 1)
        }

        @JvmStatic
        fun setNotificationMessagesChannelVersion(context: Context, version: Int) {
            setIntegerPreference(context, NOTIFICATION_MESSAGES_CHANNEL_VERSION, version)
        }

        @JvmStatic
        fun getBooleanPreference(context: Context, key: String?, defaultValue: Boolean): Boolean {
            return getDefaultSharedPreferences(context).getBoolean(key, defaultValue)
        }

        @JvmStatic
        fun setBooleanPreference(context: Context, key: String?, value: Boolean) {
            getDefaultSharedPreferences(context).edit().putBoolean(key, value).apply()
        }

        @JvmStatic
        fun getStringPreference(context: Context, key: String, defaultValue: String?): String? {
            return getDefaultSharedPreferences(context).getString(key, defaultValue)
        }

        @JvmStatic
        fun setStringPreference(context: Context, key: String?, value: String?) {
            getDefaultSharedPreferences(context).edit().putString(key, value).apply()
        }

        private fun getIntegerPreference(context: Context, key: String, defaultValue: Int): Int {
            return getDefaultSharedPreferences(context).getInt(key, defaultValue)
        }

        private fun setIntegerPreference(context: Context, key: String, value: Int) {
            getDefaultSharedPreferences(context).edit().putInt(key, value).apply()
        }

        private fun setIntegerPreferenceBlocking(context: Context, key: String, value: Int): Boolean {
            return getDefaultSharedPreferences(context).edit().putInt(key, value).commit()
        }

        private fun getLongPreference(context: Context, key: String, defaultValue: Long): Long {
            return getDefaultSharedPreferences(context).getLong(key, defaultValue)
        }

        private fun setLongPreference(context: Context, key: String, value: Long) {
            getDefaultSharedPreferences(context).edit().putLong(key, value).apply()
        }

        private fun removePreference(context: Context, key: String) {
            getDefaultSharedPreferences(context).edit().remove(key).apply()
        }

        private fun getStringSetPreference(context: Context, key: String, defaultValues: Set<String>): Set<String>? {
            val prefs = getDefaultSharedPreferences(context)
            return if (prefs.contains(key)) {
                prefs.getStringSet(key, emptySet())
            } else {
                defaultValues
            }
        }

        fun getHasViewedSeed(context: Context): Boolean {
            return getBooleanPreference(context, "has_viewed_seed", false)
        }

        fun setHasViewedSeed(context: Context, hasViewedSeed: Boolean) {
            setBooleanPreference(context, "has_viewed_seed", hasViewedSeed)
        }

        fun setRestorationTime(context: Context, time: Long) {
            setLongPreference(context, "restoration_time", time)
        }

        fun getRestorationTime(context: Context): Long {
            return getLongPreference(context, "restoration_time", 0)
        }

        @JvmStatic
        fun getLastProfilePictureUpload(context: Context): Long {
            return getLongPreference(context, "last_profile_picture_upload", 0)
        }

        @JvmStatic
        fun setLastProfilePictureUpload(context: Context, newValue: Long) {
            setLongPreference(context, "last_profile_picture_upload", newValue)
        }

        fun getLastSnodePoolRefreshDate(context: Context?): Long {
            return getLongPreference(context!!, "last_snode_pool_refresh_date", 0)
        }

        fun setLastSnodePoolRefreshDate(context: Context?, date: Date) {
            setLongPreference(context!!, "last_snode_pool_refresh_date", date.time)
        }

        @JvmStatic
        fun shouldUpdateProfile(context: Context, profileUpdateTime: Long): Boolean {
            return profileUpdateTime > getLongPreference(context, LAST_PROFILE_UPDATE_TIME, 0)
        }

        @JvmStatic
        fun setLastProfileUpdateTime(context: Context, profileUpdateTime: Long) {
            setLongPreference(context, LAST_PROFILE_UPDATE_TIME, profileUpdateTime)
        }

        fun getLastOpenTimeDate(context: Context): Long {
            return getLongPreference(context, LAST_OPEN_DATE, 0)
        }

        fun setLastOpenDate(context: Context) {
            setLongPreference(context, LAST_OPEN_DATE, System.currentTimeMillis())
        }

        fun hasSeenLinkPreviewSuggestionDialog(context: Context): Boolean {
            return getBooleanPreference(context, "has_seen_link_preview_suggestion_dialog", false)
        }

        fun setHasSeenLinkPreviewSuggestionDialog(context: Context) {
            setBooleanPreference(context, "has_seen_link_preview_suggestion_dialog", true)
        }

        @JvmStatic
        fun hasHiddenMessageRequests(context: Context): Boolean {
            return getBooleanPreference(context, HAS_HIDDEN_MESSAGE_REQUESTS, false)
        }

        @JvmStatic
        fun removeHasHiddenMessageRequests(context: Context) {
            removePreference(context, HAS_HIDDEN_MESSAGE_REQUESTS)
        }

        @JvmStatic
        fun isCallNotificationsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, CALL_NOTIFICATIONS_ENABLED, false)
        }

        @JvmStatic
        fun setShownCallWarning(context: Context): Boolean {
            val previousValue = getBooleanPreference(context, SHOWN_CALL_WARNING, false)
            if (previousValue) {
                return false
            }
            val setValue = true
            setBooleanPreference(context, SHOWN_CALL_WARNING, setValue)
            return previousValue != setValue
        }

        @JvmStatic
        fun getLastVacuumTime(context: Context): Long {
            return getLongPreference(context, LAST_VACUUM_TIME, 0)
        }

        @JvmStatic
        fun setLastVacuumNow(context: Context) {
            setLongPreference(context, LAST_VACUUM_TIME, System.currentTimeMillis())
        }

        @JvmStatic
        fun getFingerprintKeyGenerated(context: Context): Boolean {
            return getBooleanPreference(context, FINGERPRINT_KEY_GENERATED, false)
        }

        @JvmStatic
        fun setFingerprintKeyGenerated(context: Context) {
            setBooleanPreference(context, FINGERPRINT_KEY_GENERATED, true)
        }

        @JvmStatic @StyleRes
        fun getAccentColorStyle(context: Context): Int? {
            return when (getStringPreference(context, SELECTED_ACCENT_COLOR, ORANGE_ACCENT)) {
                GREEN_ACCENT -> R.style.PrimaryGreen
                BLUE_ACCENT -> R.style.PrimaryBlue
                PURPLE_ACCENT -> R.style.PrimaryPurple
                PINK_ACCENT -> R.style.PrimaryPink
                RED_ACCENT -> R.style.PrimaryRed
                ORANGE_ACCENT -> R.style.PrimaryOrange
                YELLOW_ACCENT -> R.style.PrimaryYellow
                else -> null
            }
        }

        @JvmStatic
        fun setAccentColorStyle(context: Context, @StyleRes newColor: Int?) {
            setStringPreference(context, SELECTED_ACCENT_COLOR, when (newColor) {
                R.style.PrimaryGreen -> GREEN_ACCENT
                R.style.PrimaryBlue -> BLUE_ACCENT
                R.style.PrimaryPurple -> PURPLE_ACCENT
                R.style.PrimaryPink -> PINK_ACCENT
                R.style.PrimaryRed -> RED_ACCENT
                R.style.PrimaryOrange -> ORANGE_ACCENT
                R.style.PrimaryYellow -> YELLOW_ACCENT
                else -> null
            })
        }

        @JvmStatic
        fun clearAll(context: Context) {
            getDefaultSharedPreferences(context).edit().clear().commit()
        }

    }
}

class AppTextSecurePreferences @Inject constructor(
    @ApplicationContext private val context: Context
): TextSecurePreferences {

    override fun getLastConfigurationSyncTime(): Long {
        return getLongPreference(TextSecurePreferences.LAST_CONFIGURATION_SYNC_TIME, 0)
    }

    override fun setLastConfigurationSyncTime(value: Long) {
        setLongPreference(TextSecurePreferences.LAST_CONFIGURATION_SYNC_TIME, value)
    }

    override fun getConfigurationMessageSynced(): Boolean {
        return getBooleanPreference(TextSecurePreferences.CONFIGURATION_SYNCED, false)
    }

    override fun setConfigurationMessageSynced(value: Boolean) {
        setBooleanPreference(TextSecurePreferences.CONFIGURATION_SYNCED, value)
        TextSecurePreferences._events.tryEmit(TextSecurePreferences.CONFIGURATION_SYNCED)
    }

    override fun isUsingFCM(): Boolean {
        return getBooleanPreference(TextSecurePreferences.IS_USING_FCM, false)
    }

    override fun setIsUsingFCM(value: Boolean) {
        setBooleanPreference(TextSecurePreferences.IS_USING_FCM, value)
    }

    override fun getFCMToken(): String? {
        return getStringPreference(TextSecurePreferences.FCM_TOKEN, "")
    }

    override fun setFCMToken(value: String) {
        setStringPreference(TextSecurePreferences.FCM_TOKEN, value)
    }

    override fun getLastFCMUploadTime(): Long {
        return getLongPreference(TextSecurePreferences.LAST_FCM_TOKEN_UPLOAD_TIME, 0)
    }

    override fun setLastFCMUploadTime(value: Long) {
        setLongPreference(TextSecurePreferences.LAST_FCM_TOKEN_UPLOAD_TIME, value)
    }

    override fun isScreenLockEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.SCREEN_LOCK, false)
    }

    override fun setScreenLockEnabled(value: Boolean) {
        setBooleanPreference(TextSecurePreferences.SCREEN_LOCK, value)
    }

    override fun getScreenLockTimeout(): Long {
        return getLongPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT, 0)
    }

    override fun setScreenLockTimeout(value: Long) {
        setLongPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT, value)
    }

    override fun setBackupPassphrase(passphrase: String?) {
        setStringPreference(TextSecurePreferences.BACKUP_PASSPHRASE, passphrase)
    }

    override fun getBackupPassphrase(): String? {
        return getStringPreference(TextSecurePreferences.BACKUP_PASSPHRASE, null)
    }

    override fun setEncryptedBackupPassphrase(encryptedPassphrase: String?) {
        setStringPreference(TextSecurePreferences.ENCRYPTED_BACKUP_PASSPHRASE, encryptedPassphrase)
    }

    override fun getEncryptedBackupPassphrase(): String? {
        return getStringPreference(TextSecurePreferences.ENCRYPTED_BACKUP_PASSPHRASE, null)
    }

    override fun setBackupEnabled(value: Boolean) {
        setBooleanPreference(TextSecurePreferences.BACKUP_ENABLED, value)
    }

    override fun isBackupEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.BACKUP_ENABLED, false)
    }

    override fun setNextBackupTime(time: Long) {
        setLongPreference(TextSecurePreferences.BACKUP_TIME, time)
    }

    override fun getNextBackupTime(): Long {
        return getLongPreference(TextSecurePreferences.BACKUP_TIME, -1)
    }

    override fun setBackupSaveDir(dirUri: String?) {
        setStringPreference(TextSecurePreferences.BACKUP_SAVE_DIR, dirUri)
    }

    override fun getBackupSaveDir(): String? {
        return getStringPreference(TextSecurePreferences.BACKUP_SAVE_DIR, null)
    }

    override fun getNeedsSqlCipherMigration(): Boolean {
        return getBooleanPreference(TextSecurePreferences.NEEDS_SQLCIPHER_MIGRATION, false)
    }

    override fun setAttachmentEncryptedSecret(secret: String) {
        setStringPreference(TextSecurePreferences.ATTACHMENT_ENCRYPTED_SECRET, secret)
    }

    override fun setAttachmentUnencryptedSecret(secret: String?) {
        setStringPreference(TextSecurePreferences.ATTACHMENT_UNENCRYPTED_SECRET, secret)
    }

    override fun getAttachmentEncryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.ATTACHMENT_ENCRYPTED_SECRET, null)
    }

    override fun getAttachmentUnencryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.ATTACHMENT_UNENCRYPTED_SECRET, null)
    }

    override fun setDatabaseEncryptedSecret(secret: String) {
        setStringPreference(TextSecurePreferences.DATABASE_ENCRYPTED_SECRET, secret)
    }

    override fun setDatabaseUnencryptedSecret(secret: String?) {
        setStringPreference(TextSecurePreferences.DATABASE_UNENCRYPTED_SECRET, secret)
    }

    override fun getDatabaseUnencryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.DATABASE_UNENCRYPTED_SECRET, null)
    }

    override fun getDatabaseEncryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.DATABASE_ENCRYPTED_SECRET, null)
    }

    override fun isIncognitoKeyboardEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.INCOGNITO_KEYBORAD_PREF, true)
    }

    override fun isReadReceiptsEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.READ_RECEIPTS_PREF, false)
    }

    override fun setReadReceiptsEnabled(enabled: Boolean) {
        setBooleanPreference(TextSecurePreferences.READ_RECEIPTS_PREF, enabled)
    }

    override fun isTypingIndicatorsEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.TYPING_INDICATORS, false)
    }

    override fun setTypingIndicatorsEnabled(enabled: Boolean) {
        setBooleanPreference(TextSecurePreferences.TYPING_INDICATORS, enabled)
    }

    override fun isLinkPreviewsEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.LINK_PREVIEWS, false)
    }

    override fun setLinkPreviewsEnabled(enabled: Boolean) {
        setBooleanPreference(TextSecurePreferences.LINK_PREVIEWS, enabled)
    }

    override fun hasSeenGIFMetaDataWarning(): Boolean {
        return getBooleanPreference(TextSecurePreferences.GIF_METADATA_WARNING, false)
    }

    override fun setHasSeenGIFMetaDataWarning() {
        setBooleanPreference(TextSecurePreferences.GIF_METADATA_WARNING, true)
    }

    override fun isGifSearchInGridLayout(): Boolean {
        return getBooleanPreference(TextSecurePreferences.GIF_GRID_LAYOUT, false)
    }

    override fun setIsGifSearchInGridLayout(isGrid: Boolean) {
        setBooleanPreference(TextSecurePreferences.GIF_GRID_LAYOUT, isGrid)
    }

    override fun getProfileKey(): String? {
        return getStringPreference(TextSecurePreferences.PROFILE_KEY_PREF, null)
    }

    override fun setProfileKey(key: String?) {
        setStringPreference(TextSecurePreferences.PROFILE_KEY_PREF, key)
    }

    override fun setProfileName(name: String?) {
        setStringPreference(TextSecurePreferences.PROFILE_NAME_PREF, name)
        TextSecurePreferences._events.tryEmit(TextSecurePreferences.PROFILE_NAME_PREF)
    }

    override fun getProfileName(): String? {
        return getStringPreference(TextSecurePreferences.PROFILE_NAME_PREF, null)
    }

    override fun setProfileAvatarId(id: Int) {
        setIntegerPreference(TextSecurePreferences.PROFILE_AVATAR_ID_PREF, id)
    }

    override fun getProfileAvatarId(): Int {
        return getIntegerPreference(TextSecurePreferences.PROFILE_AVATAR_ID_PREF, 0)
    }

    override fun setProfilePictureURL(url: String?) {
        setStringPreference(TextSecurePreferences.PROFILE_AVATAR_URL_PREF, url)
    }

    override fun getProfilePictureURL(): String? {
        return getStringPreference(TextSecurePreferences.PROFILE_AVATAR_URL_PREF, null)
    }

    override fun getNotificationPriority(): Int {
        return getStringPreference(
            TextSecurePreferences.NOTIFICATION_PRIORITY_PREF, NotificationCompat.PRIORITY_HIGH.toString())!!.toInt()
    }

    override fun getMessageBodyTextSize(): Int {
        return getStringPreference(TextSecurePreferences.MESSAGE_BODY_TEXT_SIZE_PREF, "16")!!.toInt()
    }

    override fun setDirectCaptureCameraId(value: Int) {
        setIntegerPreference(TextSecurePreferences.DIRECT_CAPTURE_CAMERA_ID, value)
    }

    override fun getDirectCaptureCameraId(): Int {
        return getIntegerPreference(TextSecurePreferences.DIRECT_CAPTURE_CAMERA_ID, Camera.CameraInfo.CAMERA_FACING_BACK)
    }

    override fun getNotificationPrivacy(): NotificationPrivacyPreference {
        return NotificationPrivacyPreference(getStringPreference(
            TextSecurePreferences.NOTIFICATION_PRIVACY_PREF, "all"))
    }

    override fun getRepeatAlertsCount(): Int {
        return try {
            getStringPreference(TextSecurePreferences.REPEAT_ALERTS_PREF, "0")!!.toInt()
        } catch (e: NumberFormatException) {
            Log.w(TextSecurePreferences.TAG, e)
            0
        }
    }

    override fun getLocalRegistrationId(): Int {
        return getIntegerPreference(TextSecurePreferences.LOCAL_REGISTRATION_ID_PREF, 0)
    }

    override fun setLocalRegistrationId(registrationId: Int) {
        setIntegerPreference(TextSecurePreferences.LOCAL_REGISTRATION_ID_PREF, registrationId)
    }

    override fun isInThreadNotifications(): Boolean {
        return getBooleanPreference(TextSecurePreferences.IN_THREAD_NOTIFICATION_PREF, true)
    }

    override fun isUniversalUnidentifiedAccess(): Boolean {
        return getBooleanPreference(TextSecurePreferences.UNIVERSAL_UNIDENTIFIED_ACCESS, false)
    }

    override fun getUpdateApkRefreshTime(): Long {
        return getLongPreference(TextSecurePreferences.UPDATE_APK_REFRESH_TIME_PREF, 0L)
    }

    override fun setUpdateApkRefreshTime(value: Long) {
        setLongPreference(TextSecurePreferences.UPDATE_APK_REFRESH_TIME_PREF, value)
    }

    override fun setUpdateApkDownloadId(value: Long) {
        setLongPreference(TextSecurePreferences.UPDATE_APK_DOWNLOAD_ID, value)
    }

    override fun getUpdateApkDownloadId(): Long {
        return getLongPreference(TextSecurePreferences.UPDATE_APK_DOWNLOAD_ID, -1)
    }

    override fun setUpdateApkDigest(value: String?) {
        setStringPreference(TextSecurePreferences.UPDATE_APK_DIGEST, value)
    }

    override fun getUpdateApkDigest(): String? {
        return getStringPreference(TextSecurePreferences.UPDATE_APK_DIGEST, null)
    }

    override fun getLocalNumber(): String? {
        return getStringPreference(TextSecurePreferences.LOCAL_NUMBER_PREF, null)
    }

    override fun setLocalNumber(localNumber: String) {
        setStringPreference(TextSecurePreferences.LOCAL_NUMBER_PREF, localNumber.toLowerCase())
    }

    override fun removeLocalNumber() {
        removePreference(TextSecurePreferences.LOCAL_NUMBER_PREF)
    }

    override fun isEnterSendsEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.ENTER_SENDS_PREF, false)
    }

    override fun isPasswordDisabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.DISABLE_PASSPHRASE_PREF, true)
    }

    override fun setPasswordDisabled(disabled: Boolean) {
        setBooleanPreference(TextSecurePreferences.DISABLE_PASSPHRASE_PREF, disabled)
    }

    override fun isScreenSecurityEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.SCREEN_SECURITY_PREF, true)
    }

    override fun getLastVersionCode(): Int {
        return getIntegerPreference(TextSecurePreferences.LAST_VERSION_CODE_PREF, 0)
    }

    @Throws(IOException::class)
    override fun setLastVersionCode(versionCode: Int) {
        if (!setIntegerPreferenceBlocking(TextSecurePreferences.LAST_VERSION_CODE_PREF, versionCode)) {
            throw IOException("couldn't write version code to sharedpreferences")
        }
    }

    override fun isPassphraseTimeoutEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_PREF, false)
    }

    override fun getPassphraseTimeoutInterval(): Int {
        return getIntegerPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_INTERVAL_PREF, 5 * 60)
    }

    override fun getLanguage(): String? {
        return getStringPreference(TextSecurePreferences.LANGUAGE_PREF, "zz")
    }

    override fun hasSeenWelcomeScreen(): Boolean {
        return getBooleanPreference(TextSecurePreferences.SEEN_WELCOME_SCREEN_PREF, false)
    }

    override fun setHasSeenWelcomeScreen(value: Boolean) {
        setBooleanPreference(TextSecurePreferences.SEEN_WELCOME_SCREEN_PREF, value)
    }

    override fun isNotificationsEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.NOTIFICATION_PREF, true)
    }

    override fun getNotificationRingtone(): Uri {
        var result = getStringPreference(TextSecurePreferences.RINGTONE_PREF, Settings.System.DEFAULT_NOTIFICATION_URI.toString())
        if (result != null && result.startsWith("file:")) {
            result = Settings.System.DEFAULT_NOTIFICATION_URI.toString()
        }
        return Uri.parse(result)
    }

    override fun removeNotificationRingtone() {
        removePreference(TextSecurePreferences.RINGTONE_PREF)
    }

    override fun setNotificationRingtone(ringtone: String?) {
        setStringPreference(TextSecurePreferences.RINGTONE_PREF, ringtone)
    }

    override fun setNotificationVibrateEnabled(enabled: Boolean) {
        setBooleanPreference(TextSecurePreferences.VIBRATE_PREF, enabled)
    }

    override fun isNotificationVibrateEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.VIBRATE_PREF, true)
    }

    override fun getNotificationLedColor(): Int {
        return getIntegerPreference(TextSecurePreferences.LED_COLOR_PREF_PRIMARY, context.getColor(R.color.accent_green))
    }

    override fun isThreadLengthTrimmingEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.THREAD_TRIM_ENABLED, true)
    }

    override fun isSystemEmojiPreferred(): Boolean {
        return getBooleanPreference(TextSecurePreferences.SYSTEM_EMOJI_PREF, false)
    }

    override fun getMobileMediaDownloadAllowed(): Set<String>? {
        return getMediaDownloadAllowed(TextSecurePreferences.MEDIA_DOWNLOAD_MOBILE_PREF, R.array.pref_media_download_mobile_data_default)
    }

    override fun getWifiMediaDownloadAllowed(): Set<String>? {
        return getMediaDownloadAllowed(TextSecurePreferences.MEDIA_DOWNLOAD_WIFI_PREF, R.array.pref_media_download_wifi_default)
    }

    override fun getRoamingMediaDownloadAllowed(): Set<String>? {
        return getMediaDownloadAllowed(TextSecurePreferences.MEDIA_DOWNLOAD_ROAMING_PREF, R.array.pref_media_download_roaming_default)
    }

    override fun getMediaDownloadAllowed(key: String, @ArrayRes defaultValuesRes: Int): Set<String>? {
        return getStringSetPreference(key, HashSet(listOf(*context.resources.getStringArray(defaultValuesRes))))
    }

    override fun getLogEncryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.LOG_ENCRYPTED_SECRET, null)
    }

    override fun setLogEncryptedSecret(base64Secret: String?) {
        setStringPreference(TextSecurePreferences.LOG_ENCRYPTED_SECRET, base64Secret)
    }

    override fun getLogUnencryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.LOG_UNENCRYPTED_SECRET, null)
    }

    override fun setLogUnencryptedSecret(base64Secret: String?) {
        setStringPreference(TextSecurePreferences.LOG_UNENCRYPTED_SECRET, base64Secret)
    }

    override fun getNotificationChannelVersion(): Int {
        return getIntegerPreference(TextSecurePreferences.NOTIFICATION_CHANNEL_VERSION, 1)
    }

    override fun setNotificationChannelVersion(version: Int) {
        setIntegerPreference(TextSecurePreferences.NOTIFICATION_CHANNEL_VERSION, version)
    }

    override fun getNotificationMessagesChannelVersion(): Int {
        return getIntegerPreference(TextSecurePreferences.NOTIFICATION_MESSAGES_CHANNEL_VERSION, 1)
    }

    override fun setNotificationMessagesChannelVersion(version: Int) {
        setIntegerPreference(TextSecurePreferences.NOTIFICATION_MESSAGES_CHANNEL_VERSION, version)
    }

    override fun getBooleanPreference(key: String?, defaultValue: Boolean): Boolean {
        return getDefaultSharedPreferences(context).getBoolean(key, defaultValue)
    }

    override fun setBooleanPreference(key: String?, value: Boolean) {
        getDefaultSharedPreferences(context).edit().putBoolean(key, value).apply()
    }

    override fun getStringPreference(key: String, defaultValue: String?): String? {
        return getDefaultSharedPreferences(context).getString(key, defaultValue)
    }

    override fun setStringPreference(key: String?, value: String?) {
        getDefaultSharedPreferences(context).edit().putString(key, value).apply()
    }

    override fun getIntegerPreference(key: String, defaultValue: Int): Int {
        return getDefaultSharedPreferences(context).getInt(key, defaultValue)
    }

    override fun setIntegerPreference(key: String, value: Int) {
        getDefaultSharedPreferences(context).edit().putInt(key, value).apply()
    }

    override fun setIntegerPreferenceBlocking(key: String, value: Int): Boolean {
        return getDefaultSharedPreferences(context).edit().putInt(key, value).commit()
    }

    override fun getLongPreference(key: String, defaultValue: Long): Long {
        return getDefaultSharedPreferences(context).getLong(key, defaultValue)
    }

    override fun setLongPreference(key: String, value: Long) {
        getDefaultSharedPreferences(context).edit().putLong(key, value).apply()
    }

    override fun hasPreference(key: String): Boolean {
        return getDefaultSharedPreferences(context).contains(key)
    }

    override fun removePreference(key: String) {
        getDefaultSharedPreferences(context).edit().remove(key).apply()
    }

    override fun getStringSetPreference(key: String, defaultValues: Set<String>): Set<String>? {
        val prefs = getDefaultSharedPreferences(context)
        return if (prefs.contains(key)) {
            prefs.getStringSet(key, emptySet())
        } else {
            defaultValues
        }
    }

    override fun getHasViewedSeed(): Boolean {
        return getBooleanPreference("has_viewed_seed", false)
    }

    override fun setHasViewedSeed(hasViewedSeed: Boolean) {
        setBooleanPreference("has_viewed_seed", hasViewedSeed)
    }

    override fun setRestorationTime(time: Long) {
        setLongPreference("restoration_time", time)
    }

    override fun getRestorationTime(): Long {
        return getLongPreference("restoration_time", 0)
    }

    override fun getLastProfilePictureUpload(): Long {
        return getLongPreference("last_profile_picture_upload", 0)
    }

    override fun setLastProfilePictureUpload(newValue: Long) {
        setLongPreference("last_profile_picture_upload", newValue)
    }

    override fun getLastSnodePoolRefreshDate(): Long {
        return getLongPreference("last_snode_pool_refresh_date", 0)
    }

    override fun setLastSnodePoolRefreshDate(date: Date) {
        setLongPreference("last_snode_pool_refresh_date", date.time)
    }

    override fun shouldUpdateProfile(profileUpdateTime: Long): Boolean {
        return profileUpdateTime > getLongPreference(TextSecurePreferences.LAST_PROFILE_UPDATE_TIME, 0)
    }

    override fun setLastProfileUpdateTime(profileUpdateTime: Long) {
        setLongPreference(TextSecurePreferences.LAST_PROFILE_UPDATE_TIME, profileUpdateTime)
    }

    override fun getLastOpenTimeDate(): Long {
        return getLongPreference(TextSecurePreferences.LAST_OPEN_DATE, 0)
    }

    override fun setLastOpenDate() {
        setLongPreference(TextSecurePreferences.LAST_OPEN_DATE, System.currentTimeMillis())
    }

    override fun hasSeenLinkPreviewSuggestionDialog(): Boolean {
        return getBooleanPreference("has_seen_link_preview_suggestion_dialog", false)
    }

    override fun setHasSeenLinkPreviewSuggestionDialog() {
        setBooleanPreference("has_seen_link_preview_suggestion_dialog", true)
    }

    override fun isCallNotificationsEnabled(): Boolean {
        return getBooleanPreference(CALL_NOTIFICATIONS_ENABLED, false)
    }

    override fun getLastVacuum(): Long {
        return getLongPreference(LAST_VACUUM_TIME, 0)
    }

    override fun setLastVacuumNow() {
        setLongPreference(LAST_VACUUM_TIME, System.currentTimeMillis())
    }

    override fun setShownCallNotification(): Boolean {
        val previousValue = getBooleanPreference(SHOWN_CALL_NOTIFICATION, false)
        if (previousValue) return false
        val setValue = true
        setBooleanPreference(SHOWN_CALL_NOTIFICATION, setValue)
        return previousValue != setValue
    }


    /**
     * Set the SHOWN_CALL_WARNING preference to `true`
     * Return `true` if the value did update (it was previously unset)
     */
    override fun setShownCallWarning() : Boolean {
        val previousValue = getBooleanPreference(SHOWN_CALL_WARNING, false)
        if (previousValue) {
            return false
        }
        val setValue = true
        setBooleanPreference(SHOWN_CALL_WARNING, setValue)
        return previousValue != setValue
    }

    override fun hasHiddenMessageRequests(): Boolean {
        return getBooleanPreference(TextSecurePreferences.HAS_HIDDEN_MESSAGE_REQUESTS, false)
    }

    override fun setHasHiddenMessageRequests() {
        setBooleanPreference(TextSecurePreferences.HAS_HIDDEN_MESSAGE_REQUESTS, true)
    }

    override fun getFingerprintKeyGenerated(): Boolean {
        return getBooleanPreference(TextSecurePreferences.FINGERPRINT_KEY_GENERATED, false)
    }

    override fun setFingerprintKeyGenerated() {
        setBooleanPreference(TextSecurePreferences.FINGERPRINT_KEY_GENERATED, true)
    }

    @StyleRes
    override fun getAccentColorStyle(): Int? {
        val prefColor = getStringPreference(
            TextSecurePreferences.SELECTED_ACCENT_COLOR,
            null
        )
        return when (prefColor) {
            TextSecurePreferences.GREEN_ACCENT -> R.style.PrimaryGreen
            TextSecurePreferences.BLUE_ACCENT -> R.style.PrimaryBlue
            TextSecurePreferences.PURPLE_ACCENT -> R.style.PrimaryPurple
            TextSecurePreferences.PINK_ACCENT -> R.style.PrimaryPink
            TextSecurePreferences.RED_ACCENT -> R.style.PrimaryRed
            TextSecurePreferences.ORANGE_ACCENT -> R.style.PrimaryOrange
            TextSecurePreferences.YELLOW_ACCENT -> R.style.PrimaryYellow
            else -> null
        }
    }

    override fun setAccentColorStyle(@StyleRes newColorStyle: Int?) {
        setStringPreference(
            TextSecurePreferences.SELECTED_ACCENT_COLOR, when (newColorStyle) {
                R.style.PrimaryGreen -> TextSecurePreferences.GREEN_ACCENT
                R.style.PrimaryBlue -> TextSecurePreferences.BLUE_ACCENT
                R.style.PrimaryPurple -> TextSecurePreferences.PURPLE_ACCENT
                R.style.PrimaryPink -> TextSecurePreferences.PINK_ACCENT
                R.style.PrimaryRed -> TextSecurePreferences.RED_ACCENT
                R.style.PrimaryOrange -> TextSecurePreferences.ORANGE_ACCENT
                R.style.PrimaryYellow -> TextSecurePreferences.YELLOW_ACCENT
                else -> null
            }
        )
    }

    override fun getThemeStyle(): String {
        val hasLegacy = getStringPreference(LEGACY_PREF_KEY_SELECTED_UI_MODE, null)
        if (!hasLegacy.isNullOrEmpty()) {
            migrateLegacyUiPref()
        }

        return getStringPreference(SELECTED_STYLE, CLASSIC_DARK)!!
    }

    override fun setThemeStyle(themeStyle: String) {
        val safeTheme = if (themeStyle !in listOf(CLASSIC_DARK, CLASSIC_LIGHT, OCEAN_DARK, OCEAN_LIGHT)) CLASSIC_DARK else themeStyle
        setStringPreference(SELECTED_STYLE, safeTheme)
    }

    override fun getFollowSystemSettings(): Boolean {
        val hasLegacy = getStringPreference(LEGACY_PREF_KEY_SELECTED_UI_MODE, null)
        if (!hasLegacy.isNullOrEmpty()) {
            migrateLegacyUiPref()
        }

        return getBooleanPreference(FOLLOW_SYSTEM_SETTINGS, false)
    }

    private fun migrateLegacyUiPref() {
        val legacy = getStringPreference(LEGACY_PREF_KEY_SELECTED_UI_MODE, null) ?: return
        val (mode, followSystem) = when (legacy) {
            "DAY" -> {
                CLASSIC_LIGHT to false
            }
            "NIGHT" -> {
                CLASSIC_DARK to false
            }
            "SYSTEM_DEFAULT" -> {
                CLASSIC_DARK to true
            }
            else -> {
                CLASSIC_DARK to false
            }
        }
        if (!hasPreference(FOLLOW_SYSTEM_SETTINGS) && !hasPreference(SELECTED_STYLE)) {
            setThemeStyle(mode)
            setFollowSystemSettings(followSystem)
        }
        removePreference(LEGACY_PREF_KEY_SELECTED_UI_MODE)
    }

    override fun setFollowSystemSettings(followSystemSettings: Boolean) {
        setBooleanPreference(FOLLOW_SYSTEM_SETTINGS, followSystemSettings)
    }

    override fun autoplayAudioMessages(): Boolean {
        return getBooleanPreference(AUTOPLAY_AUDIO_MESSAGES, false)
    }

    override fun clearAll() {
        getDefaultSharedPreferences(context).edit().clear().commit()
    }

}