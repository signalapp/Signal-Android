package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public final class MiscellaneousValues extends SignalStoreValues {

  private static final String LAST_PREKEY_REFRESH_TIME        = "last_prekey_refresh_time";
  private static final String MESSAGE_REQUEST_ENABLE_TIME     = "message_request_enable_time";
  private static final String LAST_PROFILE_REFRESH_TIME       = "misc.last_profile_refresh_time";
  private static final String LAST_GV1_ROUTINE_MIGRATION_TIME = "misc.last_gv1_routine_migration_time";
  private static final String USERNAME_SHOW_REMINDER          = "username.show.reminder";
  private static final String CLIENT_DEPRECATED               = "misc.client_deprecated";
  private static final String OLD_DEVICE_TRANSFER_LOCKED      = "misc.old_device.transfer.locked";
  private static final String HAS_EVER_HAD_AN_AVATAR          = "misc.has.ever.had.an.avatar";
  private static final String CHANGE_NUMBER_LOCK              = "misc.change_number.lock";
  private static final String CENSORSHIP_LAST_CHECK_TIME      = "misc.censorship.last_check_time";
  private static final String CENSORSHIP_SERVICE_REACHABLE    = "misc.censorship.service_reachable";

  MiscellaneousValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    putLong(MESSAGE_REQUEST_ENABLE_TIME, 0);
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Collections.emptyList();
  }

  public long getLastPrekeyRefreshTime() {
    return getLong(LAST_PREKEY_REFRESH_TIME, 0);
  }

  public void setLastPrekeyRefreshTime(long time) {
    putLong(LAST_PREKEY_REFRESH_TIME, time);
  }

  public long getMessageRequestEnableTime() {
    return getLong(MESSAGE_REQUEST_ENABLE_TIME, 0);
  }

  public long getLastProfileRefreshTime() {
    return getLong(LAST_PROFILE_REFRESH_TIME, 0);
  }

  public void setLastProfileRefreshTime(long time) {
    putLong(LAST_PROFILE_REFRESH_TIME, time);
  }

  public long getLastGv1RoutineMigrationTime() {
    return getLong(LAST_GV1_ROUTINE_MIGRATION_TIME, 0);
  }

  public void setLastGv1RoutineMigrationTime(long time) {
    putLong(LAST_GV1_ROUTINE_MIGRATION_TIME, time);
  }

  public void hideUsernameReminder() {
    putBoolean(USERNAME_SHOW_REMINDER, false);
  }

  public boolean shouldShowUsernameReminder() {
    return getBoolean(USERNAME_SHOW_REMINDER, true);
  }

  public boolean isClientDeprecated() {
    return getBoolean(CLIENT_DEPRECATED, false);
  }

  public void markClientDeprecated() {
    putBoolean(CLIENT_DEPRECATED, true);
  }

  public void clearClientDeprecated() {
    putBoolean(CLIENT_DEPRECATED, false);
  }

  public boolean isOldDeviceTransferLocked() {
    return getBoolean(OLD_DEVICE_TRANSFER_LOCKED, false);
  }

  public void markOldDeviceTransferLocked() {
    putBoolean(OLD_DEVICE_TRANSFER_LOCKED, true);
  }

  public void clearOldDeviceTransferLocked() {
    putBoolean(OLD_DEVICE_TRANSFER_LOCKED, false);
  }

  public boolean hasEverHadAnAvatar() {
    return getBoolean(HAS_EVER_HAD_AN_AVATAR, false);
  }

  public void markHasEverHadAnAvatar() {
    putBoolean(HAS_EVER_HAD_AN_AVATAR, true);
  }

  public boolean isChangeNumberLocked() {
    return getBoolean(CHANGE_NUMBER_LOCK, false);
  }

  public void lockChangeNumber() {
    putBoolean(CHANGE_NUMBER_LOCK, true);
  }

  public void unlockChangeNumber() {
    putBoolean(CHANGE_NUMBER_LOCK, false);
  }

  public long getLastCensorshipServiceReachabilityCheckTime() {
    return getLong(CENSORSHIP_LAST_CHECK_TIME, 0);
  }

  public void setLastCensorshipServiceReachabilityCheckTime(long value) {
    putLong(CENSORSHIP_LAST_CHECK_TIME, value);
  }

  public boolean isServiceReachableWithoutCircumvention() {
    return getBoolean(CENSORSHIP_SERVICE_REACHABLE, false);
  }

  public void setServiceReachableWithoutCircumvention(boolean value) {
    putBoolean(CENSORSHIP_SERVICE_REACHABLE, value);
  }
}
