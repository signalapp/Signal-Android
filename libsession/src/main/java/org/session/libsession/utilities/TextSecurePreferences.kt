package org.session.libsession.utilities

import android.content.Context
import android.hardware.Camera
import android.net.Uri
import android.preference.PreferenceManager.getDefaultSharedPreferences
import android.provider.Settings
import androidx.annotation.ArrayRes
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.session.libsession.R
import org.session.libsignal.utilities.Log
import java.io.IOException
import java.util.*

object TextSecurePreferences {
    private val TAG = TextSecurePreferences::class.simpleName

    private val _events = MutableSharedFlow<String>(0, 64, BufferOverflow.DROP_OLDEST)
    val events get() = _events.asSharedFlow()

    const val DISABLE_PASSPHRASE_PREF = "pref_disable_passphrase"
    const val THEME_PREF = "pref_theme"
    const val LANGUAGE_PREF = "pref_language"
    const val THREAD_TRIM_LENGTH = "pref_trim_length"
    const val THREAD_TRIM_NOW = "pref_trim_now"

    private const val LAST_VERSION_CODE_PREF = "last_version_code"
    private const val LAST_EXPERIENCE_VERSION_PREF = "last_experience_version_code"
    const val RINGTONE_PREF = "pref_key_ringtone"
    const val VIBRATE_PREF = "pref_key_vibrate"
    private const val NOTIFICATION_PREF = "pref_key_enable_notifications"
    const val LED_COLOR_PREF = "pref_led_color"
    const val LED_BLINK_PREF = "pref_led_blink"
    private const val LED_BLINK_PREF_CUSTOM = "pref_led_blink_custom"
    const val PASSPHRASE_TIMEOUT_INTERVAL_PREF = "pref_timeout_interval"
    const val PASSPHRASE_TIMEOUT_PREF = "pref_timeout_passphrase"
    const val SCREEN_SECURITY_PREF = "pref_screen_security"
    private const val ENTER_SENDS_PREF = "pref_enter_sends"
    private const val THREAD_TRIM_ENABLED = "pref_trim_threads"
    const val LOCAL_NUMBER_PREF = "pref_local_number"
    const val REGISTERED_GCM_PREF = "pref_gcm_registered"
    private const val SEEN_WELCOME_SCREEN_PREF = "pref_seen_welcome_screen"
    private const val UPDATE_APK_REFRESH_TIME_PREF = "pref_update_apk_refresh_time"
    private const val UPDATE_APK_DOWNLOAD_ID = "pref_update_apk_download_id"
    private const val UPDATE_APK_DIGEST = "pref_update_apk_digest"

    private const val IN_THREAD_NOTIFICATION_PREF = "pref_key_inthread_notifications"
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

    private const val DATABASE_ENCRYPTED_SECRET = "pref_database_encrypted_secret"
    private const val DATABASE_UNENCRYPTED_SECRET = "pref_database_unencrypted_secret"
    private const val ATTACHMENT_ENCRYPTED_SECRET = "pref_attachment_encrypted_secret"
    private const val ATTACHMENT_UNENCRYPTED_SECRET = "pref_attachment_unencrypted_secret"
    private const val NEEDS_SQLCIPHER_MIGRATION = "pref_needs_sql_cipher_migration"

    const val BACKUP_ENABLED = "pref_backup_enabled_v3"
    private const val BACKUP_PASSPHRASE = "pref_backup_passphrase"
    private const val ENCRYPTED_BACKUP_PASSPHRASE = "pref_encrypted_backup_passphrase"
    private const val BACKUP_TIME = "pref_backup_next_time"
    const val BACKUP_NOW = "pref_backup_create"
    private const val BACKUP_SAVE_DIR = "pref_save_dir"

    const val SCREEN_LOCK = "pref_android_screen_lock"
    const val SCREEN_LOCK_TIMEOUT = "pref_android_screen_lock_timeout"

    private const val LOG_ENCRYPTED_SECRET = "pref_log_encrypted_secret"
    private const val LOG_UNENCRYPTED_SECRET = "pref_log_unencrypted_secret"

    private const val NOTIFICATION_CHANNEL_VERSION = "pref_notification_channel_version"
    private const val NOTIFICATION_MESSAGES_CHANNEL_VERSION = "pref_notification_messages_channel_version"

    const val UNIVERSAL_UNIDENTIFIED_ACCESS = "pref_universal_unidentified_access"

    const val TYPING_INDICATORS = "pref_typing_indicators"

    const val LINK_PREVIEWS = "pref_link_previews"

    private const val GIF_GRID_LAYOUT = "pref_gif_grid_layout"

    const val IS_USING_FCM = "pref_is_using_fcm"
    private const val FCM_TOKEN = "pref_fcm_token"
    private const val LAST_FCM_TOKEN_UPLOAD_TIME = "pref_last_fcm_token_upload_time_2"

    private const val LAST_CONFIGURATION_SYNC_TIME = "pref_last_configuration_sync_time"
    const val CONFIGURATION_SYNCED = "pref_configuration_synced"
    private const val LAST_PROFILE_UPDATE_TIME = "pref_last_profile_update_time"

    private const val LAST_OPEN_DATE = "pref_last_open_date"

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
        setIntegerPrefrence(context, PROFILE_AVATAR_ID_PREF, id)
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
        setIntegerPrefrence(context, DIRECT_CAPTURE_CAMERA_ID, value)
    }

    @JvmStatic
    fun getDirectCaptureCameraId(context: Context): Int {
        return getIntegerPreference(context, DIRECT_CAPTURE_CAMERA_ID, Camera.CameraInfo.CAMERA_FACING_FRONT)
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
        setIntegerPrefrence(context, LOCAL_REGISTRATION_ID_PREF, registrationId)
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
        return getBooleanPreference(context, SCREEN_SECURITY_PREF, true)
    }

    fun getLastVersionCode(context: Context): Int {
        return getIntegerPreference(context, LAST_VERSION_CODE_PREF, 0)
    }

    @Throws(IOException::class)
    fun setLastVersionCode(context: Context, versionCode: Int) {
        if (!setIntegerPrefrenceBlocking(context, LAST_VERSION_CODE_PREF, versionCode)) {
            throw IOException("couldn't write version code to sharedpreferences")
        }
    }

    fun setLastExperienceVersionCode(context: Context, versionCode: Int) {
        setIntegerPrefrence(context, LAST_EXPERIENCE_VERSION_PREF, versionCode)
    }

    fun getTheme(context: Context): String? {
        return getStringPreference(context, THEME_PREF, "light")
    }

    @JvmStatic
    fun isPushRegistered(context: Context): Boolean {
        return getBooleanPreference(context, REGISTERED_GCM_PREF, false)
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
    fun getNotificationLedColor(context: Context): String? {
        return getStringPreference(context, LED_COLOR_PREF, "blue")
    }

    @JvmStatic
    fun getNotificationLedPattern(context: Context): String? {
        return getStringPreference(context, LED_BLINK_PREF, "500,2000")
    }

    @JvmStatic
    fun getNotificationLedPatternCustom(context: Context): String? {
        return getStringPreference(context, LED_BLINK_PREF_CUSTOM, "500,2000")
    }

    @JvmStatic
    fun isThreadLengthTrimmingEnabled(context: Context): Boolean {
        return getBooleanPreference(context, THREAD_TRIM_ENABLED, false)
    }

    @JvmStatic
    fun getThreadTrimLength(context: Context): Int {
        return getStringPreference(context, THREAD_TRIM_LENGTH, "500")!!.toInt()
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
    fun setLogEncryptedSecret(context: Context, base64Secret: String?) {
        setStringPreference(context, LOG_ENCRYPTED_SECRET, base64Secret)
    }

    @JvmStatic
    fun getLogEncryptedSecret(context: Context): String? {
        return getStringPreference(context, LOG_ENCRYPTED_SECRET, null)
    }

    @JvmStatic
    fun setLogUnencryptedSecret(context: Context, base64Secret: String?) {
        setStringPreference(context, LOG_UNENCRYPTED_SECRET, base64Secret)
    }

    @JvmStatic
    fun getLogUnencryptedSecret(context: Context): String? {
        return getStringPreference(context, LOG_UNENCRYPTED_SECRET, null)
    }

    @JvmStatic
    fun getNotificationChannelVersion(context: Context): Int {
        return getIntegerPreference(context, NOTIFICATION_CHANNEL_VERSION, 1)
    }

    @JvmStatic
    fun setNotificationChannelVersion(context: Context, version: Int) {
        setIntegerPrefrence(context, NOTIFICATION_CHANNEL_VERSION, version)
    }

    @JvmStatic
    fun getNotificationMessagesChannelVersion(context: Context): Int {
        return getIntegerPreference(context, NOTIFICATION_MESSAGES_CHANNEL_VERSION, 1)
    }

    @JvmStatic
    fun setNotificationMessagesChannelVersion(context: Context, version: Int) {
        setIntegerPrefrence(context, NOTIFICATION_MESSAGES_CHANNEL_VERSION, version)
    }

    @JvmStatic
    fun setBooleanPreference(context: Context, key: String?, value: Boolean) {
        getDefaultSharedPreferences(context).edit().putBoolean(key, value).apply()
    }

    @JvmStatic
    fun getBooleanPreference(context: Context, key: String?, defaultValue: Boolean): Boolean {
        return getDefaultSharedPreferences(context).getBoolean(key, defaultValue)
    }

    @JvmStatic
    fun setStringPreference(context: Context, key: String?, value: String?) {
        getDefaultSharedPreferences(context).edit().putString(key, value).apply()
    }

    @JvmStatic
    fun getStringPreference(context: Context, key: String, defaultValue: String?): String? {
        return getDefaultSharedPreferences(context).getString(key, defaultValue)
    }

    private fun getIntegerPreference(context: Context, key: String, defaultValue: Int): Int {
        return getDefaultSharedPreferences(context).getInt(key, defaultValue)
    }

    private fun setIntegerPrefrence(context: Context, key: String, value: Int) {
        getDefaultSharedPreferences(context).edit().putInt(key, value).apply()
    }

    private fun setIntegerPrefrenceBlocking(context: Context, key: String, value: Int): Boolean {
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

    // region Loki
    @JvmStatic

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

    @JvmStatic
    fun hasSeenGIFMetaDataWarning(context: Context): Boolean {
        return getBooleanPreference(context, "has_seen_gif_metadata_warning", false)
    }

    @JvmStatic
    fun setHasSeenGIFMetaDataWarning(context: Context) {
        setBooleanPreference(context, "has_seen_gif_metadata_warning", true)
    }

    @JvmStatic
    fun clearAll(context: Context) {
        getDefaultSharedPreferences(context).edit().clear().commit()
    }

    fun getLastSnodePoolRefreshDate(context: Context?): Long {
        return getLongPreference(context!!, "last_snode_pool_refresh_date", 0)
    }

    fun setLastSnodePoolRefreshDate(context: Context?, date: Date) {
        setLongPreference(context!!, "last_snode_pool_refresh_date", date.time)
    }

    fun getIsMigratingKeyPair(context: Context?): Boolean {
        return getBooleanPreference(context!!, "is_migrating_key_pair", false)
    }

    @JvmStatic
    fun setIsMigratingKeyPair(context: Context?, newValue: Boolean) {
        setBooleanPreference(context!!, "is_migrating_key_pair", newValue)
    }

    @JvmStatic
    fun setLastProfileUpdateTime(context: Context, profileUpdateTime: Long) {
        setLongPreference(context, LAST_PROFILE_UPDATE_TIME, profileUpdateTime)
    }

    @JvmStatic
    fun shouldUpdateProfile(context: Context, profileUpdateTime: Long) =
            profileUpdateTime > getLongPreference(context, LAST_PROFILE_UPDATE_TIME, 0)

    fun hasPerformedContactMigration(context: Context): Boolean {
        return getBooleanPreference(context, "has_performed_contact_migration", false)
    }

    fun setPerformedContactMigration(context: Context) {
        setBooleanPreference(context, "has_performed_contact_migration", true)
    }

    fun getLastOpenTimeDate(context: Context): Long {
        return getLongPreference(context, LAST_OPEN_DATE, 0)
    }

    fun setLastOpenDate(context: Context) {
        setLongPreference(context, LAST_OPEN_DATE, System.currentTimeMillis())
    }
}