package org.thoughtcrime.securesms.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera.CameraInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.PendingIntentFlags;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.proto.SharedPreference;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.impl.SqlCipherMigrationConstraintObserver;
import org.thoughtcrime.securesms.keyvalue.SettingsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.RegistrationLockReminders;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.NotificationIds;
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.registration.RegistrationNavigationActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TextSecurePreferences {

  private static final String TAG = Log.tag(TextSecurePreferences.class);

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
  public  static final String ENABLE_MANUAL_MMS_PREF           = "pref_enable_manual_mms";

  private static final String LAST_VERSION_CODE_PREF           = "last_version_code";
  public  static final String RINGTONE_PREF                    = "pref_key_ringtone";
  public  static final String VIBRATE_PREF                     = "pref_key_vibrate";
  private static final String NOTIFICATION_PREF                = "pref_key_enable_notifications";
  public  static final String LED_COLOR_PREF                   = "pref_led_color";
  public  static final String LED_BLINK_PREF                   = "pref_led_blink";
  private static final String LED_BLINK_PREF_CUSTOM            = "pref_led_blink_custom";
  public  static final String ALL_MMS_PREF                     = "pref_all_mms";
  public  static final String ALL_SMS_PREF                     = "pref_all_sms";
  public  static final String PASSPHRASE_TIMEOUT_INTERVAL_PREF = "pref_timeout_interval";
  public  static final String PASSPHRASE_TIMEOUT_PREF          = "pref_timeout_passphrase";
  public  static final String SCREEN_SECURITY_PREF             = "pref_screen_security";
  private static final String ENTER_SENDS_PREF                 = "pref_enter_sends";
  private static final String ENTER_PRESENT_PREF               = "pref_enter_key";
  private static final String SMS_DELIVERY_REPORT_PREF         = "pref_delivery_report_sms";
  public  static final String MMS_USER_AGENT                   = "pref_mms_user_agent";
  private static final String MMS_CUSTOM_USER_AGENT            = "pref_custom_mms_user_agent";
  private static final String SEEN_WELCOME_SCREEN_PREF         = "pref_seen_welcome_screen";
  private static final String PROMPTED_PUSH_REGISTRATION_PREF  = "pref_prompted_push_registration";
  private static final String PROMPTED_OPTIMIZE_DOZE_PREF      = "pref_prompted_optimize_doze";
  private static final String DIRECTORY_FRESH_TIME_PREF        = "pref_directory_refresh_time";
  private static final String UPDATE_APK_REFRESH_TIME_PREF     = "pref_update_apk_refresh_time";
  private static final String UPDATE_APK_DOWNLOAD_ID           = "pref_update_apk_download_id";
  private static final String UPDATE_APK_DIGEST                = "pref_update_apk_digest";
  private static final String SIGNED_PREKEY_ROTATION_TIME_PREF = "pref_signed_pre_key_rotation_time";

  private static final String IN_THREAD_NOTIFICATION_PREF      = "pref_key_inthread_notifications";
  private static final String SHOW_INVITE_REMINDER_PREF        = "pref_show_invite_reminder";
  public  static final String MESSAGE_BODY_TEXT_SIZE_PREF      = "pref_message_body_text_size";

  private static final String WIFI_SMS_PREF                    = "pref_wifi_sms";

  private static final String RATING_LATER_PREF                = "pref_rating_later";
  private static final String RATING_ENABLED_PREF              = "pref_rating_enabled";

  public  static final String REPEAT_ALERTS_PREF               = "pref_repeat_alerts";
  public  static final String NOTIFICATION_PRIVACY_PREF        = "pref_notification_privacy";
  public  static final String NOTIFICATION_PRIORITY_PREF       = "pref_notification_priority";
  public  static final String NEW_CONTACTS_NOTIFICATIONS       = "pref_enable_new_contacts_notifications";
  public  static final String WEBRTC_CALLING_PREF              = "pref_webrtc_calling";

  public  static final String MEDIA_DOWNLOAD_MOBILE_PREF       = "pref_media_download_mobile";
  public  static final String MEDIA_DOWNLOAD_WIFI_PREF         = "pref_media_download_wifi";
  public  static final String MEDIA_DOWNLOAD_ROAMING_PREF      = "pref_media_download_roaming";

  public  static final String CALL_BANDWIDTH_PREF              = "pref_data_call_bandwidth";

  public  static final String SYSTEM_EMOJI_PREF                = "pref_system_emoji";
  private static final String MULTI_DEVICE_PROVISIONED_PREF    = "pref_multi_device";
  public  static final String DIRECT_CAPTURE_CAMERA_ID         = "pref_direct_capture_camera_id";
  public  static final String ALWAYS_RELAY_CALLS_PREF          = "pref_turn_only";
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

  public  static final String BACKUP                      = "pref_backup";
  public  static final String BACKUP_ENABLED              = "pref_backup_enabled";
  private static final String BACKUP_PASSPHRASE           = "pref_backup_passphrase";
  private static final String ENCRYPTED_BACKUP_PASSPHRASE = "pref_encrypted_backup_passphrase";
  private static final String BACKUP_TIME                 = "pref_backup_next_time";

  public static final String TRANSFER = "pref_transfer";

  public static final String SCREEN_LOCK         = "pref_android_screen_lock";
  public static final String SCREEN_LOCK_TIMEOUT = "pref_android_screen_lock_timeout";

  @Deprecated
  public static final  String REGISTRATION_LOCK_PREF_V1                = "pref_registration_lock";
  @Deprecated
  private static final String REGISTRATION_LOCK_PIN_PREF_V1            = "pref_registration_lock_pin";

  public static final  String REGISTRATION_LOCK_PREF_V2                = "pref_registration_lock_v2";

  private static final String REGISTRATION_LOCK_LAST_REMINDER_TIME_POST_KBS = "pref_registration_lock_last_reminder_time_post_kbs";
  private static final String REGISTRATION_LOCK_NEXT_REMINDER_INTERVAL      = "pref_registration_lock_next_reminder_interval";

  public  static final String SIGNAL_PIN_CHANGE = "pref_kbs_change";

  private static final String SERVICE_OUTAGE         = "pref_service_outage";
  private static final String LAST_OUTAGE_CHECK_TIME = "pref_last_outage_check_time";

  private static final String LAST_FULL_CONTACT_SYNC_TIME = "pref_last_full_contact_sync_time";
  private static final String NEEDS_FULL_CONTACT_SYNC     = "pref_needs_full_contact_sync";

  private static final String LOG_ENCRYPTED_SECRET   = "pref_log_encrypted_secret";
  private static final String LOG_UNENCRYPTED_SECRET = "pref_log_unencrypted_secret";

  private static final String NOTIFICATION_CHANNEL_VERSION          = "pref_notification_channel_version";
  private static final String NOTIFICATION_MESSAGES_CHANNEL_VERSION = "pref_notification_messages_channel_version";

  private static final String NEEDS_MESSAGE_PULL = "pref_needs_message_pull";

  private static final String UNIDENTIFIED_ACCESS_CERTIFICATE_ROTATION_TIME_PREF = "pref_unidentified_access_certificate_rotation_time";
  public  static final String UNIVERSAL_UNIDENTIFIED_ACCESS                      = "pref_universal_unidentified_access";
  public  static final String SHOW_UNIDENTIFIED_DELIVERY_INDICATORS              = "pref_show_unidentifed_delivery_indicators";
  private static final String UNIDENTIFIED_DELIVERY_ENABLED                      = "pref_unidentified_delivery_enabled";

  public static final String TYPING_INDICATORS = "pref_typing_indicators";

  public static final String LINK_PREVIEWS = "pref_link_previews";

  private static final String SEEN_STICKER_INTRO_TOOLTIP = "pref_seen_sticker_intro_tooltip";

  private static final String MEDIA_KEYBOARD_MODE = "pref_media_keyboard_mode";
  public  static final String RECENT_STORAGE_KEY  = "pref_recent_emoji2";

  private static final String VIEW_ONCE_TOOLTIP_SEEN = "pref_revealable_message_tooltip_seen";

  private static final String SEEN_CAMERA_FIRST_TOOLTIP = "pref_seen_camera_first_tooltip";

  private static final String JOB_MANAGER_VERSION = "pref_job_manager_version";

  private static final String APP_MIGRATION_VERSION = "pref_app_migration_version";

  private static final String FIRST_INSTALL_VERSION = "pref_first_install_version";

  private static final String HAS_SEEN_SWIPE_TO_REPLY = "pref_has_seen_swipe_to_reply";

  private static final String HAS_SEEN_VIDEO_RECORDING_TOOLTIP = "camerax.fragment.has.dismissed.video.recording.tooltip";

  private static final String STORAGE_MANIFEST_VERSION = "pref_storage_manifest_version";

  private static final String ARGON2_TESTED = "argon2_tested";

  private static final String[] booleanPreferencesToBackup = {SCREEN_SECURITY_PREF,
                                                              INCOGNITO_KEYBORAD_PREF,
                                                              ALWAYS_RELAY_CALLS_PREF,
                                                              READ_RECEIPTS_PREF,
                                                              TYPING_INDICATORS,
                                                              SHOW_UNIDENTIFIED_DELIVERY_INDICATORS,
                                                              UNIVERSAL_UNIDENTIFIED_ACCESS,
                                                              NOTIFICATION_PREF,
                                                              VIBRATE_PREF,
                                                              IN_THREAD_NOTIFICATION_PREF,
                                                              CALL_NOTIFICATIONS_PREF,
                                                              CALL_VIBRATE_PREF,
                                                              NEW_CONTACTS_NOTIFICATIONS,
                                                              SHOW_INVITE_REMINDER_PREF,
                                                              SYSTEM_EMOJI_PREF,
                                                              ENTER_SENDS_PREF};

  private static final String[] stringPreferencesToBackup = {LED_COLOR_PREF,
                                                             LED_BLINK_PREF,
                                                             REPEAT_ALERTS_PREF,
                                                             NOTIFICATION_PRIVACY_PREF,
                                                             THEME_PREF,
                                                             LANGUAGE_PREF,
                                                             MESSAGE_BODY_TEXT_SIZE_PREF};

  private static final String[] stringSetPreferencesToBackup = {MEDIA_DOWNLOAD_MOBILE_PREF,
                                                                MEDIA_DOWNLOAD_WIFI_PREF,
                                                                MEDIA_DOWNLOAD_ROAMING_PREF};

  private static volatile SharedPreferences preferences = null;

  public static long getPreferencesToSaveToBackupCount(@NonNull Context context) {
    SharedPreferences preferences = getSharedPreferences(context);
    long              count       = 0;

    for (String booleanPreference : booleanPreferencesToBackup) {
      if (preferences.contains(booleanPreference)) {
        count++;
      }
    }

    for (String stringPreference : stringPreferencesToBackup) {
      if (preferences.contains(stringPreference)) {
        count++;
      }
    }

    for (String stringSetPreference : stringSetPreferencesToBackup) {
      if (preferences.contains(stringSetPreference)) {
        count++;
      }
    }

    return count;
  }

  public static List<SharedPreference> getPreferencesToSaveToBackup(@NonNull Context context) {
    SharedPreferences      preferences  = getSharedPreferences(context);
    List<SharedPreference> backupProtos = new ArrayList<>();
    String                 defaultFile  = context.getPackageName() + "_preferences";

    for (String booleanPreference : booleanPreferencesToBackup) {
      if (preferences.contains(booleanPreference)) {
        backupProtos.add(new SharedPreference.Builder()
                                             .file_(defaultFile)
                                             .key(booleanPreference)
                                             .booleanValue(preferences.getBoolean(booleanPreference, false))
                                             .build());
      }
    }

    for (String stringPreference : stringPreferencesToBackup) {
      if (preferences.contains(stringPreference)) {
        backupProtos.add(new SharedPreference.Builder()
                                             .file_(defaultFile)
                                             .key(stringPreference)
                                             .value_(preferences.getString(stringPreference, null))
                                             .build());
      }
    }

    for (String stringSetPreference : stringSetPreferencesToBackup) {
      if (preferences.contains(stringSetPreference)) {
        backupProtos.add(new SharedPreference.Builder()
                                             .file_(defaultFile)
                                             .key(stringSetPreference)
                                             .isStringSetValue(true)
                                             .stringSetValue(new ArrayList<>(preferences.getStringSet(stringSetPreference, Collections.emptySet())))
                                             .build());
      }
    }

    return backupProtos;
  }

  public static void onPostBackupRestore(@NonNull Context context) {
    if (NotificationChannels.supported()) {
      NotificationChannels.getInstance().updateMessageVibrate(SignalStore.settings().isMessageVibrateEnabled());
    }
  }

  public static boolean isScreenLockEnabled(@NonNull Context context) {
    return getBooleanPreference(context, SCREEN_LOCK, false);
  }

  public static void setScreenLockEnabled(@NonNull Context context, boolean value) {
    setBooleanPreference(context, SCREEN_LOCK, value);
  }

  public static long getScreenLockTimeout(@NonNull Context context) {
    return getLongPreference(context, SCREEN_LOCK_TIMEOUT, 0);
  }

  public static void setScreenLockTimeout(@NonNull Context context, long value) {
    setLongPreference(context, SCREEN_LOCK_TIMEOUT, value);
  }

  public static boolean isV1RegistrationLockEnabled(@NonNull Context context) {
    //noinspection deprecation
    return getBooleanPreference(context, REGISTRATION_LOCK_PREF_V1, false);
  }

  /**
   * @deprecated Use only during re-reg where user had pinV1.
   */
  @Deprecated
  public static void setV1RegistrationLockEnabled(@NonNull Context context, boolean value) {
    //noinspection deprecation
    setBooleanPreference(context, REGISTRATION_LOCK_PREF_V1, value);
  }

  /**
   * @deprecated Use only for migrations to the Key Backup Store registration pinV2.
   */
  @Deprecated
  public static @Nullable String getDeprecatedV1RegistrationLockPin(@NonNull Context context) {
    //noinspection deprecation
    return getStringPreference(context, REGISTRATION_LOCK_PIN_PREF_V1, null);
  }

  public static void clearRegistrationLockV1(@NonNull Context context) {
    //noinspection deprecation
    getSharedPreferences(context)
                     .edit()
                     .remove(REGISTRATION_LOCK_PIN_PREF_V1)
                     .apply();
  }

  /**
   * @deprecated Use only for migrations to the Key Backup Store registration pinV2.
   */
  @Deprecated
  public static void setV1RegistrationLockPin(@NonNull Context context, String pin) {
    //noinspection deprecation
    setStringPreference(context, REGISTRATION_LOCK_PIN_PREF_V1, pin);
  }

  public static long getRegistrationLockLastReminderTime(@NonNull Context context) {
    return getLongPreference(context, REGISTRATION_LOCK_LAST_REMINDER_TIME_POST_KBS, 0);
  }

  public static void setRegistrationLockLastReminderTime(@NonNull Context context, long time) {
    setLongPreference(context, REGISTRATION_LOCK_LAST_REMINDER_TIME_POST_KBS, time);
  }

  public static long getRegistrationLockNextReminderInterval(@NonNull Context context) {
    return getLongPreference(context, REGISTRATION_LOCK_NEXT_REMINDER_INTERVAL, RegistrationLockReminders.INITIAL_INTERVAL);
  }

  public static void setRegistrationLockNextReminderInterval(@NonNull Context context, long value) {
    setLongPreference(context, REGISTRATION_LOCK_NEXT_REMINDER_INTERVAL, value);
  }

  public static void setBackupPassphrase(@NonNull Context context, @Nullable String passphrase) {
    setStringPreference(context, BACKUP_PASSPHRASE, passphrase);
  }

  public static @Nullable String getBackupPassphrase(@NonNull Context context) {
    return getStringPreference(context, BACKUP_PASSPHRASE, null);
  }

  public static void setEncryptedBackupPassphrase(@NonNull Context context, @Nullable String encryptedPassphrase) {
    setStringPreference(context, ENCRYPTED_BACKUP_PASSPHRASE, encryptedPassphrase);
  }

  public static @Nullable String getEncryptedBackupPassphrase(@NonNull Context context) {
    return getStringPreference(context, ENCRYPTED_BACKUP_PASSPHRASE, null);
  }

  @Deprecated
  public static boolean isBackupEnabled(@NonNull Context context) {
    return getBooleanPreference(context, BACKUP_ENABLED, false);
  }

  public static void setNextBackupTime(@NonNull Context context, long time) {
    setLongPreference(context, BACKUP_TIME, time);
  }

  public static long getNextBackupTime(@NonNull Context context) {
    return getLongPreference(context, BACKUP_TIME, -1);
  }

  public static void setNeedsSqlCipherMigration(@NonNull Context context, boolean value) {
    setBooleanPreference(context, NEEDS_SQLCIPHER_MIGRATION, value);
    EventBus.getDefault().post(new SqlCipherMigrationConstraintObserver.SqlCipherNeedsMigrationEvent());
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
    boolean previous = isUnauthorizedReceived(context);
    setBooleanPreference(context, UNAUTHORIZED_RECEIVED, value);

    if (previous != value) {
      Recipient.self().live().refresh();
      if (value) {
        notifyUnregisteredReceived(context);
      }
    }

    if (value) {
      clearLocalCredentials(context);
    }
  }

  public static boolean isUnauthorizedReceived(Context context) {
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

  public static boolean isTypingIndicatorsEnabled(Context context) {
    return getBooleanPreference(context, TYPING_INDICATORS, false);
  }

  public static void setTypingIndicatorsEnabled(Context context, boolean enabled) {
    setBooleanPreference(context, TYPING_INDICATORS, enabled);
  }

  /**
   * Only kept so that we can avoid showing the megaphone for the new link previews setting
   * ({@link SettingsValues#isLinkPreviewsEnabled()}) when users upgrade. This can be removed after
   * we stop showing the link previews megaphone.
   */
  public static boolean wereLinkPreviewsEnabled(Context context) {
    return getBooleanPreference(context, LINK_PREVIEWS, true);
  }

  public static int getNotificationPriority(Context context) {
    try {
      return Integer.parseInt(getStringPreference(context, NOTIFICATION_PRIORITY_PREF, String.valueOf(NotificationCompat.PRIORITY_HIGH)));
    } catch (ClassCastException e) {
      return getIntegerPreference(context, NOTIFICATION_PRIORITY_PREF, NotificationCompat.PRIORITY_HIGH);
    }
  }

  /**
   * @deprecated Use {@link SettingsValues#getMessageFontSize()} via {@link org.thoughtcrime.securesms.keyvalue.SignalStore} instead.
   */
  public static int getMessageBodyTextSize(Context context) {
    return Integer.parseInt(getStringPreference(context, MESSAGE_BODY_TEXT_SIZE_PREF, "16"));
  }

  public static boolean isTurnOnly(Context context) {
    return getBooleanPreference(context, ALWAYS_RELAY_CALLS_PREF, false);
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

  @Deprecated
  public static NotificationPrivacyPreference getNotificationPrivacy(Context context) {
    return new NotificationPrivacyPreference(getStringPreference(context, NOTIFICATION_PRIVACY_PREF, "all"));
  }

  public static boolean isNewContactsNotificationEnabled(Context context) {
    return getBooleanPreference(context, NEW_CONTACTS_NOTIFICATIONS, false);
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

  @Deprecated
  public static boolean isWifiSmsEnabled(Context context) {
    return getBooleanPreference(context, WIFI_SMS_PREF, false);
  }

  @Deprecated
  public static int getRepeatAlertsCount(Context context) {
    try {
      return Integer.parseInt(getStringPreference(context, REPEAT_ALERTS_PREF, "0"));
    } catch (NumberFormatException e) {
      Log.w(TAG, e);
      return 0;
    }
  }

  @Deprecated
  public static boolean isInThreadNotifications(Context context) {
    return getBooleanPreference(context, IN_THREAD_NOTIFICATION_PREF, true);
  }

  public static long getUnidentifiedAccessCertificateRotationTime(Context context) {
    return getLongPreference(context, UNIDENTIFIED_ACCESS_CERTIFICATE_ROTATION_TIME_PREF, 0L);
  }

  public static void setUnidentifiedAccessCertificateRotationTime(Context context, long value) {
    setLongPreference(context, UNIDENTIFIED_ACCESS_CERTIFICATE_ROTATION_TIME_PREF, value);
  }

  public static boolean isUniversalUnidentifiedAccess(Context context) {
    return getBooleanPreference(context, UNIVERSAL_UNIDENTIFIED_ACCESS, false);
  }

  public static void setShowUnidentifiedDeliveryIndicatorsEnabled(Context context, boolean enabled) {
    setBooleanPreference(context, SHOW_UNIDENTIFIED_DELIVERY_INDICATORS, enabled);
  }

  public static boolean isShowUnidentifiedDeliveryIndicatorsEnabled(Context context) {
    return getBooleanPreference(context, SHOW_UNIDENTIFIED_DELIVERY_INDICATORS, false);
  }

  public static void setIsUnidentifiedDeliveryEnabled(Context context, boolean enabled) {
    setBooleanPreference(context, UNIDENTIFIED_DELIVERY_ENABLED, enabled);
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

  public static boolean isEnterImeKeyEnabled(Context context) {
    return getBooleanPreference(context, ENTER_PRESENT_PREF, false);
  }

  @Deprecated
  public static boolean isEnterSendsEnabled(Context context) {
    return getBooleanPreference(context, ENTER_SENDS_PREF, false);
  }

  public static boolean isPasswordDisabled(Context context) {
    return getBooleanPreference(context, DISABLE_PASSPHRASE_PREF, true);
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
    return getIntegerPreference(context, LAST_VERSION_CODE_PREF, Util.getCanonicalVersionCode());
  }

  public static void setLastVersionCode(Context context, int versionCode) {
    if (!setIntegerPrefrenceBlocking(context, LAST_VERSION_CODE_PREF, versionCode)) {
      throw new AssertionError("couldn't write version code to sharedpreferences");
    }
  }

  /**
   * @deprecated Use {@link SettingsValues#getTheme()} via {@link org.thoughtcrime.securesms.keyvalue.SignalStore} instead.
   */
  public static String getTheme(Context context) {
    return getStringPreference(context, THEME_PREF, DynamicTheme.systemThemeAvailable() ? "system" : "light");
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

  /**
   * @deprecated Use {@link SettingsValues#getLanguage()} via {@link org.thoughtcrime.securesms.keyvalue.SignalStore} instead.
   */
  public static String getLanguage(Context context) {
    return getStringPreference(context, LANGUAGE_PREF, "zz");
  }

  /**
   * @deprecated Use {@link SettingsValues#setLanguage(String)} via {@link org.thoughtcrime.securesms.keyvalue.SignalStore} instead.
   */
  public static void setLanguage(Context context, String language) {
    setStringPreference(context, LANGUAGE_PREF, language);
  }

  @Deprecated
  public static boolean isSmsDeliveryReportsEnabled(Context context) {
    return getBooleanPreference(context, SMS_DELIVERY_REPORT_PREF, false);
  }

  public static boolean hasSeenWelcomeScreen(Context context) {
    return getBooleanPreference(context, SEEN_WELCOME_SCREEN_PREF, true);
  }

  public static void setHasSeenWelcomeScreen(Context context, boolean value) {
    setBooleanPreference(context, SEEN_WELCOME_SCREEN_PREF, value);
  }

  public static boolean hasPromptedPushRegistration(Context context) {
    return getBooleanPreference(context, PROMPTED_PUSH_REGISTRATION_PREF, false);
  }

  public static void setPromptedPushRegistration(Context context, boolean value) {
    setBooleanPreference(context, PROMPTED_PUSH_REGISTRATION_PREF, value);
  }

  public static void setPromptedOptimizeDoze(Context context, boolean value) {
    setBooleanPreference(context, PROMPTED_OPTIMIZE_DOZE_PREF, value);
  }

  public static boolean hasPromptedOptimizeDoze(Context context) {
    return getBooleanPreference(context, PROMPTED_OPTIMIZE_DOZE_PREF, false);
  }

  public static boolean isInterceptAllMmsEnabled(Context context) {
    return getBooleanPreference(context, ALL_MMS_PREF, true);
  }

  public static boolean isInterceptAllSmsEnabled(Context context) {
    return getBooleanPreference(context, ALL_SMS_PREF, true);
  }

  @Deprecated
  public static boolean isNotificationsEnabled(Context context) {
    return getBooleanPreference(context, NOTIFICATION_PREF, true);
  }

  @Deprecated
  public static boolean isCallNotificationsEnabled(Context context) {
    return getBooleanPreference(context, CALL_NOTIFICATIONS_PREF, true);
  }

  @Deprecated
  public static @NonNull Uri getNotificationRingtone(Context context) {
    String result = getStringPreference(context, RINGTONE_PREF, Settings.System.DEFAULT_NOTIFICATION_URI.toString());

    if (result != null && result.startsWith("file:")) {
      result = Settings.System.DEFAULT_NOTIFICATION_URI.toString();
    }

    return Uri.parse(result);
  }

  @Deprecated
  public static @NonNull Uri getCallNotificationRingtone(Context context) {
    String result = getStringPreference(context, CALL_RINGTONE_PREF, Settings.System.DEFAULT_RINGTONE_URI.toString());

    if (result != null && result.startsWith("file:")) {
      result = Settings.System.DEFAULT_RINGTONE_URI.toString();
    }

    return Uri.parse(result);
  }

  @Deprecated
  public static boolean isNotificationVibrateEnabled(Context context) {
    return getBooleanPreference(context, VIBRATE_PREF, true);
  }

  @Deprecated
  public static boolean isCallNotificationVibrateEnabled(Context context) {
    boolean defaultValue = true;

    if (Build.VERSION.SDK_INT >= 23) {
      defaultValue = (Settings.System.getInt(context.getContentResolver(), Settings.System.VIBRATE_WHEN_RINGING, 1) == 1);
    }

    return getBooleanPreference(context, CALL_VIBRATE_PREF, defaultValue);
  }

  @Deprecated
  public static String getNotificationLedColor(Context context) {
    return getStringPreference(context, LED_COLOR_PREF, "blue");
  }

  @Deprecated
  public static String getNotificationLedPattern(Context context) {
    return getStringPreference(context, LED_BLINK_PREF, "500,2000");
  }

  public static String getNotificationLedPatternCustom(Context context) {
    return getStringPreference(context, LED_BLINK_PREF_CUSTOM, "500,2000");
  }

  public static void setNotificationLedPatternCustom(Context context, String pattern) {
    setStringPreference(context, LED_BLINK_PREF_CUSTOM, pattern);
  }

  @Deprecated
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

  public static void setLastOutageCheckTime(Context context, long timestamp) {
    setLongPreference(context, LAST_OUTAGE_CHECK_TIME, timestamp);
  }

  public static long getLastOutageCheckTime(Context context) {
    return getLongPreference(context, LAST_OUTAGE_CHECK_TIME, 0);
  }

  public static void setServiceOutage(Context context, boolean isOutage) {
    setBooleanPreference(context, SERVICE_OUTAGE, isOutage);
  }

  public static boolean getServiceOutage(Context context) {
    return getBooleanPreference(context, SERVICE_OUTAGE, false);
  }

  public static long getLastFullContactSyncTime(Context context) {
    return getLongPreference(context, LAST_FULL_CONTACT_SYNC_TIME, 0);
  }

  public static void setLastFullContactSyncTime(Context context, long timestamp) {
    setLongPreference(context, LAST_FULL_CONTACT_SYNC_TIME, timestamp);
  }

  public static boolean needsFullContactSync(Context context) {
    return getBooleanPreference(context, NEEDS_FULL_CONTACT_SYNC, false);
  }

  public static void setNeedsFullContactSync(Context context, boolean needsSync) {
    setBooleanPreference(context, NEEDS_FULL_CONTACT_SYNC, needsSync);
  }

  public static void setLogEncryptedSecret(Context context, String base64Secret) {
    setStringPreference(context, LOG_ENCRYPTED_SECRET, base64Secret);
  }

  public static String getLogEncryptedSecret(Context context) {
    return getStringPreference(context, LOG_ENCRYPTED_SECRET, null);
  }

  public static void setLogUnencryptedSecret(Context context, String base64Secret) {
    setStringPreference(context, LOG_UNENCRYPTED_SECRET, base64Secret);
  }

  public static String getLogUnencryptedSecret(Context context) {
    return getStringPreference(context, LOG_UNENCRYPTED_SECRET, null);
  }

  public static int getNotificationChannelVersion(Context context) {
    return getIntegerPreference(context, NOTIFICATION_CHANNEL_VERSION, 1);
  }

  public static void setNotificationChannelVersion(Context context, int version) {
    setIntegerPrefrence(context, NOTIFICATION_CHANNEL_VERSION, version);
  }

  public static int getNotificationMessagesChannelVersion(Context context) {
    return getIntegerPreference(context, NOTIFICATION_MESSAGES_CHANNEL_VERSION, 1);
  }

  public static void setNotificationMessagesChannelVersion(Context context, int version) {
    setIntegerPrefrence(context, NOTIFICATION_MESSAGES_CHANNEL_VERSION, version);
  }

  public static boolean getNeedsMessagePull(Context context) {
    return getBooleanPreference(context, NEEDS_MESSAGE_PULL, false);
  }

  public static void setNeedsMessagePull(Context context, boolean needsMessagePull) {
    setBooleanPreference(context, NEEDS_MESSAGE_PULL, needsMessagePull);
  }

  public static boolean hasSeenStickerIntroTooltip(Context context) {
    return getBooleanPreference(context, SEEN_STICKER_INTRO_TOOLTIP, false);
  }

  public static void setHasSeenStickerIntroTooltip(Context context, boolean seenStickerTooltip) {
    setBooleanPreference(context, SEEN_STICKER_INTRO_TOOLTIP, seenStickerTooltip);
  }

  public static void setMediaKeyboardMode(Context context, MediaKeyboardMode mode) {
    setStringPreference(context, MEDIA_KEYBOARD_MODE, mode.name());
  }

  public static MediaKeyboardMode getMediaKeyboardMode(Context context) {
    String name = getStringPreference(context, MEDIA_KEYBOARD_MODE, MediaKeyboardMode.EMOJI.name());
    return MediaKeyboardMode.valueOf(name);
  }

  public static void setHasSeenViewOnceTooltip(Context context, boolean value) {
    setBooleanPreference(context, VIEW_ONCE_TOOLTIP_SEEN, value);
  }

  public static boolean hasSeenViewOnceTooltip(Context context) {
    return getBooleanPreference(context, VIEW_ONCE_TOOLTIP_SEEN, false);
  }

  public static void setHasSeenCameraFirstTooltip(Context context, boolean value) {
    setBooleanPreference(context, SEEN_CAMERA_FIRST_TOOLTIP, value);
  }

  public static boolean hasSeenCameraFirstTooltip(Context context) {
    return getBooleanPreference(context, SEEN_CAMERA_FIRST_TOOLTIP, false);
  }

  public static void setJobManagerVersion(Context context, int version) {
    setIntegerPrefrence(context, JOB_MANAGER_VERSION, version);
  }

  public static int getJobManagerVersion(Context contex) {
    return getIntegerPreference(contex, JOB_MANAGER_VERSION, 1);
  }

  public static void setAppMigrationVersion(Context context, int version) {
    setIntegerPrefrence(context, APP_MIGRATION_VERSION, version);
  }

  public static int getAppMigrationVersion(Context context) {
    return getIntegerPreference(context, APP_MIGRATION_VERSION, 1);
  }

  public static void setFirstInstallVersion(Context context, int version) {
    setIntegerPrefrence(context, FIRST_INSTALL_VERSION, version);
  }

  public static int getFirstInstallVersion(Context context) {
    return getIntegerPreference(context, FIRST_INSTALL_VERSION, -1);
  }

  public static boolean hasSeenSwipeToReplyTooltip(Context context) {
    return getBooleanPreference(context, HAS_SEEN_SWIPE_TO_REPLY, false);
  }

  public static void setHasSeenSwipeToReplyTooltip(Context context, boolean value) {
    setBooleanPreference(context, HAS_SEEN_SWIPE_TO_REPLY, value);
  }

  public static boolean hasSeenVideoRecordingTooltip(Context context) {
    return getBooleanPreference(context, HAS_SEEN_VIDEO_RECORDING_TOOLTIP, false);
  }

  public static void setHasSeenVideoRecordingTooltip(Context context, boolean value) {
    setBooleanPreference(context, HAS_SEEN_VIDEO_RECORDING_TOOLTIP, value);
  }

  public static void setStorageManifestVersion(Context context, long version) {
    setLongPreference(context, STORAGE_MANIFEST_VERSION, version);
  }

  public static boolean isArgon2Tested(Context context) {
    return getBooleanPreference(context, ARGON2_TESTED, false);
  }

  public static void setArgon2Tested(Context context, boolean tested) {
    setBooleanPreference(context, ARGON2_TESTED, tested);
  }

  public static void setBooleanPreference(Context context, String key, boolean value) {
    getSharedPreferences(context).edit().putBoolean(key, value).apply();
  }

  public static boolean getBooleanPreference(Context context, String key, boolean defaultValue) {
    return getSharedPreferences(context).getBoolean(key, defaultValue);
  }

  public static void setStringPreference(Context context, String key, String value) {
    getSharedPreferences(context).edit().putString(key, value).apply();
  }

  public static String getStringPreference(Context context, String key, String defaultValue) {
    return getSharedPreferences(context).getString(key, defaultValue);
  }

  public static int getIntegerPreference(Context context, String key, int defaultValue) {
    return getSharedPreferences(context).getInt(key, defaultValue);
  }

  private static void setIntegerPrefrence(Context context, String key, int value) {
    getSharedPreferences(context).edit().putInt(key, value).apply();
  }

  private static boolean setIntegerPrefrenceBlocking(Context context, String key, int value) {
    return getSharedPreferences(context).edit().putInt(key, value).commit();
  }

  public static long getLongPreference(Context context, String key, long defaultValue) {
    return getSharedPreferences(context).getLong(key, defaultValue);
  }

  private static void setLongPreference(Context context, String key, long value) {
    getSharedPreferences(context).edit().putLong(key, value).apply();
  }

  private static void removePreference(Context context, String key) {
    getSharedPreferences(context).edit().remove(key).apply();
  }

  private static Set<String> getStringSetPreference(Context context, String key, Set<String> defaultValues) {
    final SharedPreferences prefs = getSharedPreferences(context);
    if (prefs.contains(key)) {
      return prefs.getStringSet(key, Collections.<String>emptySet());
    } else {
      return defaultValues;
    }
  }

  private static void clearLocalCredentials(Context context) {

    ProfileKey newProfileKey = ProfileKeyUtil.createNew();
    Recipient  self          = Recipient.self();
    SignalDatabase.recipients().setProfileKey(self.getId(), newProfileKey);

    ApplicationDependencies.getGroupsV2Authorization().clear();
  }

  private static SharedPreferences getSharedPreferences(Context context) {
    if (preferences == null) {
      preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }
    return preferences;
  }

  private static void notifyUnregisteredReceived(Context context) {
    PendingIntent reRegistrationIntent = PendingIntent.getActivity(context,
                                                                   0,
                                                                   RegistrationNavigationActivity.newIntentForReRegistration(context),
                                                                   PendingIntent.FLAG_UPDATE_CURRENT | PendingIntentFlags.immutable());
    final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
        .setSmallIcon(R.drawable.ic_signal_logo_large)
        .setContentText(context.getString(R.string.LoggedOutNotification_you_have_been_logged_out))
        .setContentIntent(reRegistrationIntent)
        .setOnlyAlertOnce(true)
        .setAutoCancel(true);
    NotificationManagerCompat.from(context).notify(NotificationIds.UNREGISTERED_NOTIFICATION_ID, builder.build());
  }

  // NEVER rename these -- they're persisted by name
  public enum MediaKeyboardMode {
    EMOJI, STICKER, GIF
  }
}
