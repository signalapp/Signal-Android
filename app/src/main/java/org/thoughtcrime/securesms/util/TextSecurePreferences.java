package org.thoughtcrime.securesms.util;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.signal.core.util.PendingIntentFlags;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.v2.BackupRepository;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.RegistrationLockReminders;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.NotificationIds;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.registration.ui.RegistrationActivity;

public class TextSecurePreferences {

  private static final String TAG = Log.tag(TextSecurePreferences.class);

  public  static final String ENABLE_MANUAL_MMS_PREF           = "pref_enable_manual_mms";

  private static final String PROMPTED_PUSH_REGISTRATION_PREF  = "pref_prompted_push_registration";
  private static final String PROMPTED_OPTIMIZE_DOZE_PREF      = "pref_prompted_optimize_doze";
  private static final String DIRECTORY_FRESH_TIME_PREF        = "pref_directory_refresh_time";
  private static final String UPDATE_APK_REFRESH_TIME_PREF     = "pref_update_apk_refresh_time";
  private static final String SIGNED_PREKEY_ROTATION_TIME_PREF = "pref_signed_pre_key_rotation_time";

  private static final String RATING_LATER_PREF                = "pref_rating_later";
  private static final String RATING_ENABLED_PREF              = "pref_rating_enabled";

  private static final String MULTI_DEVICE_PROVISIONED_PREF    = "pref_multi_device";
  public  static final String UNAUTHORIZED_RECEIVED            = "pref_unauthorized_received";
  private static final String SUCCESSFUL_DIRECTORY_PREF        = "pref_successful_directory";

  public  static final String BACKUP                      = "pref_backup";
  private static final String BACKUP_PASSPHRASE           = "pref_backup_passphrase";
  private static final String ENCRYPTED_BACKUP_PASSPHRASE = "pref_encrypted_backup_passphrase";

  @Deprecated
  public static final  String REGISTRATION_LOCK_PREF_V1                = "pref_registration_lock";
  @Deprecated
  private static final String REGISTRATION_LOCK_PIN_PREF_V1            = "pref_registration_lock_pin";

  private static final String REGISTRATION_LOCK_LAST_REMINDER_TIME_POST_KBS = "pref_registration_lock_last_reminder_time_post_kbs";
  private static final String REGISTRATION_LOCK_NEXT_REMINDER_INTERVAL      = "pref_registration_lock_next_reminder_interval";

  public  static final String SERVICE_OUTAGE         = "pref_service_outage";
  private static final String LAST_OUTAGE_CHECK_TIME = "pref_last_outage_check_time";

  private static final String LAST_FULL_CONTACT_SYNC_TIME = "pref_last_full_contact_sync_time";
  private static final String NEEDS_FULL_CONTACT_SYNC     = "pref_needs_full_contact_sync";

  private static final String LOG_ENCRYPTED_SECRET   = "pref_log_encrypted_secret";
  private static final String LOG_UNENCRYPTED_SECRET = "pref_log_unencrypted_secret";

  private static final String NOTIFICATION_CHANNEL_VERSION          = "pref_notification_channel_version";
  private static final String NOTIFICATION_MESSAGES_CHANNEL_VERSION = "pref_notification_messages_channel_version";

  private static final String NEEDS_MESSAGE_PULL = "pref_needs_message_pull";

  private static final String UNIDENTIFIED_ACCESS_CERTIFICATE_ROTATION_TIME_PREF = "pref_unidentified_access_certificate_rotation_time";
  private static final String UNIDENTIFIED_DELIVERY_ENABLED                      = "pref_unidentified_delivery_enabled";

  public static final String LINK_PREVIEWS = "pref_link_previews";

  private static final String SEEN_STICKER_INTRO_TOOLTIP = "pref_seen_sticker_intro_tooltip";

  public  static final String RECENT_STORAGE_KEY  = "pref_recent_emoji2";

  private static final String JOB_MANAGER_VERSION = "pref_job_manager_version";

  private static final String FIRST_INSTALL_VERSION = "pref_first_install_version";

  private static final String HAS_SEEN_SWIPE_TO_REPLY = "pref_has_seen_swipe_to_reply";

  private static final String HAS_SEEN_VIDEO_RECORDING_TOOLTIP = "camerax.fragment.has.dismissed.video.recording.tooltip";

  private static volatile SharedPreferences preferences = null;

  public static void onPostBackupRestore(@NonNull Context context) {
    if (NotificationChannels.supported()) {
      NotificationChannels.getInstance().updateMessageVibrate(SignalStore.settings().isMessageVibrateEnabled());
    }
  }

  public static void registerListener(@NonNull Context context, SharedPreferences.OnSharedPreferenceChangeListener listener) {
    getSharedPreferences(context).registerOnSharedPreferenceChangeListener(listener);
  }

  public static void unregisterListener(@NonNull Context context, SharedPreferences.OnSharedPreferenceChangeListener listener) {
    getSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(listener);
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
        clearLocalCredentials(context);
        BackupRepository.haltBackupWritesForDeregistration();
      }
    }
  }

  public static boolean isUnauthorizedReceived(Context context) {
    return getBooleanPreference(context, UNAUTHORIZED_RECEIVED, false);
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

  public static long getUnidentifiedAccessCertificateRotationTime(Context context) {
    return getLongPreference(context, UNIDENTIFIED_ACCESS_CERTIFICATE_ROTATION_TIME_PREF, 0L);
  }

  public static void setUnidentifiedAccessCertificateRotationTime(Context context, long value) {
    setLongPreference(context, UNIDENTIFIED_ACCESS_CERTIFICATE_ROTATION_TIME_PREF, value);
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

  public static boolean isLegacyUseLocalApnsEnabled(Context context) {
    return getBooleanPreference(context, ENABLE_MANUAL_MMS_PREF, false);
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

  public static void setJobManagerVersion(Context context, int version) {
    setIntegerPrefrence(context, JOB_MANAGER_VERSION, version);
  }

  public static int getJobManagerVersion(Context contex) {
    return getIntegerPreference(contex, JOB_MANAGER_VERSION, 1);
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

  public static long getLongPreference(Context context, String key, long defaultValue) {
    return getSharedPreferences(context).getLong(key, defaultValue);
  }

  private static void setLongPreference(Context context, String key, long value) {
    getSharedPreferences(context).edit().putLong(key, value).apply();
  }

  private static void removePreference(Context context, String key) {
    getSharedPreferences(context).edit().remove(key).apply();
  }

  private static void clearLocalCredentials(Context context) {
    ProfileKey newProfileKey = ProfileKeyUtil.createNew();
    Recipient  self          = Recipient.self();
    SignalDatabase.recipients().setProfileKey(self.getId(), newProfileKey);

    AppDependencies.getGroupsV2Authorization().clear();
  }

  public static SharedPreferences getSharedPreferences(Context context) {
    if (preferences == null) {
      preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }
    return preferences;
  }

  private static void notifyUnregisteredReceived(Context context) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "notifyUnregisteredReceived: Notification permission is not granted.");
      return;
    }

    Intent registrationIntent = SignalStore.account().isLinkedDevice() ? RegistrationActivity.newIntentForReLinkDevice(context)
                                                                       : RegistrationActivity.newIntentForReRegistration(context);

    PendingIntent reRegistrationIntent = PendingIntent.getActivity(context,
                                                                   0,
                                                                   registrationIntent,
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
}
