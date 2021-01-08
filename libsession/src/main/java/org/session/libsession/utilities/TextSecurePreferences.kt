package org.session.libsession.utilities

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Camera
import android.net.Uri
import android.os.Build
import android.preference.PreferenceManager.*
import android.provider.Settings
import androidx.annotation.ArrayRes
import androidx.core.app.NotificationCompat
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.libsignal.util.Medium
import org.session.libsignal.service.internal.util.Base64
import java.io.IOException
import java.security.SecureRandom
import java.util.*

object TextSecurePreferences {
    private val TAG = TextSecurePreferences::class.simpleName

    const val IDENTITY_PREF = "pref_choose_identity"
    const val CHANGE_PASSPHRASE_PREF = "pref_change_passphrase"
    const val DISABLE_PASSPHRASE_PREF = "pref_disable_passphrase"
    const val THEME_PREF = "pref_theme"
    const val LANGUAGE_PREF = "pref_language"
    private const val MMSC_CUSTOM_HOST_PREF = "pref_apn_mmsc_custom_host"
    const val MMSC_HOST_PREF = "pref_apn_mmsc_host"
    private const val MMSC_CUSTOM_PROXY_PREF = "pref_apn_mms_custom_proxy"
    const val MMSC_PROXY_HOST_PREF = "pref_apn_mms_proxy"
    private const val MMSC_CUSTOM_PROXY_PORT_PREF = "pref_apn_mms_custom_proxy_port"
    const val MMSC_PROXY_PORT_PREF = "pref_apn_mms_proxy_port"
    private const val MMSC_CUSTOM_USERNAME_PREF = "pref_apn_mmsc_custom_username"
    const val MMSC_USERNAME_PREF = "pref_apn_mmsc_username"
    private const val MMSC_CUSTOM_PASSWORD_PREF = "pref_apn_mmsc_custom_password"
    const val MMSC_PASSWORD_PREF = "pref_apn_mmsc_password"
    const val THREAD_TRIM_LENGTH = "pref_trim_length"
    const val THREAD_TRIM_NOW = "pref_trim_now"
    const val ENABLE_MANUAL_MMS_PREF = "pref_enable_manual_mms"

    private const val LAST_VERSION_CODE_PREF = "last_version_code"
    private const val LAST_EXPERIENCE_VERSION_PREF = "last_experience_version_code"
    private const val EXPERIENCE_DISMISSED_PREF = "experience_dismissed"
    const val RINGTONE_PREF = "pref_key_ringtone"
    const val VIBRATE_PREF = "pref_key_vibrate"
    private const val NOTIFICATION_PREF = "pref_key_enable_notifications"
    const val LED_COLOR_PREF = "pref_led_color"
    const val LED_BLINK_PREF = "pref_led_blink"
    private const val LED_BLINK_PREF_CUSTOM = "pref_led_blink_custom"
    const val ALL_MMS_PREF = "pref_all_mms"
    const val ALL_SMS_PREF = "pref_all_sms"
    const val PASSPHRASE_TIMEOUT_INTERVAL_PREF = "pref_timeout_interval"
    const val PASSPHRASE_TIMEOUT_PREF = "pref_timeout_passphrase"
    const val SCREEN_SECURITY_PREF = "pref_screen_security"
    private const val ENTER_SENDS_PREF = "pref_enter_sends"
    private const val ENTER_PRESENT_PREF = "pref_enter_key"
    private const val SMS_DELIVERY_REPORT_PREF = "pref_delivery_report_sms"
    const val MMS_USER_AGENT = "pref_mms_user_agent"
    private const val MMS_CUSTOM_USER_AGENT = "pref_custom_mms_user_agent"
    private const val THREAD_TRIM_ENABLED = "pref_trim_threads"
    private const val LOCAL_NUMBER_PREF = "pref_local_number"
    private const val VERIFYING_STATE_PREF = "pref_verifying"
    const val REGISTERED_GCM_PREF = "pref_gcm_registered"
    private const val GCM_PASSWORD_PREF = "pref_gcm_password"
    private const val SEEN_WELCOME_SCREEN_PREF = "pref_seen_welcome_screen"
    private const val PROMPTED_PUSH_REGISTRATION_PREF = "pref_prompted_push_registration"
    private const val PROMPTED_DEFAULT_SMS_PREF = "pref_prompted_default_sms"
    private const val PROMPTED_OPTIMIZE_DOZE_PREF = "pref_prompted_optimize_doze"
    private const val PROMPTED_SHARE_PREF = "pref_prompted_share"
    private const val SIGNALING_KEY_PREF = "pref_signaling_key"
    private const val DIRECTORY_FRESH_TIME_PREF = "pref_directory_refresh_time"
    private const val UPDATE_APK_REFRESH_TIME_PREF = "pref_update_apk_refresh_time"
    private const val UPDATE_APK_DOWNLOAD_ID = "pref_update_apk_download_id"
    private const val UPDATE_APK_DIGEST = "pref_update_apk_digest"
    private const val SIGNED_PREKEY_ROTATION_TIME_PREF = "pref_signed_pre_key_rotation_time"

    private const val IN_THREAD_NOTIFICATION_PREF = "pref_key_inthread_notifications"
    private const val SHOW_INVITE_REMINDER_PREF = "pref_show_invite_reminder"
    const val MESSAGE_BODY_TEXT_SIZE_PREF = "pref_message_body_text_size"

    private const val LOCAL_REGISTRATION_ID_PREF = "pref_local_registration_id"
    private const val SIGNED_PREKEY_REGISTERED_PREF = "pref_signed_prekey_registered"
    private const val WIFI_SMS_PREF = "pref_wifi_sms"

    private const val GCM_DISABLED_PREF = "pref_gcm_disabled"
    private const val GCM_REGISTRATION_ID_PREF = "pref_gcm_registration_id"
    private const val GCM_REGISTRATION_ID_VERSION_PREF = "pref_gcm_registration_id_version"
    private const val GCM_REGISTRATION_ID_TIME_PREF = "pref_gcm_registration_id_last_set_time"
    private const val WEBSOCKET_REGISTERED_PREF = "pref_websocket_registered"
    private const val RATING_LATER_PREF = "pref_rating_later"
    private const val RATING_ENABLED_PREF = "pref_rating_enabled"
    private const val SIGNED_PREKEY_FAILURE_COUNT_PREF = "pref_signed_prekey_failure_count"

    const val REPEAT_ALERTS_PREF = "pref_repeat_alerts"
    const val NOTIFICATION_PRIVACY_PREF = "pref_notification_privacy"
    const val NOTIFICATION_PRIORITY_PREF = "pref_notification_priority"
    const val NEW_CONTACTS_NOTIFICATIONS = "pref_enable_new_contacts_notifications"
    const val WEBRTC_CALLING_PREF = "pref_webrtc_calling"

    const val MEDIA_DOWNLOAD_MOBILE_PREF = "pref_media_download_mobile"
    const val MEDIA_DOWNLOAD_WIFI_PREF = "pref_media_download_wifi"
    const val MEDIA_DOWNLOAD_ROAMING_PREF = "pref_media_download_roaming"

    const val SYSTEM_EMOJI_PREF = "pref_system_emoji"
    private const val MULTI_DEVICE_PROVISIONED_PREF = "pref_multi_device"
    const val DIRECT_CAPTURE_CAMERA_ID = "pref_direct_capture_camera_id"
    private const val ALWAYS_RELAY_CALLS_PREF = "pref_turn_only"
    private const val PROFILE_KEY_PREF = "pref_profile_key"
    private const val PROFILE_NAME_PREF = "pref_profile_name"
    private const val PROFILE_AVATAR_ID_PREF = "pref_profile_avatar_id"
    private const val PROFILE_AVATAR_URL_PREF = "pref_profile_avatar_url"
    const val READ_RECEIPTS_PREF = "pref_read_receipts"
    const val INCOGNITO_KEYBORAD_PREF = "pref_incognito_keyboard"
    private const val UNAUTHORIZED_RECEIVED = "pref_unauthorized_received"
    private const val SUCCESSFUL_DIRECTORY_PREF = "pref_successful_directory"

    private const val DATABASE_ENCRYPTED_SECRET = "pref_database_encrypted_secret"
    private const val DATABASE_UNENCRYPTED_SECRET = "pref_database_unencrypted_secret"
    private const val ATTACHMENT_ENCRYPTED_SECRET = "pref_attachment_encrypted_secret"
    private const val ATTACHMENT_UNENCRYPTED_SECRET = "pref_attachment_unencrypted_secret"
    private const val NEEDS_SQLCIPHER_MIGRATION = "pref_needs_sql_cipher_migration"

    private const val NEXT_PRE_KEY_ID = "pref_next_pre_key_id"
    private const val ACTIVE_SIGNED_PRE_KEY_ID = "pref_active_signed_pre_key_id"
    private const val NEXT_SIGNED_PRE_KEY_ID = "pref_next_signed_pre_key_id"

    const val BACKUP_ENABLED = "pref_backup_enabled_v3"
    private const val BACKUP_PASSPHRASE = "pref_backup_passphrase"
    private const val ENCRYPTED_BACKUP_PASSPHRASE = "pref_encrypted_backup_passphrase"
    private const val BACKUP_TIME = "pref_backup_next_time"
    const val BACKUP_NOW = "pref_backup_create"
    private const val BACKUP_SAVE_DIR = "pref_save_dir"

    const val SCREEN_LOCK = "pref_android_screen_lock"
    const val SCREEN_LOCK_TIMEOUT = "pref_android_screen_lock_timeout"

    private const val LAST_FULL_CONTACT_SYNC_TIME = "pref_last_full_contact_sync_time"
    private const val NEEDS_FULL_CONTACT_SYNC = "pref_needs_full_contact_sync"

    private const val LOG_ENCRYPTED_SECRET = "pref_log_encrypted_secret"
    private const val LOG_UNENCRYPTED_SECRET = "pref_log_unencrypted_secret"

    private const val NOTIFICATION_CHANNEL_VERSION = "pref_notification_channel_version"
    private const val NOTIFICATION_MESSAGES_CHANNEL_VERSION = "pref_notification_messages_channel_version"

    private const val NEEDS_MESSAGE_PULL = "pref_needs_message_pull"

    private const val UNIDENTIFIED_ACCESS_CERTIFICATE_ROTATION_TIME_PREF = "pref_unidentified_access_certificate_rotation_time"
    private const val UNIDENTIFIED_ACCESS_CERTIFICATE = "pref_unidentified_access_certificate"
    const val UNIVERSAL_UNIDENTIFIED_ACCESS = "pref_universal_unidentified_access"
    const val SHOW_UNIDENTIFIED_DELIVERY_INDICATORS = "pref_show_unidentifed_delivery_indicators"
    private const val UNIDENTIFIED_DELIVERY_ENABLED = "pref_unidentified_delivery_enabled"

    const val TYPING_INDICATORS = "pref_typing_indicators"

    const val LINK_PREVIEWS = "pref_link_previews"

    private const val GIF_GRID_LAYOUT = "pref_gif_grid_layout"

    private const val SEEN_STICKER_INTRO_TOOLTIP = "pref_seen_sticker_intro_tooltip"

    private const val MEDIA_KEYBOARD_MODE = "pref_media_keyboard_mode"

    // region FCM
    private const val IS_USING_FCM = "pref_is_using_fcm"
    private const val FCM_TOKEN = "pref_fcm_token"
    private const val LAST_FCM_TOKEN_UPLOAD_TIME = "pref_last_fcm_token_upload_time_2"
    private const val HAS_SEEN_PN_MODE_SHEET = "pref_has_seen_pn_mode_sheet"

    fun isUsingFCM(context: Context): Boolean {
        return getBooleanPreference(context, IS_USING_FCM, false)
    }

    fun setIsUsingFCM(context: Context, value: Boolean) {
        setBooleanPreference(context, IS_USING_FCM, value)
    }

    fun getFCMToken(context: Context): String? {
        return getStringPreference(context, FCM_TOKEN, "")
    }

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
    fun isScreenLockEnabled(context: Context): Boolean {
        return getBooleanPreference(context, SCREEN_LOCK, false)
    }

    fun setScreenLockEnabled(context: Context, value: Boolean) {
        setBooleanPreference(context, SCREEN_LOCK, value)
    }

    fun getScreenLockTimeout(context: Context): Long {
        return getLongPreference(context, SCREEN_LOCK_TIMEOUT, 0)
    }

    fun setScreenLockTimeout(context: Context, value: Long) {
        setLongPreference(context, SCREEN_LOCK_TIMEOUT, value)
    }

    fun setBackupPassphrase(context: Context, passphrase: String?) {
        setStringPreference(context, BACKUP_PASSPHRASE, passphrase)
    }

    fun getBackupPassphrase(context: Context): String? {
        return getStringPreference(context, BACKUP_PASSPHRASE, null)
    }

    fun setEncryptedBackupPassphrase(context: Context, encryptedPassphrase: String?) {
        setStringPreference(context, ENCRYPTED_BACKUP_PASSPHRASE, encryptedPassphrase)
    }

    fun getEncryptedBackupPassphrase(context: Context): String? {
        return getStringPreference(context, ENCRYPTED_BACKUP_PASSPHRASE, null)
    }

    fun setBackupEnabled(context: Context, value: Boolean) {
        setBooleanPreference(context, BACKUP_ENABLED, value)
    }

    fun isBackupEnabled(context: Context): Boolean {
        return getBooleanPreference(context, BACKUP_ENABLED, false)
    }

    fun setNextBackupTime(context: Context, time: Long) {
        setLongPreference(context, BACKUP_TIME, time)
    }

    fun getNextBackupTime(context: Context): Long {
        return getLongPreference(context, BACKUP_TIME, -1)
    }

    fun setBackupSaveDir(context: Context, dirUri: String?) {
        setStringPreference(context, BACKUP_SAVE_DIR, dirUri)
    }

    fun getBackupSaveDir(context: Context): String? {
        return getStringPreference(context, BACKUP_SAVE_DIR, null)
    }

    fun getNextPreKeyId(context: Context): Int {
        return getIntegerPreference(context, NEXT_PRE_KEY_ID, SecureRandom().nextInt(Medium.MAX_VALUE))
    }

    fun setNextPreKeyId(context: Context, value: Int) {
        setIntegerPrefrence(context, NEXT_PRE_KEY_ID, value)
    }

    fun getNextSignedPreKeyId(context: Context): Int {
        return getIntegerPreference(context, NEXT_SIGNED_PRE_KEY_ID, SecureRandom().nextInt(Medium.MAX_VALUE))
    }

    fun setNextSignedPreKeyId(context: Context, value: Int) {
        setIntegerPrefrence(context, NEXT_SIGNED_PRE_KEY_ID, value)
    }

    fun getActiveSignedPreKeyId(context: Context): Int {
        return getIntegerPreference(context, ACTIVE_SIGNED_PRE_KEY_ID, -1)
    }

    fun setActiveSignedPreKeyId(context: Context, value: Int) {
        setIntegerPrefrence(context, ACTIVE_SIGNED_PRE_KEY_ID, value)
    }

    // TODO
//    fun setNeedsSqlCipherMigration(context: Context, value: Boolean) {
//        setBooleanPreference(context, NEEDS_SQLCIPHER_MIGRATION, value)
//        org.greenrobot.eventbus.EventBus.getDefault().post(SqlCipherNeedsMigrationEvent())
//    }

    fun getNeedsSqlCipherMigration(context: Context): Boolean {
        return getBooleanPreference(context, NEEDS_SQLCIPHER_MIGRATION, false)
    }

    fun setAttachmentEncryptedSecret(context: Context, secret: String) {
        setStringPreference(context, ATTACHMENT_ENCRYPTED_SECRET, secret)
    }

    fun setAttachmentUnencryptedSecret(context: Context, secret: String?) {
        setStringPreference(context, ATTACHMENT_UNENCRYPTED_SECRET, secret)
    }

    fun getAttachmentEncryptedSecret(context: Context): String? {
        return getStringPreference(context, ATTACHMENT_ENCRYPTED_SECRET, null)
    }

    fun getAttachmentUnencryptedSecret(context: Context): String? {
        return getStringPreference(context, ATTACHMENT_UNENCRYPTED_SECRET, null)
    }

    fun setDatabaseEncryptedSecret(context: Context, secret: String) {
        setStringPreference(context, DATABASE_ENCRYPTED_SECRET, secret)
    }

    fun setDatabaseUnencryptedSecret(context: Context, secret: String?) {
        setStringPreference(context, DATABASE_UNENCRYPTED_SECRET, secret)
    }

    fun getDatabaseUnencryptedSecret(context: Context): String? {
        return getStringPreference(context, DATABASE_UNENCRYPTED_SECRET, null)
    }

    fun getDatabaseEncryptedSecret(context: Context): String? {
        return getStringPreference(context, DATABASE_ENCRYPTED_SECRET, null)
    }

    fun setHasSuccessfullyRetrievedDirectory(context: Context, value: Boolean) {
        setBooleanPreference(context, SUCCESSFUL_DIRECTORY_PREF, value)
    }

    fun hasSuccessfullyRetrievedDirectory(context: Context): Boolean {
        return getBooleanPreference(context, SUCCESSFUL_DIRECTORY_PREF, false)
    }

    fun setUnauthorizedReceived(context: Context, value: Boolean) {
        setBooleanPreference(context, UNAUTHORIZED_RECEIVED, value)
    }

    fun isUnauthorizedRecieved(context: Context): Boolean {
        return getBooleanPreference(context, UNAUTHORIZED_RECEIVED, false)
    }

    fun isIncognitoKeyboardEnabled(context: Context): Boolean {
        return getBooleanPreference(context, INCOGNITO_KEYBORAD_PREF, true)
    }

    fun isReadReceiptsEnabled(context: Context): Boolean {
        return getBooleanPreference(context, READ_RECEIPTS_PREF, false)
    }

    fun setReadReceiptsEnabled(context: Context, enabled: Boolean) {
        setBooleanPreference(context, READ_RECEIPTS_PREF, enabled)
    }

    fun isTypingIndicatorsEnabled(context: Context): Boolean {
        return getBooleanPreference(context, TYPING_INDICATORS, false)
    }

    fun setTypingIndicatorsEnabled(context: Context, enabled: Boolean) {
        setBooleanPreference(context, TYPING_INDICATORS, enabled)
    }

    fun isLinkPreviewsEnabled(context: Context): Boolean {
        return getBooleanPreference(context, LINK_PREVIEWS, false)
    }

    fun setLinkPreviewsEnabled(context: Context, enabled: Boolean) {
        setBooleanPreference(context, LINK_PREVIEWS, enabled)
    }

    fun isGifSearchInGridLayout(context: Context): Boolean {
        return getBooleanPreference(context, GIF_GRID_LAYOUT, false)
    }

    fun setIsGifSearchInGridLayout(context: Context, isGrid: Boolean) {
        setBooleanPreference(context, GIF_GRID_LAYOUT, isGrid)
    }

    fun getProfileKey(context: Context): String? {
        return getStringPreference(context, PROFILE_KEY_PREF, null)
    }

    fun setProfileKey(context: Context, key: String?) {
        setStringPreference(context, PROFILE_KEY_PREF, key)
    }

    fun setProfileName(context: Context, name: String?) {
        setStringPreference(context, PROFILE_NAME_PREF, name)
    }

    fun getProfileName(context: Context): String? {
        return getStringPreference(context, PROFILE_NAME_PREF, null)
    }

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

    fun getProfilePictureURL(context: Context): String? {
        return getStringPreference(context, PROFILE_AVATAR_URL_PREF, null)
    }

    fun getNotificationPriority(context: Context): Int {
        return getStringPreference(context, NOTIFICATION_PRIORITY_PREF, NotificationCompat.PRIORITY_HIGH.toString())!!.toInt()
    }

    fun getMessageBodyTextSize(context: Context): Int {
        return getStringPreference(context, MESSAGE_BODY_TEXT_SIZE_PREF, "16")!!.toInt()
    }

    fun isTurnOnly(context: Context): Boolean {
        return getBooleanPreference(context, ALWAYS_RELAY_CALLS_PREF, false)
    }

    fun isFcmDisabled(context: Context): Boolean {
        return getBooleanPreference(context, GCM_DISABLED_PREF, false)
    }

    fun setFcmDisabled(context: Context, disabled: Boolean) {
        setBooleanPreference(context, GCM_DISABLED_PREF, disabled)
    }

    fun isWebrtcCallingEnabled(context: Context): Boolean {
        return getBooleanPreference(context, WEBRTC_CALLING_PREF, false)
    }

    fun setWebrtcCallingEnabled(context: Context, enabled: Boolean) {
        setBooleanPreference(context, WEBRTC_CALLING_PREF, enabled)
    }

    fun setDirectCaptureCameraId(context: Context, value: Int) {
        setIntegerPrefrence(context, DIRECT_CAPTURE_CAMERA_ID, value)
    }

    fun getDirectCaptureCameraId(context: Context): Int {
        return getIntegerPreference(context, DIRECT_CAPTURE_CAMERA_ID, Camera.CameraInfo.CAMERA_FACING_FRONT)
    }

    fun setMultiDevice(context: Context, value: Boolean) {
        setBooleanPreference(context, MULTI_DEVICE_PROVISIONED_PREF, value)
    }

    fun isMultiDevice(context: Context): Boolean {
        return getBooleanPreference(context, MULTI_DEVICE_PROVISIONED_PREF, false)
    }

    fun setSignedPreKeyFailureCount(context: Context, value: Int) {
        setIntegerPrefrence(context, SIGNED_PREKEY_FAILURE_COUNT_PREF, value)
    }

    fun getSignedPreKeyFailureCount(context: Context): Int {
        return getIntegerPreference(context, SIGNED_PREKEY_FAILURE_COUNT_PREF, 0)
    }

    // TODO
//    fun getNotificationPrivacy(context: Context): NotificationPrivacyPreference {
//        return NotificationPrivacyPreference(getStringPreference(context, NOTIFICATION_PRIVACY_PREF, "all"))
//    }

    fun isNewContactsNotificationEnabled(context: Context): Boolean {
        return getBooleanPreference(context, NEW_CONTACTS_NOTIFICATIONS, true)
    }

    fun getRatingLaterTimestamp(context: Context): Long {
        return getLongPreference(context, RATING_LATER_PREF, 0)
    }

    fun setRatingLaterTimestamp(context: Context, timestamp: Long) {
        setLongPreference(context, RATING_LATER_PREF, timestamp)
    }

    fun isRatingEnabled(context: Context): Boolean {
        return getBooleanPreference(context, RATING_ENABLED_PREF, true)
    }

    fun setRatingEnabled(context: Context, enabled: Boolean) {
        setBooleanPreference(context, RATING_ENABLED_PREF, enabled)
    }

    fun isWebsocketRegistered(context: Context): Boolean {
        return getBooleanPreference(context, WEBSOCKET_REGISTERED_PREF, false)
    }

    fun setWebsocketRegistered(context: Context, registered: Boolean) {
        setBooleanPreference(context, WEBSOCKET_REGISTERED_PREF, registered)
    }

    fun isWifiSmsEnabled(context: Context): Boolean {
        return getBooleanPreference(context, WIFI_SMS_PREF, false)
    }

    fun getRepeatAlertsCount(context: Context): Int {
        return try {
            getStringPreference(context, REPEAT_ALERTS_PREF, "0")!!.toInt()
        } catch (e: NumberFormatException) {
            Log.w(TAG, e)
            0
        }
    }

    fun setRepeatAlertsCount(context: Context, count: Int) {
        setStringPreference(context, REPEAT_ALERTS_PREF, count.toString())
    }

    fun isSignedPreKeyRegistered(context: Context): Boolean {
        return getBooleanPreference(context, SIGNED_PREKEY_REGISTERED_PREF, false)
    }

    fun setSignedPreKeyRegistered(context: Context, value: Boolean) {
        setBooleanPreference(context, SIGNED_PREKEY_REGISTERED_PREF, value)
    }

    fun getLocalRegistrationId(context: Context): Int {
        return getIntegerPreference(context, LOCAL_REGISTRATION_ID_PREF, 0)
    }

    fun setLocalRegistrationId(context: Context, registrationId: Int) {
        setIntegerPrefrence(context, LOCAL_REGISTRATION_ID_PREF, registrationId)
    }

    fun removeLocalRegistrationId(context: Context) {
        removePreference(context, LOCAL_REGISTRATION_ID_PREF)
    }

    fun isInThreadNotifications(context: Context): Boolean {
        return getBooleanPreference(context, IN_THREAD_NOTIFICATION_PREF, true)
    }

    fun getUnidentifiedAccessCertificateRotationTime(context: Context): Long {
        return getLongPreference(context, UNIDENTIFIED_ACCESS_CERTIFICATE_ROTATION_TIME_PREF, 0L)
    }

    fun setUnidentifiedAccessCertificateRotationTime(context: Context, value: Long) {
        setLongPreference(context, UNIDENTIFIED_ACCESS_CERTIFICATE_ROTATION_TIME_PREF, value)
    }

    fun setUnidentifiedAccessCertificate(context: Context, value: ByteArray?) {
        setStringPreference(context, UNIDENTIFIED_ACCESS_CERTIFICATE, Base64.encodeBytes(value))
    }

    fun getUnidentifiedAccessCertificate(context: Context): ByteArray? {
        try {
            val result = getStringPreference(context, UNIDENTIFIED_ACCESS_CERTIFICATE, null)
            if (result != null) {
                return Base64.decode(result)
            }
        } catch (e: IOException) {
            Log.w(TAG, e)
        }
        return null
    }

    fun isUniversalUnidentifiedAccess(context: Context): Boolean {
        return getBooleanPreference(context, UNIVERSAL_UNIDENTIFIED_ACCESS, false)
    }

    fun isShowUnidentifiedDeliveryIndicatorsEnabled(context: Context): Boolean {
        return getBooleanPreference(context, SHOW_UNIDENTIFIED_DELIVERY_INDICATORS, false)
    }

    fun setIsUnidentifiedDeliveryEnabled(context: Context, enabled: Boolean) {
        setBooleanPreference(context, UNIDENTIFIED_DELIVERY_ENABLED, enabled)
    }

    fun isUnidentifiedDeliveryEnabled(context: Context): Boolean {
        // Loki - Always enable unidentified sender
        return true
        // return getBooleanPreference(context, UNIDENTIFIED_DELIVERY_ENABLED, true);
    }

    fun getSignedPreKeyRotationTime(context: Context): Long {
        return getLongPreference(context, SIGNED_PREKEY_ROTATION_TIME_PREF, 0L)
    }

    fun setSignedPreKeyRotationTime(context: Context, value: Long) {
        setLongPreference(context, SIGNED_PREKEY_ROTATION_TIME_PREF, value)
    }

    fun getDirectoryRefreshTime(context: Context): Long {
        return getLongPreference(context, DIRECTORY_FRESH_TIME_PREF, 0L)
    }

    fun setDirectoryRefreshTime(context: Context, value: Long) {
        setLongPreference(context, DIRECTORY_FRESH_TIME_PREF, value)
    }

    fun getUpdateApkRefreshTime(context: Context): Long {
        return getLongPreference(context, UPDATE_APK_REFRESH_TIME_PREF, 0L)
    }

    fun setUpdateApkRefreshTime(context: Context, value: Long) {
        setLongPreference(context, UPDATE_APK_REFRESH_TIME_PREF, value)
    }

    fun setUpdateApkDownloadId(context: Context, value: Long) {
        setLongPreference(context, UPDATE_APK_DOWNLOAD_ID, value)
    }

    fun getUpdateApkDownloadId(context: Context): Long {
        return getLongPreference(context, UPDATE_APK_DOWNLOAD_ID, -1)
    }

    fun setUpdateApkDigest(context: Context, value: String?) {
        setStringPreference(context, UPDATE_APK_DIGEST, value)
    }

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

    fun getPushServerPassword(context: Context): String? {
        return getStringPreference(context, GCM_PASSWORD_PREF, null)
    }

    fun setPushServerPassword(context: Context, password: String?) {
        setStringPreference(context, GCM_PASSWORD_PREF, password)
    }

    fun getSignalingKey(context: Context): String? {
        return getStringPreference(context, SIGNALING_KEY_PREF, null)
    }

    fun isEnterImeKeyEnabled(context: Context): Boolean {
        return getBooleanPreference(context, ENTER_PRESENT_PREF, false)
    }

    fun isEnterSendsEnabled(context: Context): Boolean {
        return getBooleanPreference(context, ENTER_SENDS_PREF, false)
    }

    fun isPasswordDisabled(context: Context): Boolean {
        return getBooleanPreference(context, DISABLE_PASSPHRASE_PREF, false)
    }

    fun setPasswordDisabled(context: Context, disabled: Boolean) {
        setBooleanPreference(context, DISABLE_PASSPHRASE_PREF, disabled)
    }

    fun getUseCustomMmsc(context: Context): Boolean {
        val legacy: Boolean = isLegacyUseLocalApnsEnabled(context)
        return getBooleanPreference(context, MMSC_CUSTOM_HOST_PREF, legacy)
    }

    fun setUseCustomMmsc(context: Context, value: Boolean) {
        setBooleanPreference(context, MMSC_CUSTOM_HOST_PREF, value)
    }

    fun getMmscUrl(context: Context): String? {
        return getStringPreference(context, MMSC_HOST_PREF, "")
    }

    fun setMmscUrl(context: Context, mmsc: String?) {
        setStringPreference(context, MMSC_HOST_PREF, mmsc)
    }

    fun getUseCustomMmscProxy(context: Context): Boolean {
        val legacy: Boolean = isLegacyUseLocalApnsEnabled(context)
        return getBooleanPreference(context, MMSC_CUSTOM_PROXY_PREF, legacy)
    }

    fun setUseCustomMmscProxy(context: Context, value: Boolean) {
        setBooleanPreference(context, MMSC_CUSTOM_PROXY_PREF, value)
    }

    fun getMmscProxy(context: Context): String? {
        return getStringPreference(context, MMSC_PROXY_HOST_PREF, "")
    }

    fun setMmscProxy(context: Context, value: String?) {
        setStringPreference(context, MMSC_PROXY_HOST_PREF, value)
    }

    fun getUseCustomMmscProxyPort(context: Context): Boolean {
        val legacy: Boolean = isLegacyUseLocalApnsEnabled(context)
        return getBooleanPreference(context, MMSC_CUSTOM_PROXY_PORT_PREF, legacy)
    }

    fun setUseCustomMmscProxyPort(context: Context, value: Boolean) {
        setBooleanPreference(context, MMSC_CUSTOM_PROXY_PORT_PREF, value)
    }

    fun getMmscProxyPort(context: Context): String? {
        return getStringPreference(context, MMSC_PROXY_PORT_PREF, "")
    }

    fun setMmscProxyPort(context: Context, value: String?) {
        setStringPreference(context, MMSC_PROXY_PORT_PREF, value)
    }

    fun getUseCustomMmscUsername(context: Context): Boolean {
        val legacy: Boolean = isLegacyUseLocalApnsEnabled(context)
        return getBooleanPreference(context, MMSC_CUSTOM_USERNAME_PREF, legacy)
    }

    fun setUseCustomMmscUsername(context: Context, value: Boolean) {
        setBooleanPreference(context, MMSC_CUSTOM_USERNAME_PREF, value)
    }

    fun getMmscUsername(context: Context): String? {
        return getStringPreference(context, MMSC_USERNAME_PREF, "")
    }

    fun setMmscUsername(context: Context, value: String?) {
        setStringPreference(context, MMSC_USERNAME_PREF, value)
    }

    fun getUseCustomMmscPassword(context: Context): Boolean {
        val legacy: Boolean = isLegacyUseLocalApnsEnabled(context)
        return getBooleanPreference(context, MMSC_CUSTOM_PASSWORD_PREF, legacy)
    }

    fun setUseCustomMmscPassword(context: Context, value: Boolean) {
        setBooleanPreference(context, MMSC_CUSTOM_PASSWORD_PREF, value)
    }

    fun getMmscPassword(context: Context): String? {
        return getStringPreference(context, MMSC_PASSWORD_PREF, "")
    }

    fun setMmscPassword(context: Context, value: String?) {
        setStringPreference(context, MMSC_PASSWORD_PREF, value)
    }

    fun getMmsUserAgent(context: Context, defaultUserAgent: String): String {
        val useCustom: Boolean = getBooleanPreference(context, MMS_CUSTOM_USER_AGENT, false)
        return if (useCustom) getStringPreference(context, MMS_USER_AGENT, defaultUserAgent)!! else defaultUserAgent
    }

    fun getIdentityContactUri(context: Context): String? {
        return getStringPreference(context, IDENTITY_PREF, null)
    }

    fun setIdentityContactUri(context: Context, identityUri: String?) {
        setStringPreference(context, IDENTITY_PREF, identityUri)
    }

    fun setScreenSecurityEnabled(context: Context, value: Boolean) {
        setBooleanPreference(context, SCREEN_SECURITY_PREF, value)
    }

    fun isScreenSecurityEnabled(context: Context): Boolean {
        return getBooleanPreference(context, SCREEN_SECURITY_PREF, true)
    }

    fun isLegacyUseLocalApnsEnabled(context: Context): Boolean {
        return getBooleanPreference(context, ENABLE_MANUAL_MMS_PREF, false)
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

    fun getLastExperienceVersionCode(context: Context): Int {
        return getIntegerPreference(context, LAST_EXPERIENCE_VERSION_PREF, 0)
    }

    fun setLastExperienceVersionCode(context: Context, versionCode: Int) {
        setIntegerPrefrence(context, LAST_EXPERIENCE_VERSION_PREF, versionCode)
    }

    fun getExperienceDismissedVersionCode(context: Context): Int {
        return getIntegerPreference(context, EXPERIENCE_DISMISSED_PREF, 0)
    }

    fun setExperienceDismissedVersionCode(context: Context, versionCode: Int) {
        setIntegerPrefrence(context, EXPERIENCE_DISMISSED_PREF, versionCode)
    }

    fun getTheme(context: Context): String? {
        return getStringPreference(context, THEME_PREF, "light")
    }

    fun isVerifying(context: Context): Boolean {
        return getBooleanPreference(context, VERIFYING_STATE_PREF, false)
    }

    fun setVerifying(context: Context, verifying: Boolean) {
        setBooleanPreference(context, VERIFYING_STATE_PREF, verifying)
    }

    fun isPushRegistered(context: Context): Boolean {
        return getBooleanPreference(context, REGISTERED_GCM_PREF, false)
    }

    fun setPushRegistered(context: Context, registered: Boolean) {
        Log.i(TAG, "Setting push registered: $registered")
        setBooleanPreference(context, REGISTERED_GCM_PREF, registered)
    }

    fun isShowInviteReminders(context: Context): Boolean {
        return getBooleanPreference(context, SHOW_INVITE_REMINDER_PREF, true)
    }

    fun isPassphraseTimeoutEnabled(context: Context): Boolean {
        return getBooleanPreference(context, PASSPHRASE_TIMEOUT_PREF, false)
    }

    fun getPassphraseTimeoutInterval(context: Context): Int {
        return getIntegerPreference(context, PASSPHRASE_TIMEOUT_INTERVAL_PREF, 5 * 60)
    }

    fun setPassphraseTimeoutInterval(context: Context, interval: Int) {
        setIntegerPrefrence(context, PASSPHRASE_TIMEOUT_INTERVAL_PREF, interval)
    }

    @JvmStatic
    fun getLanguage(context: Context): String? {
        return getStringPreference(context, LANGUAGE_PREF, "zz")
    }

    fun setLanguage(context: Context, language: String?) {
        setStringPreference(context, LANGUAGE_PREF, language)
    }

    fun isSmsDeliveryReportsEnabled(context: Context): Boolean {
        return getBooleanPreference(context, SMS_DELIVERY_REPORT_PREF, false)
    }

    fun hasSeenWelcomeScreen(context: Context): Boolean {
        return getBooleanPreference(context, SEEN_WELCOME_SCREEN_PREF, true)
    }

    fun setHasSeenWelcomeScreen(context: Context, value: Boolean) {
        setBooleanPreference(context, SEEN_WELCOME_SCREEN_PREF, value)
    }

    fun hasPromptedPushRegistration(context: Context): Boolean {
        return getBooleanPreference(context, PROMPTED_PUSH_REGISTRATION_PREF, false)
    }

    fun setPromptedPushRegistration(context: Context, value: Boolean) {
        setBooleanPreference(context, PROMPTED_PUSH_REGISTRATION_PREF, value)
    }

    fun hasPromptedDefaultSmsProvider(context: Context): Boolean {
        return getBooleanPreference(context, PROMPTED_DEFAULT_SMS_PREF, false)
    }

    fun setPromptedDefaultSmsProvider(context: Context, value: Boolean) {
        setBooleanPreference(context, PROMPTED_DEFAULT_SMS_PREF, value)
    }

    fun setPromptedOptimizeDoze(context: Context, value: Boolean) {
        setBooleanPreference(context, PROMPTED_OPTIMIZE_DOZE_PREF, value)
    }

    fun hasPromptedOptimizeDoze(context: Context): Boolean {
        return getBooleanPreference(context, PROMPTED_OPTIMIZE_DOZE_PREF, false)
    }

    fun hasPromptedShare(context: Context): Boolean {
        return getBooleanPreference(context, PROMPTED_SHARE_PREF, false)
    }

    fun setPromptedShare(context: Context, value: Boolean) {
        setBooleanPreference(context, PROMPTED_SHARE_PREF, value)
    }

    fun isInterceptAllMmsEnabled(context: Context): Boolean {
        return getBooleanPreference(context, ALL_MMS_PREF, true)
    }

    fun isInterceptAllSmsEnabled(context: Context): Boolean {
        return getBooleanPreference(context, ALL_SMS_PREF, true)
    }

    fun isNotificationsEnabled(context: Context): Boolean {
        return getBooleanPreference(context, NOTIFICATION_PREF, true)
    }

    fun getNotificationRingtone(context: Context): Uri {
        var result = getStringPreference(context, RINGTONE_PREF, Settings.System.DEFAULT_NOTIFICATION_URI.toString())
        if (result != null && result.startsWith("file:")) {
            result = Settings.System.DEFAULT_NOTIFICATION_URI.toString()
        }
        return Uri.parse(result)
    }

    fun removeNotificationRingtone(context: Context) {
        removePreference(context, RINGTONE_PREF)
    }

    fun setNotificationRingtone(context: Context, ringtone: String?) {
        setStringPreference(context, RINGTONE_PREF, ringtone)
    }

    fun setNotificationVibrateEnabled(context: Context, enabled: Boolean) {
        setBooleanPreference(context, VIBRATE_PREF, enabled)
    }

    fun isNotificationVibrateEnabled(context: Context): Boolean {
        return getBooleanPreference(context, VIBRATE_PREF, true)
    }

    fun getNotificationLedColor(context: Context): String? {
        return getStringPreference(context, LED_COLOR_PREF, "blue")
    }

    fun getNotificationLedPattern(context: Context): String? {
        return getStringPreference(context, LED_BLINK_PREF, "500,2000")
    }

    fun getNotificationLedPatternCustom(context: Context): String? {
        return getStringPreference(context, LED_BLINK_PREF_CUSTOM, "500,2000")
    }

    fun setNotificationLedPatternCustom(context: Context, pattern: String?) {
        setStringPreference(context, LED_BLINK_PREF_CUSTOM, pattern)
    }

    fun isThreadLengthTrimmingEnabled(context: Context): Boolean {
        return getBooleanPreference(context, THREAD_TRIM_ENABLED, false)
    }

    fun getThreadTrimLength(context: Context): Int {
        return getStringPreference(context, THREAD_TRIM_LENGTH, "500")!!.toInt()
    }

    fun isSystemEmojiPreferred(context: Context): Boolean {
        return getBooleanPreference(context, SYSTEM_EMOJI_PREF, false)
    }

    // TODO
//    fun getMobileMediaDownloadAllowed(context: Context): Set<String> {
//        return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_MOBILE_PREF, R.array.pref_media_download_mobile_data_default)
//    }
//
//    fun getWifiMediaDownloadAllowed(context: Context): Set<String> {
//        return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_WIFI_PREF, R.array.pref_media_download_wifi_default)
//    }
//
//    fun getRoamingMediaDownloadAllowed(context: Context): Set<String> {
//        return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_ROAMING_PREF, R.array.pref_media_download_roaming_default)
//    }

    private fun getMediaDownloadAllowed(context: Context, key: String, @ArrayRes defaultValuesRes: Int): Set<String>? {
        return getStringSetPreference(context, key, HashSet(Arrays.asList(*context.resources.getStringArray(defaultValuesRes))))
    }

    fun getLastFullContactSyncTime(context: Context): Long {
        return getLongPreference(context, LAST_FULL_CONTACT_SYNC_TIME, 0)
    }

    fun setLastFullContactSyncTime(context: Context, timestamp: Long) {
        setLongPreference(context, LAST_FULL_CONTACT_SYNC_TIME, timestamp)
    }

    fun needsFullContactSync(context: Context): Boolean {
        return getBooleanPreference(context, NEEDS_FULL_CONTACT_SYNC, false)
    }

    fun setNeedsFullContactSync(context: Context, needsSync: Boolean) {
        setBooleanPreference(context, NEEDS_FULL_CONTACT_SYNC, needsSync)
    }

    fun setLogEncryptedSecret(context: Context, base64Secret: String?) {
        setStringPreference(context, LOG_ENCRYPTED_SECRET, base64Secret)
    }

    fun getLogEncryptedSecret(context: Context): String? {
        return getStringPreference(context, LOG_ENCRYPTED_SECRET, null)
    }

    fun setLogUnencryptedSecret(context: Context, base64Secret: String?) {
        setStringPreference(context, LOG_UNENCRYPTED_SECRET, base64Secret)
    }

    fun getLogUnencryptedSecret(context: Context): String? {
        return getStringPreference(context, LOG_UNENCRYPTED_SECRET, null)
    }

    fun getNotificationChannelVersion(context: Context): Int {
        return getIntegerPreference(context, NOTIFICATION_CHANNEL_VERSION, 1)
    }

    fun setNotificationChannelVersion(context: Context, version: Int) {
        setIntegerPrefrence(context, NOTIFICATION_CHANNEL_VERSION, version)
    }

    fun getNotificationMessagesChannelVersion(context: Context): Int {
        return getIntegerPreference(context, NOTIFICATION_MESSAGES_CHANNEL_VERSION, 1)
    }

    fun setNotificationMessagesChannelVersion(context: Context, version: Int) {
        setIntegerPrefrence(context, NOTIFICATION_MESSAGES_CHANNEL_VERSION, version)
    }

    fun getNeedsMessagePull(context: Context): Boolean {
        return getBooleanPreference(context, NEEDS_MESSAGE_PULL, false)
    }

    fun setNeedsMessagePull(context: Context, needsMessagePull: Boolean) {
        setBooleanPreference(context, NEEDS_MESSAGE_PULL, needsMessagePull)
    }

    fun hasSeenStickerIntroTooltip(context: Context): Boolean {
        return getBooleanPreference(context, SEEN_STICKER_INTRO_TOOLTIP, false)
    }

    fun setHasSeenStickerIntroTooltip(context: Context, seenStickerTooltip: Boolean) {
        setBooleanPreference(context, SEEN_STICKER_INTRO_TOOLTIP, seenStickerTooltip)
    }

    fun setMediaKeyboardMode(context: Context, mode: MediaKeyboardMode) {
        setStringPreference(context, MEDIA_KEYBOARD_MODE, mode.name)
    }

    fun getMediaKeyboardMode(context: Context): MediaKeyboardMode {
        val name = getStringPreference(context, MEDIA_KEYBOARD_MODE, MediaKeyboardMode.EMOJI.name)!!
        return MediaKeyboardMode.valueOf(name)
    }

    fun setBooleanPreference(context: Context, key: String?, value: Boolean) {
        getDefaultSharedPreferences(context).edit().putBoolean(key, value).apply()
    }

    fun getBooleanPreference(context: Context, key: String?, defaultValue: Boolean): Boolean {
        return getDefaultSharedPreferences(context).getBoolean(key, defaultValue)
    }

    fun setStringPreference(context: Context, key: String?, value: String?) {
        getDefaultSharedPreferences(context).edit().putString(key, value).apply()
    }

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
    fun getBackgroundPollTime(context: Context): Long {
        return getLongPreference(context, "background_poll_time", 0L)
    }

    fun setBackgroundPollTime(context: Context, backgroundPollTime: Long) {
        setLongPreference(context, "background_poll_time", backgroundPollTime)
    }

    fun getOpenGroupBackgroundPollTime(context: Context): Long {
        return getLongPreference(context, "public_chat_background_poll_time", 0L)
    }

    fun setOpenGroupBackgroundPollTime(context: Context, backgroundPollTime: Long) {
        setLongPreference(context, "public_chat_background_poll_time", backgroundPollTime)
    }

    fun isChatSetUp(context: Context, id: String): Boolean {
        return getBooleanPreference(context, "is_chat_set_up?chat=$id", false)
    }

    fun markChatSetUp(context: Context, id: String) {
        setBooleanPreference(context, "is_chat_set_up?chat=$id", true)
    }

    @JvmStatic
    fun getMasterHexEncodedPublicKey(context: Context): String? {
        return getStringPreference(context, "master_hex_encoded_public_key", null)
    }

    fun setMasterHexEncodedPublicKey(context: Context, masterHexEncodedPublicKey: String) {
        setStringPreference(context, "master_hex_encoded_public_key", masterHexEncodedPublicKey.toLowerCase())
    }

    fun getHasViewedSeed(context: Context): Boolean {
        return getBooleanPreference(context, "has_viewed_seed", false)
    }

    fun setHasViewedSeed(context: Context, hasViewedSeed: Boolean) {
        setBooleanPreference(context, "has_viewed_seed", hasViewedSeed)
    }

    fun setNeedsDatabaseReset(context: Context, resetDatabase: Boolean) {
        getDefaultSharedPreferences(context).edit().putBoolean("database_reset", resetDatabase).commit()
    }

    fun getNeedsDatabaseReset(context: Context): Boolean {
        return getBooleanPreference(context, "database_reset", false)
    }

    fun setWasUnlinked(context: Context, value: Boolean) {
        // We do it this way so that it gets persisted in storage straight away
        getDefaultSharedPreferences(context).edit().putBoolean("database_reset_unpair", value).commit()
    }

    fun getWasUnlinked(context: Context): Boolean {
        return getBooleanPreference(context, "database_reset_unpair", false)
    }

    fun setNeedsIsRevokedSlaveDeviceCheck(context: Context, value: Boolean) {
        setBooleanPreference(context, "needs_revocation", value)
    }

    fun getNeedsIsRevokedSlaveDeviceCheck(context: Context): Boolean {
        return getBooleanPreference(context, "needs_revocation", false)
    }

    fun setRestorationTime(context: Context, time: Long) {
        setLongPreference(context, "restoration_time", time)
    }

    fun getRestorationTime(context: Context): Long {
        return getLongPreference(context, "restoration_time", 0)
    }

    fun getHasSeenOpenGroupSuggestionSheet(context: Context): Boolean {
        return getBooleanPreference(context, "has_seen_open_group_suggestion_sheet", false)
    }

    fun setHasSeenOpenGroupSuggestionSheet(context: Context) {
        setBooleanPreference(context, "has_seen_open_group_suggestion_sheet", true)
    }

    fun getLastProfilePictureUpload(context: Context): Long {
        return getLongPreference(context, "last_profile_picture_upload", 0)
    }

    fun setLastProfilePictureUpload(context: Context, newValue: Long) {
        setLongPreference(context, "last_profile_picture_upload", newValue)
    }

    fun hasSeenGIFMetaDataWarning(context: Context): Boolean {
        return getBooleanPreference(context, "has_seen_gif_metadata_warning", false)
    }

    fun setHasSeenGIFMetaDataWarning(context: Context) {
        setBooleanPreference(context, "has_seen_gif_metadata_warning", true)
    }

    fun clearAll(context: Context) {
        getDefaultSharedPreferences(context).edit().clear().commit()
    }

    fun getHasSeenMultiDeviceRemovalSheet(context: Context): Boolean {
        return getBooleanPreference(context, "has_seen_multi_device_removal_sheet", false)
    }

    fun setHasSeenMultiDeviceRemovalSheet(context: Context) {
        setBooleanPreference(context, "has_seen_multi_device_removal_sheet", true)
    }

    fun hasSeenLightThemeIntroSheet(context: Context): Boolean {
        return getBooleanPreference(context, "has_seen_light_theme_intro_sheet", false)
    }

    fun setHasSeenLightThemeIntroSheet(context: Context) {
        setBooleanPreference(context, "has_seen_light_theme_intro_sheet", true)
    }

    // endregion
    /* TODO
    // region Backup related
    fun getBackupRecords(context: Context): List<SharedPreference> {
        val preferences = getDefaultSharedPreferences(context)
        val prefsFileName: String
        prefsFileName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getDefaultSharedPreferencesName(context)
        } else {
            context.packageName + "_preferences"
        }
        val prefList: LinkedList<SharedPreference> = LinkedList<SharedPreference>()
        addBackupEntryInt(prefList, preferences, prefsFileName, LOCAL_REGISTRATION_ID_PREF)
        addBackupEntryString(prefList, preferences, prefsFileName, LOCAL_NUMBER_PREF)
        addBackupEntryString(prefList, preferences, prefsFileName, PROFILE_NAME_PREF)
        addBackupEntryString(prefList, preferences, prefsFileName, PROFILE_AVATAR_URL_PREF)
        addBackupEntryInt(prefList, preferences, prefsFileName, PROFILE_AVATAR_ID_PREF)
        addBackupEntryString(prefList, preferences, prefsFileName, PROFILE_KEY_PREF)
        addBackupEntryBoolean(prefList, preferences, prefsFileName, IS_USING_FCM)
        return prefList
    }

    private fun addBackupEntryString(
            outPrefList: MutableList<SharedPreference>,
            prefs: SharedPreferences,
            prefFileName: String,
            prefKey: String,
    ) {
        val value = prefs.getString(prefKey, null)
        if (value == null) {
            logBackupEntry(prefKey, false)
            return
        }
        outPrefList.add(BackupProtos.SharedPreference.newBuilder()
                .setFile(prefFileName)
                .setKey(prefKey)
                .setValue(value)
                .build())
        logBackupEntry(prefKey, true)
    }

    private fun addBackupEntryInt(
            outPrefList: MutableList<SharedPreference>,
            prefs: SharedPreferences,
            prefFileName: String,
            prefKey: String,
    ) {
        val value = prefs.getInt(prefKey, -1)
        if (value == -1) {
            logBackupEntry(prefKey, false)
            return
        }
        outPrefList.add(BackupProtos.SharedPreference.newBuilder()
                .setFile(prefFileName)
                .setKey(PREF_PREFIX_TYPE_INT + prefKey) // The prefix denotes the type of the preference.
                .setValue(value.toString())
                .build())
        logBackupEntry(prefKey, true)
    }

    private fun addBackupEntryBoolean(
            outPrefList: MutableList<SharedPreference>,
            prefs: SharedPreferences,
            prefFileName: String,
            prefKey: String,
    ) {
        if (!prefs.contains(prefKey)) {
            logBackupEntry(prefKey, false)
            return
        }
        outPrefList.add(BackupProtos.SharedPreference.newBuilder()
                .setFile(prefFileName)
                .setKey(PREF_PREFIX_TYPE_BOOLEAN + prefKey) // The prefix denotes the type of the preference.
                .setValue(prefs.getBoolean(prefKey, false).toString())
                .build())
        logBackupEntry(prefKey, true)
    }

    private fun logBackupEntry(prefName: String, wasIncluded: Boolean) {
        val sb = StringBuilder()
        sb.append("Backup preference ")
        sb.append(if (wasIncluded) "+ " else "- ")
        sb.append('\"').append(prefName).append("\" ")
        if (!wasIncluded) {
            sb.append("(is empty and not included)")
        }
        Log.d(TAG, sb.toString())
    } // endregion
     */

    // NEVER rename these -- they're persisted by name
    enum class MediaKeyboardMode {
        EMOJI, STICKER
    }
}