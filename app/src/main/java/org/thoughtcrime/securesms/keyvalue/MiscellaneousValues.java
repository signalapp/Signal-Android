package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

public final class MiscellaneousValues extends SignalStoreValues {

  private static final String LAST_PREKEY_REFRESH_TIME    = "last_prekey_refresh_time";
  private static final String MESSAGE_REQUEST_ENABLE_TIME = "message_request_enable_time";
  private static final String LAST_PROFILE_REFRESH_TIME   = "misc.last_profile_refresh_time";
  private static final String USERNAME_SHOW_REMINDER      = "username.show.reminder";

  MiscellaneousValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    putLong(MESSAGE_REQUEST_ENABLE_TIME, System.currentTimeMillis());
  }

  public long getLastPrekeyRefreshTime() {
    return getLong(LAST_PREKEY_REFRESH_TIME, 0);
  }

  public void setLastPrekeyRefreshTime(long time) {
    putLong(LAST_PREKEY_REFRESH_TIME, time);
  }

  public long getMessageRequestEnableTime() {
    return getLong(MESSAGE_REQUEST_ENABLE_TIME, System.currentTimeMillis());
  }

  public long getLastProfileRefreshTime() {
    return getLong(LAST_PROFILE_REFRESH_TIME, 0);
  }

  public void setLastProfileRefreshTime(long time) {
    putLong(LAST_PROFILE_REFRESH_TIME, time);
  }

  public void hideUsernameReminder() {
    putBoolean(USERNAME_SHOW_REMINDER, false);
  }

  public boolean shouldShowUsernameReminder() {
    return getBoolean(USERNAME_SHOW_REMINDER, true);
  }
}
