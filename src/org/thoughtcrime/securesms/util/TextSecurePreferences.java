package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera.CameraInfo;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.ArrayRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.jobs.requirements.SqlCipherMigrationRequirementProvider;
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference;
import org.whispersystems.libsignal.util.Medium;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TextSecurePreferences {

  private static final String TAG = TextSecurePreferences.class.getSimpleName();

  public  static final String IDENTITY_PREF                    = "pref_choose_identity";
  public  static final String CHANGE_PASSPHRASE_PREF           = "pref_change_passphrase";
  public  static final String DISABLE_PASSPHRASE_PREF          = "pref_disable_passphrase";
  public  static final String THEME_PREF                       = "pref_theme";
  public  static final String LANGUAGE_PREF                    = "pref_language";
  private static final String MMSC_CUSTOM_HOST_PREF            = "pref_apn_mmsc_custom_host";
  public  static final String MMSC_HOST_PREF                   = "pref_apn_mmsc_host";
  private static final String MMSC_CUSTOM_PROXY_PREF           = "pref_apn_mms_custom_proxy";
  public  static final String MMSC_PROXY_HOST_PREF             = "pref_apn_mms_proxy";
  private static final String MMSC_CUSTOM_PROXY_PORT_PREF      = "pref_apn_mms_custom_proxy_port";
  public  static final String MMSC_PROXY_PORT_PREF             = "pref_apn_mms_proxy_port";
  private static final String MMSC_CUSTOM_USERNAME_PREF        = "pref_apn_mmsc_custom_username";
  public  static final String MMSC_USERNAME_PREF               = "pref_apn_mmsc_username";
  private static final String MMSC_CUSTOM_PASSWORD_PREF        = "pref_apn_mmsc_custom_password";
  public  static final String MMSC_PASSWORD_PREF               = "pref_apn_mmsc_password";
  public  static final String THREAD_TRIM_LENGTH               = "pref_trim_length";
  public  static final String THREAD_TRIM_NOW                  = "pref_trim_now";
  public  static final String ENABLE_MANUAL_MMS_PREF           = "pref_enable_manual_mms";

  private static final String LAST_VERSION_CODE_PREF           = "last_version_code";
  private static final String LAST_EXPERIENCE_VERSION_PREF     = "last_experience_version_code";
  private static final String EXPERIENCE_DISMISSED_PREF        = "experience_dismissed";
  public  static final String RINGTONE_PREF                    = "pref_key_ringtone";
  private static final String VIBRATE_PREF                     = "pref_key_vibrate";
  private static final String NOTIFICATION_PREF                = "pref_key_enable_notifications";
  public  static final String LED_COLOR_PREF                   = "pref_led_color";
  public  static final String LED_BLINK_PREF                   = "pref_led_blink";
  private static final String LED_BLINK_PREF_CUSTOM            = "pref_led_blink_custom";
  public  static final String ALL_MMS_PREF                     = "pref_all_mms";
  public  static final String ALL_SMS_PREF                     = "pref_all_sms";
  public  static final String PASSPHRASE_TIMEOUT_INTERVAL_PREF = "pref_timeout_interval";
  private static final String PASSPHRASE_TIMEOUT_PREF          = "pref_timeout_passphrase";
  public  static final String SCREEN_SECURITY_PREF             = "pref_screen_security";
  private static final String ENTER_SENDS_PREF                 = "pref_enter_sends";
  private static final String ENTER_PRESENT_PREF               = "pref_enter_key";
  private static final String SMS_DELIVERY_REPORT_PREF         = "pref_delivery_report_sms";
  public  static final String MMS_USER_AGENT                   = "pref_mms_user_agent";
  private static final String MMS_CUSTOM_USER_AGENT            = "pref_custom_mms_user_agent";
  private static final String THREAD_TRIM_ENABLED              = "pref_trim_threads";
  private static final String LOCAL_NUMBER_PREF                = "pref_local_number";
  private static final String VERIFYING_STATE_PREF             = "pref_verifying";
  public  static final String REGISTERED_GCM_PREF              = "pref_gcm_registered";
  private static final String GCM_PASSWORD_PREF                = "pref_gcm_password";
  private static final String PROMPTED_PUSH_REGISTRATION_PREF  = "pref_prompted_push_registration";
  private static final String PROMPTED_DEFAULT_SMS_PREF        = "pref_prompted_default_sms";
  private static final String PROMPTED_OPTIMIZE_DOZE_PREF      = "pref_prompted_optimize_doze";
  private static final String PROMPTED_SHARE_PREF              = "pref_prompted_share";
  private static final String SIGNALING_KEY_PREF               = "pref_signaling_key";
  private static final String DIRECTORY_FRESH_TIME_PREF        = "pref_directory_refresh_time";
  private static final String UPDATE_APK_REFRESH_TIME_PREF     = "pref_update_apk_refresh_time";
  private static final String UPDATE_APK_DOWNLOAD_ID           = "pref_update_apk_download_id";
  private static final String UPDATE_APK_DIGEST                = "pref_update_apk_digest";
  private static final String SIGNED_PREKEY_ROTATION_TIME_PREF = "pref_signed_pre_key_rotation_time";
  private static final String IN_THREAD_NOTIFICATION_PREF      = "pref_key_inthread_notifications";
  private static final String SHOW_INVITE_REMINDER_PREF        = "pref_show_invite_reminder";
  public  static final String MESSAGE_BODY_TEXT_SIZE_PREF      = "pref_message_body_text_size";

  private static final String LOCAL_REGISTRATION_ID_PREF       = "pref_local_registration_id";
  private static final String SIGNED_PREKEY_REGISTERED_PREF    = "pref_signed_prekey_registered";
  private static final String WIFI_SMS_PREF                    = "pref_wifi_sms";

  private static final String GCM_DISABLED_PREF                = "pref_gcm_disabled";
  private static final String GCM_REGISTRATION_ID_PREF         = "pref_gcm_registration_id";
  private static final String GCM_REGISTRATION_ID_VERSION_PREF = "pref_gcm_registration_id_version";
  private static final String GCM_REGISTRATION_ID_TIME_PREF    = "pref_gcm_registration_id_last_set_time";
  private static final String WEBSOCKET_REGISTERED_PREF        = "pref_websocket_registered";
  private static final String RATING_LATER_PREF                = "pref_rating_later";
  private static final String RATING_ENABLED_PREF              = "pref_rating_enabled";
  private static final String SIGNED_PREKEY_FAILURE_COUNT_PREF = "pref_signed_prekey_failure_count";

  public  static final String REPEAT_ALERTS_PREF               = "pref_repeat_alerts";
  public  static final String NOTIFICATION_PRIVACY_PREF        = "pref_notification_privacy";
  public  static final String NOTIFICATION_PRIORITY_PREF       = "pref_notification_priority";
  public  static final String NEW_CONTACTS_NOTIFICATIONS       = "pref_enable_new_contacts_notifications";
  public  static final String WEBRTC_CALLING_PREF              = "pref_webrtc_calling";

  public  static final String MEDIA_DOWNLOAD_MOBILE_PREF       = "pref_media_download_mobile";
  public  static final String MEDIA_DOWNLOAD_WIFI_PREF         = "pref_media_download_wifi";
  public  static final String MEDIA_DOWNLOAD_ROAMING_PREF      = "pref_media_download_roaming";

  public  static final String SYSTEM_EMOJI_PREF                = "pref_system_emoji";
  private static final String MULTI_DEVICE_PROVISIONED_PREF    = "pref_multi_device";
  public  static final String DIRECT_CAPTURE_CAMERA_ID         = "pref_direct_capture_camera_id";
  private static final String ALWAYS_RELAY_CALLS_PREF          = "pref_turn_only";
  private static final String PROFILE_KEY_PREF                 = "pref_profile_key";
  private static final String PROFILE_NAME_PREF                = "pref_profile_name";
  private static final String PROFILE_AVATAR_ID_PREF           = "pref_profile_avatar_id";
  public  static final String READ_RECEIPTS_PREF               = "pref_read_receipts";
  public  static final String INCOGNITO_KEYBORAD_PREF          = "pref_incognito_keyboard";
  private static final String UNAUTHORIZED_RECEIVED            = "pref_unauthorized_received";
  private static final String SUCCESSFUL_DIRECTORY_PREF        = "pref_successful_directory";

  private static final String DATABASE_ENCRYPTED_SECRET     = "pref_database_encrypted_secret";
  private static final String DATABASE_UNENCRYPTED_SECRET   = "pref_database_unencrypted_secret";
  private static final String ATTACHMENT_ENCRYPTED_SECRET   = "pref_attachment_encrypted_secret";
  private static final String ATTACHMENT_UNENCRYPTED_SECRET = "pref_attachment_unencrypted_secret";
  private static final String NEEDS_SQLCIPHER_MIGRATION     = "pref_needs_sql_cipher_migration";

  public static final String CALL_NOTIFICATIONS_PREF = "pref_call_notifications";
  public static final String CALL_RINGTONE_PREF      = "pref_call_ringtone";
  public static final String CALL_VIBRATE_PREF       = "pref_call_vibrate";

  private static final String NEXT_PRE_KEY_ID          = "pref_next_pre_key_id";
  private static final String ACTIVE_SIGNED_PRE_KEY_ID = "pref_active_signed_pre_key_id";
  private static final String NEXT_SIGNED_PRE_KEY_ID   = "pref_next_signed_pre_key_id";

  public  static final String BACKUP_ENABLED    = "pref_backup_enabled";
  private static final String BACKUP_PASSPHRASE = "pref_backup_passphrase";
  private static final String BACKUP_TIME       = "pref_backup_next_time";
  public  static final String BACKUP_NOW        = "pref_backup_create";

  public static void setBackupPassphrase(@NonNull Context context, @Nullable String passphrase) {
    setStringPreference(context, BACKUP_PASSPHRASE, passphrase);
  }

  public static @Nullable String getBackupPassphrase(@NonNull Context context) {
    return getStringPreference(context, BACKUP_PASSPHRASE, null);
  }

  public static void setBackupEnabled(@NonNull Context context, boolean value) {
    setBooleanPreference(context, BACKUP_ENABLED, value);
  }

  public static boolean isBackupEnabled(@NonNull Context context) {
    return getBooleanPreference(context, BACKUP_ENABLED, false);
  }

  public static void setNextBackupTime(@NonNull Context context, long time) {
    setLongPreference(context, BACKUP_TIME, time);
  }

  public static long getNextBackupTime(@NonNull Context context) {
    return getLongPreference(context, BACKUP_TIME, -1);
  }

  public static int getNextPreKeyId(@NonNull Context context) {
    return getIntegerPreference(context, NEXT_PRE_KEY_ID, new SecureRandom().nextInt(Medium.MAX_VALUE));
  }

  public static void setNextPreKeyId(@NonNull Context context, int value) {
    setIntegerPrefrence(context, NEXT_PRE_KEY_ID, value);
  }

  public static int getNextSignedPreKeyId(@NonNull Context context) {
    return getIntegerPreference(context, NEXT_SIGNED_PRE_KEY_ID, new SecureRandom().nextInt(Medium.MAX_VALUE));
  }

  public static void setNextSignedPreKeyId(@NonNull Context context, int value) {
    setIntegerPrefrence(context, NEXT_SIGNED_PRE_KEY_ID, value);
  }

  public static int getActiveSignedPreKeyId(@NonNull Context context) {
    return getIntegerPreference(context, ACTIVE_SIGNED_PRE_KEY_ID, -1);
  }

  public static void setActiveSignedPreKeyId(@NonNull Context context, int value) {
    setIntegerPrefrence(context, ACTIVE_SIGNED_PRE_KEY_ID, value);;
  }

  public static void setNeedsSqlCipherMigration(@NonNull Context context, boolean value) {
    setBooleanPreference(context, NEEDS_SQLCIPHER_MIGRATION, value);
    EventBus.getDefault().post(new SqlCipherMigrationRequirementProvider.SqlCipherNeedsMigrationEvent());
  }

  public static boolean getNeedsSqlCipherMigration(@NonNull Context context) {
    return getBooleanPreference(context, NEEDS_SQLCIPHER_MIGRATION, false);
  }

  public static void setAttachmentEncryptedSecret(@NonNull Context context, @NonNull String secret) {
    setStringPreference(context, ATTACHMENT_ENCRYPTED_SECRET, secret);
  }

  public static void setAttachmentUnencryptedSecret(@NonNull Context context, @Nullable String secret) {
    setStringPreference(context, ATTACHMENT_UNENCRYPTED_SECRET, secret);
  }

  public static @Nullable String getAttachmentEncryptedSecret(@NonNull Context context) {
    return getStringPreference(context, ATTACHMENT_ENCRYPTED_SECRET, null);
  }

  public static @Nullable String getAttachmentUnencryptedSecret(@NonNull Context context) {
    return getStringPreference(context, ATTACHMENT_UNENCRYPTED_SECRET, null);
  }

  public static void setDatabaseEncryptedSecret(@NonNull Context context, @NonNull String secret) {
    setStringPreference(context, DATABASE_ENCRYPTED_SECRET, secret);
  }

  public static void setDatabaseUnencryptedSecret(@NonNull Context context, @Nullable String secret) {
    setStringPreference(context, DATABASE_UNENCRYPTED_SECRET, secret);
  }

  public static @Nullable String getDatabaseUnencryptedSecret(@NonNull Context context) {
    return getStringPreference(context, DATABASE_UNENCRYPTED_SECRET, null);
  }

  public static @Nullable String getDatabaseEncryptedSecret(@NonNull Context context) {
    return getStringPreference(context, DATABASE_ENCRYPTED_SECRET, null);
  }

  public static void setHasSuccessfullyRetrievedDirectory(Context context, boolean value) {
    setBooleanPreference(context, SUCCESSFUL_DIRECTORY_PREF, value);
  }

  public static boolean hasSuccessfullyRetrievedDirectory(Context context) {
    return getBooleanPreference(context, SUCCESSFUL_DIRECTORY_PREF, false);
  }

  public static void setUnauthorizedReceived(Context context, boolean value) {
    setBooleanPreference(context, UNAUTHORIZED_RECEIVED, value);
  }

  public static boolean isUnauthorizedRecieved(Context context) {
    return getBooleanPreference(context, UNAUTHORIZED_RECEIVED, false);
  }

  public static boolean isIncognitoKeyboardEnabled(Context context) {
    return getBooleanPreference(context, INCOGNITO_KEYBORAD_PREF, false);
  }

  public static boolean isReadReceiptsEnabled(Context context) {
    return getBooleanPreference(context, READ_RECEIPTS_PREF, false);
  }

  public static void setReadReceiptsEnabled(Context context, boolean enabled) {
    setBooleanPreference(context, READ_RECEIPTS_PREF, enabled);
  }

  public static @Nullable String getProfileKey(Context context) {
    return getStringPreference(context, PROFILE_KEY_PREF, null);
  }

  public static void setProfileKey(Context context, String key) {
    setStringPreference(context, PROFILE_KEY_PREF, key);
  }

  public static void setProfileName(Context context, String name) {
    setStringPreference(context, PROFILE_NAME_PREF, name);
  }

  public static String getProfileName(Context context) {
    return getStringPreference(context, PROFILE_NAME_PREF, null);
  }

  public static void setProfileAvatarId(Context context, int id) {
    setIntegerPrefrence(context, PROFILE_AVATAR_ID_PREF, id);
  }

  public static int getProfileAvatarId(Context context) {
    return getIntegerPreference(context, PROFILE_AVATAR_ID_PREF, 0);
  }

  public static int getNotificationPriority(Context context) {
    return Integer.valueOf(getStringPreference(context, NOTIFICATION_PRIORITY_PREF, String.valueOf(NotificationCompat.PRIORITY_HIGH)));
  }

  public static int getMessageBodyTextSize(Context context) {
    return Integer.valueOf(getStringPreference(context, MESSAGE_BODY_TEXT_SIZE_PREF, "16"));
  }

  public static boolean isTurnOnly(Context context) {
    return getBooleanPreference(context, ALWAYS_RELAY_CALLS_PREF, false);
  }

  public static boolean isGcmDisabled(Context context) {
    return getBooleanPreference(context, GCM_DISABLED_PREF, false);
  }

  public static void setGcmDisabled(Context context, boolean disabled) {
    setBooleanPreference(context, GCM_DISABLED_PREF, disabled);
  }

  public static boolean isWebrtcCallingEnabled(Context context) {
    return getBooleanPreference(context, WEBRTC_CALLING_PREF, false);
  }

  public static void setWebrtcCallingEnabled(Context context, boolean enabled) {
    setBooleanPreference(context, WEBRTC_CALLING_PREF, enabled);
  }

  public static void setDirectCaptureCameraId(Context context, int value) {
    setIntegerPrefrence(context, DIRECT_CAPTURE_CAMERA_ID, value);
  }

  @SuppressWarnings("deprecation")
  public static int getDirectCaptureCameraId(Context context) {
    return getIntegerPreference(context, DIRECT_CAPTURE_CAMERA_ID, CameraInfo.CAMERA_FACING_FRONT);
  }

  public static void setMultiDevice(Context context, boolean value) {
    setBooleanPreference(context, MULTI_DEVICE_PROVISIONED_PREF, value);
  }

  public static boolean isMultiDevice(Context context) {
    return getBooleanPreference(context, MULTI_DEVICE_PROVISIONED_PREF, false);
  }

  public static void setSignedPreKeyFailureCount(Context context, int value) {
    setIntegerPrefrence(context, SIGNED_PREKEY_FAILURE_COUNT_PREF, value);
  }

  public static int getSignedPreKeyFailureCount(Context context) {
    return getIntegerPreference(context, SIGNED_PREKEY_FAILURE_COUNT_PREF, 0);
  }

  public static NotificationPrivacyPreference getNotificationPrivacy(Context context) {
    return new NotificationPrivacyPreference(getStringPreference(context, NOTIFICATION_PRIVACY_PREF, "all"));
  }

  public static boolean isNewContactsNotificationEnabled(Context context) {
    return getBooleanPreference(context, NEW_CONTACTS_NOTIFICATIONS, true);
  }

  public static long getRatingLaterTimestamp(Context context) {
    return getLongPreference(context, RATING_LATER_PREF, 0);
  }

  public static void setRatingLaterTimestamp(Context context, long timestamp) {
    setLongPreference(context, RATING_LATER_PREF, timestamp);
  }

  public static boolean isRatingEnabled(Context context) {
    return getBooleanPreference(context, RATING_ENABLED_PREF, true);
  }

  public static void setRatingEnabled(Context context, boolean enabled) {
    setBooleanPreference(context, RATING_ENABLED_PREF, enabled);
  }

  public static boolean isWebsocketRegistered(Context context) {
    return getBooleanPreference(context, WEBSOCKET_REGISTERED_PREF, false);
  }

  public static void setWebsocketRegistered(Context context, boolean registered) {
    setBooleanPreference(context, WEBSOCKET_REGISTERED_PREF, registered);
  }

  public static boolean isWifiSmsEnabled(Context context) {
    return getBooleanPreference(context, WIFI_SMS_PREF, false);
  }

  public static int getRepeatAlertsCount(Context context) {
    try {
      return Integer.parseInt(getStringPreference(context, REPEAT_ALERTS_PREF, "0"));
    } catch (NumberFormatException e) {
      Log.w(TAG, e);
      return 0;
    }
  }

  public static void setRepeatAlertsCount(Context context, int count) {
    setStringPreference(context, REPEAT_ALERTS_PREF, String.valueOf(count));
  }

  public static boolean isSignedPreKeyRegistered(Context context) {
    return getBooleanPreference(context, SIGNED_PREKEY_REGISTERED_PREF, false);
  }

  public static void setSignedPreKeyRegistered(Context context, boolean value) {
    setBooleanPreference(context, SIGNED_PREKEY_REGISTERED_PREF, value);
  }

  public static void setGcmRegistrationId(Context context, String registrationId) {
    setStringPreference(context, GCM_REGISTRATION_ID_PREF, registrationId);
    setIntegerPrefrence(context, GCM_REGISTRATION_ID_VERSION_PREF, Util.getCurrentApkReleaseVersion(context));
  }

  public static String getGcmRegistrationId(Context context) {
    int storedRegistrationIdVersion = getIntegerPreference(context, GCM_REGISTRATION_ID_VERSION_PREF, 0);

    if (storedRegistrationIdVersion != Util.getCurrentApkReleaseVersion(context)) {
      return null;
    } else {
      return getStringPreference(context, GCM_REGISTRATION_ID_PREF, null);
    }
  }

  public static long getGcmRegistrationIdLastSetTime(Context context) {
    return getLongPreference(context, GCM_REGISTRATION_ID_TIME_PREF, 0);
  }

  public static void setGcmRegistrationIdLastSetTime(Context context, long timestamp) {
    setLongPreference(context, GCM_REGISTRATION_ID_TIME_PREF, timestamp);
  }

  public static boolean isSmsEnabled(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      return Util.isDefaultSmsProvider(context);
    } else {
      return isInterceptAllSmsEnabled(context);
    }
  }

  public static int getLocalRegistrationId(Context context) {
    return getIntegerPreference(context, LOCAL_REGISTRATION_ID_PREF, 0);
  }

  public static void setLocalRegistrationId(Context context, int registrationId) {
    setIntegerPrefrence(context, LOCAL_REGISTRATION_ID_PREF, registrationId);
  }

  public static boolean isInThreadNotifications(Context context) {
    return getBooleanPreference(context, IN_THREAD_NOTIFICATION_PREF, true);
  }

  public static long getSignedPreKeyRotationTime(Context context) {
    return getLongPreference(context, SIGNED_PREKEY_ROTATION_TIME_PREF, 0L);
  }

  public static void setSignedPreKeyRotationTime(Context context, long value) {
    setLongPreference(context, SIGNED_PREKEY_ROTATION_TIME_PREF, value);
  }

  public static long getDirectoryRefreshTime(Context context) {
    return getLongPreference(context, DIRECTORY_FRESH_TIME_PREF, 0L);
  }

  public static void setDirectoryRefreshTime(Context context, long value) {
    setLongPreference(context, DIRECTORY_FRESH_TIME_PREF, value);
  }

  public static long getUpdateApkRefreshTime(Context context) {
    return getLongPreference(context, UPDATE_APK_REFRESH_TIME_PREF, 0L);
  }

  public static void setUpdateApkRefreshTime(Context context, long value) {
    setLongPreference(context, UPDATE_APK_REFRESH_TIME_PREF, value);
  }

  public static void setUpdateApkDownloadId(Context context, long value) {
    setLongPreference(context, UPDATE_APK_DOWNLOAD_ID, value);
  }

  public static long getUpdateApkDownloadId(Context context) {
    return getLongPreference(context, UPDATE_APK_DOWNLOAD_ID, -1);
  }

  public static void setUpdateApkDigest(Context context, String value) {
    setStringPreference(context, UPDATE_APK_DIGEST, value);
  }

  public static String getUpdateApkDigest(Context context) {
    return getStringPreference(context, UPDATE_APK_DIGEST, null);
  }

  public static String getLocalNumber(Context context) {
    return getStringPreference(context, LOCAL_NUMBER_PREF, null);
  }

  public static void setLocalNumber(Context context, String localNumber) {
    setStringPreference(context, LOCAL_NUMBER_PREF, localNumber);
  }

  public static String getPushServerPassword(Context context) {
    return getStringPreference(context, GCM_PASSWORD_PREF, null);
  }

  public static void setPushServerPassword(Context context, String password) {
    setStringPreference(context, GCM_PASSWORD_PREF, password);
  }

  public static void setSignalingKey(Context context, String signalingKey) {
    setStringPreference(context, SIGNALING_KEY_PREF, signalingKey);
  }

  public static String getSignalingKey(Context context) {
    return getStringPreference(context, SIGNALING_KEY_PREF, null);
  }

  public static boolean isEnterImeKeyEnabled(Context context) {
    return getBooleanPreference(context, ENTER_PRESENT_PREF, false);
  }

  public static boolean isEnterSendsEnabled(Context context) {
    return getBooleanPreference(context, ENTER_SENDS_PREF, false);
  }

  public static boolean isPasswordDisabled(Context context) {
    return getBooleanPreference(context, DISABLE_PASSPHRASE_PREF, false);
  }

  public static void setPasswordDisabled(Context context, boolean disabled) {
    setBooleanPreference(context, DISABLE_PASSPHRASE_PREF, disabled);
  }

  public static boolean getUseCustomMmsc(Context context) {
    boolean legacy = TextSecurePreferences.isLegacyUseLocalApnsEnabled(context);
    return getBooleanPreference(context, MMSC_CUSTOM_HOST_PREF, legacy);
  }

  public static void setUseCustomMmsc(Context context, boolean value) {
    setBooleanPreference(context, MMSC_CUSTOM_HOST_PREF, value);
  }

  public static String getMmscUrl(Context context) {
    return getStringPreference(context, MMSC_HOST_PREF, "");
  }

  public static void setMmscUrl(Context context, String mmsc) {
    setStringPreference(context, MMSC_HOST_PREF, mmsc);
  }

  public static boolean getUseCustomMmscProxy(Context context) {
    boolean legacy = TextSecurePreferences.isLegacyUseLocalApnsEnabled(context);
    return getBooleanPreference(context, MMSC_CUSTOM_PROXY_PREF, legacy);
  }

  public static void setUseCustomMmscProxy(Context context, boolean value) {
    setBooleanPreference(context, MMSC_CUSTOM_PROXY_PREF, value);
  }

  public static String getMmscProxy(Context context) {
    return getStringPreference(context, MMSC_PROXY_HOST_PREF, "");
  }

  public static void setMmscProxy(Context context, String value) {
    setStringPreference(context, MMSC_PROXY_HOST_PREF, value);
  }

  public static boolean getUseCustomMmscProxyPort(Context context) {
    boolean legacy = TextSecurePreferences.isLegacyUseLocalApnsEnabled(context);
    return getBooleanPreference(context, MMSC_CUSTOM_PROXY_PORT_PREF, legacy);
  }

  public static void setUseCustomMmscProxyPort(Context context, boolean value) {
    setBooleanPreference(context, MMSC_CUSTOM_PROXY_PORT_PREF, value);
  }

  public static String getMmscProxyPort(Context context) {
    return getStringPreference(context, MMSC_PROXY_PORT_PREF, "");
  }

  public static void setMmscProxyPort(Context context, String value) {
    setStringPreference(context, MMSC_PROXY_PORT_PREF, value);
  }

  public static boolean getUseCustomMmscUsername(Context context) {
    boolean legacy = TextSecurePreferences.isLegacyUseLocalApnsEnabled(context);
    return getBooleanPreference(context, MMSC_CUSTOM_USERNAME_PREF, legacy);
  }

  public static void setUseCustomMmscUsername(Context context, boolean value) {
    setBooleanPreference(context, MMSC_CUSTOM_USERNAME_PREF, value);
  }

  public static String getMmscUsername(Context context) {
    return getStringPreference(context, MMSC_USERNAME_PREF, "");
  }

  public static void setMmscUsername(Context context, String value) {
    setStringPreference(context, MMSC_USERNAME_PREF, value);
  }

  public static boolean getUseCustomMmscPassword(Context context) {
    boolean legacy = TextSecurePreferences.isLegacyUseLocalApnsEnabled(context);
    return getBooleanPreference(context, MMSC_CUSTOM_PASSWORD_PREF, legacy);
  }

  public static void setUseCustomMmscPassword(Context context, boolean value) {
    setBooleanPreference(context, MMSC_CUSTOM_PASSWORD_PREF, value);
  }

  public static String getMmscPassword(Context context) {
    return getStringPreference(context, MMSC_PASSWORD_PREF, "");
  }

  public static void setMmscPassword(Context context, String value) {
    setStringPreference(context, MMSC_PASSWORD_PREF, value);
  }

  public static String getMmsUserAgent(Context context, String defaultUserAgent) {
    boolean useCustom = getBooleanPreference(context, MMS_CUSTOM_USER_AGENT, false);

    if (useCustom) return getStringPreference(context, MMS_USER_AGENT, defaultUserAgent);
    else           return defaultUserAgent;
  }

  public static String getIdentityContactUri(Context context) {
    return getStringPreference(context, IDENTITY_PREF, null);
  }

  public static void setIdentityContactUri(Context context, String identityUri) {
    setStringPreference(context, IDENTITY_PREF, identityUri);
  }

  public static void setScreenSecurityEnabled(Context context, boolean value) {
    setBooleanPreference(context, SCREEN_SECURITY_PREF, value);
  }

  public static boolean isScreenSecurityEnabled(Context context) {
    return getBooleanPreference(context, SCREEN_SECURITY_PREF, false);
  }

  public static boolean isLegacyUseLocalApnsEnabled(Context context) {
    return getBooleanPreference(context, ENABLE_MANUAL_MMS_PREF, false);
  }

  public static int getLastVersionCode(Context context) {
    return getIntegerPreference(context, LAST_VERSION_CODE_PREF, 0);
  }

  public static void setLastVersionCode(Context context, int versionCode) throws IOException {
    if (!setIntegerPrefrenceBlocking(context, LAST_VERSION_CODE_PREF, versionCode)) {
      throw new IOException("couldn't write version code to sharedpreferences");
    }
  }

  public static int getLastExperienceVersionCode(Context context) {
    return getIntegerPreference(context, LAST_EXPERIENCE_VERSION_PREF, 0);
  }

  public static void setLastExperienceVersionCode(Context context, int versionCode) {
    setIntegerPrefrence(context, LAST_EXPERIENCE_VERSION_PREF, versionCode);
  }

  public static int getExperienceDismissedVersionCode(Context context) {
    return getIntegerPreference(context, EXPERIENCE_DISMISSED_PREF, 0);
  }

  public static void setExperienceDismissedVersionCode(Context context, int versionCode) {
    setIntegerPrefrence(context, EXPERIENCE_DISMISSED_PREF, versionCode);
  }

  public static String getTheme(Context context) {
    return getStringPreference(context, THEME_PREF, "light");
  }

  public static boolean isVerifying(Context context) {
    return getBooleanPreference(context, VERIFYING_STATE_PREF, false);
  }

  public static void setVerifying(Context context, boolean verifying) {
    setBooleanPreference(context, VERIFYING_STATE_PREF, verifying);
  }

  public static boolean isPushRegistered(Context context) {
    return getBooleanPreference(context, REGISTERED_GCM_PREF, false);
  }

  public static void setPushRegistered(Context context, boolean registered) {
    Log.w("TextSecurePreferences", "Setting push registered: " + registered);
    setBooleanPreference(context, REGISTERED_GCM_PREF, registered);
  }

  public static boolean isShowInviteReminders(Context context) {
    return getBooleanPreference(context, SHOW_INVITE_REMINDER_PREF, true);
  }

  public static boolean isPassphraseTimeoutEnabled(Context context) {
    return getBooleanPreference(context, PASSPHRASE_TIMEOUT_PREF, false);
  }

  public static int getPassphraseTimeoutInterval(Context context) {
    return getIntegerPreference(context, PASSPHRASE_TIMEOUT_INTERVAL_PREF, 5 * 60);
  }

  public static void setPassphraseTimeoutInterval(Context context, int interval) {
    setIntegerPrefrence(context, PASSPHRASE_TIMEOUT_INTERVAL_PREF, interval);
  }

  public static String getLanguage(Context context) {
    return getStringPreference(context, LANGUAGE_PREF, "zz");
  }

  public static void setLanguage(Context context, String language) {
    setStringPreference(context, LANGUAGE_PREF, language);
  }

  public static boolean isSmsDeliveryReportsEnabled(Context context) {
    return getBooleanPreference(context, SMS_DELIVERY_REPORT_PREF, false);
  }

  public static boolean hasPromptedPushRegistration(Context context) {
    return getBooleanPreference(context, PROMPTED_PUSH_REGISTRATION_PREF, false);
  }

  public static void setPromptedPushRegistration(Context context, boolean value) {
    setBooleanPreference(context, PROMPTED_PUSH_REGISTRATION_PREF, value);
  }

  public static boolean hasPromptedDefaultSmsProvider(Context context) {
    return getBooleanPreference(context, PROMPTED_DEFAULT_SMS_PREF, false);
  }

  public static void setPromptedDefaultSmsProvider(Context context, boolean value) {
    setBooleanPreference(context, PROMPTED_DEFAULT_SMS_PREF, value);
  }

  public static void setPromptedOptimizeDoze(Context context, boolean value) {
    setBooleanPreference(context, PROMPTED_OPTIMIZE_DOZE_PREF, value);
  }

  public static boolean hasPromptedOptimizeDoze(Context context) {
    return getBooleanPreference(context, PROMPTED_OPTIMIZE_DOZE_PREF, false);
  }

  public static boolean hasPromptedShare(Context context) {
    return getBooleanPreference(context, PROMPTED_SHARE_PREF, false);
  }

  public static void setPromptedShare(Context context, boolean value) {
    setBooleanPreference(context, PROMPTED_SHARE_PREF, value);
  }

  public static boolean isInterceptAllMmsEnabled(Context context) {
    return getBooleanPreference(context, ALL_MMS_PREF, true);
  }

  public static boolean isInterceptAllSmsEnabled(Context context) {
    return getBooleanPreference(context, ALL_SMS_PREF, true);
  }

  public static boolean isNotificationsEnabled(Context context) {
    return getBooleanPreference(context, NOTIFICATION_PREF, true);
  }

  public static boolean isCallNotificationsEnabled(Context context) {
    return getBooleanPreference(context, CALL_NOTIFICATIONS_PREF, true);
  }

  public static @NonNull Uri getNotificationRingtone(Context context) {
    String result = getStringPreference(context, RINGTONE_PREF, Settings.System.DEFAULT_NOTIFICATION_URI.toString());

    if (result != null && result.startsWith("file:")) {
      result = Settings.System.DEFAULT_NOTIFICATION_URI.toString();
    }

    return Uri.parse(result);
  }

  public static @NonNull Uri getCallNotificationRingtone(Context context) {
    String result = getStringPreference(context, CALL_RINGTONE_PREF, Settings.System.DEFAULT_RINGTONE_URI.toString());

    if (result != null && result.startsWith("file:")) {
      result = Settings.System.DEFAULT_RINGTONE_URI.toString();
    }

    return Uri.parse(result);
  }

  public static void removeNotificationRingtone(Context context) {
    removePreference(context, RINGTONE_PREF);
  }

  public static void removeCallNotificationRingtone(Context context) {
    removePreference(context, CALL_RINGTONE_PREF);
  }

  public static void setNotificationRingtone(Context context, String ringtone) {
    setStringPreference(context, RINGTONE_PREF, ringtone);
  }

  public static void setCallNotificationRingtone(Context context, String ringtone) {
    setStringPreference(context, CALL_RINGTONE_PREF, ringtone);
  }

  public static boolean isNotificationVibrateEnabled(Context context) {
    return getBooleanPreference(context, VIBRATE_PREF, true);
  }

  public static boolean isCallNotificationVibrateEnabled(Context context) {
    boolean defaultValue = true;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      defaultValue = (Settings.System.getInt(context.getContentResolver(), Settings.System.VIBRATE_WHEN_RINGING, 1) == 1);
    }

    return getBooleanPreference(context, CALL_VIBRATE_PREF, defaultValue);
  }

  public static String getNotificationLedColor(Context context) {
    return getStringPreference(context, LED_COLOR_PREF, "blue");
  }

  public static String getNotificationLedPattern(Context context) {
    return getStringPreference(context, LED_BLINK_PREF, "500,2000");
  }

  public static String getNotificationLedPatternCustom(Context context) {
    return getStringPreference(context, LED_BLINK_PREF_CUSTOM, "500,2000");
  }

  public static void setNotificationLedPatternCustom(Context context, String pattern) {
    setStringPreference(context, LED_BLINK_PREF_CUSTOM, pattern);
  }

  public static boolean isThreadLengthTrimmingEnabled(Context context) {
    return getBooleanPreference(context, THREAD_TRIM_ENABLED, false);
  }

  public static int getThreadTrimLength(Context context) {
    return Integer.parseInt(getStringPreference(context, THREAD_TRIM_LENGTH, "500"));
  }

  public static boolean isSystemEmojiPreferred(Context context) {
    return getBooleanPreference(context, SYSTEM_EMOJI_PREF, false);
  }

  public static @NonNull Set<String> getMobileMediaDownloadAllowed(Context context) {
    return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_MOBILE_PREF, R.array.pref_media_download_mobile_data_default);
  }

  public static @NonNull Set<String> getWifiMediaDownloadAllowed(Context context) {
    return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_WIFI_PREF, R.array.pref_media_download_wifi_default);
  }

  public static @NonNull Set<String> getRoamingMediaDownloadAllowed(Context context) {
    return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_ROAMING_PREF, R.array.pref_media_download_roaming_default);
  }

  private static @NonNull Set<String> getMediaDownloadAllowed(Context context, String key, @ArrayRes int defaultValuesRes) {
    return getStringSetPreference(context,
                                  key,
                                  new HashSet<>(Arrays.asList(context.getResources().getStringArray(defaultValuesRes))));
  }

  public static void setBooleanPreference(Context context, String key, boolean value) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(key, value).apply();
  }

  public static boolean getBooleanPreference(Context context, String key, boolean defaultValue) {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, defaultValue);
  }

  public static void setStringPreference(Context context, String key, String value) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(key, value).apply();
  }

  public static String getStringPreference(Context context, String key, String defaultValue) {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(key, defaultValue);
  }

  private static int getIntegerPreference(Context context, String key, int defaultValue) {
    return PreferenceManager.getDefaultSharedPreferences(context).getInt(key, defaultValue);
  }

  private static void setIntegerPrefrence(Context context, String key, int value) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(key, value).apply();
  }

  private static boolean setIntegerPrefrenceBlocking(Context context, String key, int value) {
    return PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(key, value).commit();
  }

  private static long getLongPreference(Context context, String key, long defaultValue) {
    return PreferenceManager.getDefaultSharedPreferences(context).getLong(key, defaultValue);
  }

  private static void setLongPreference(Context context, String key, long value) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(key, value).apply();
  }

  private static void removePreference(Context context, String key) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().remove(key).apply();
  }

  private static Set<String> getStringSetPreference(Context context, String key, Set<String> defaultValues) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    if (prefs.contains(key)) {
      return prefs.getStringSet(key, Collections.<String>emptySet());
    } else {
      return defaultValues;
    }
  }
}
