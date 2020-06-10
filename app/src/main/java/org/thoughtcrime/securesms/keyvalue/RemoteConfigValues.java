package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.logging.Log;

public final class RemoteConfigValues extends SignalStoreValues {

  private static final String TAG = Log.tag(RemoteConfigValues.class);

  private static final String CURRENT_CONFIG  = "remote_config";
  private static final String PENDING_CONFIG  = "pending_remote_config";
  private static final String LAST_FETCH_TIME = "remote_config_last_fetch_time";

  RemoteConfigValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
  }

  public String getCurrentConfig() {
    return getString(CURRENT_CONFIG, null);
  }

  public void setCurrentConfig(String value) {
    putString(CURRENT_CONFIG, value);
  }

  public String getPendingConfig() {
    return getString(PENDING_CONFIG, getCurrentConfig());
  }

  public void setPendingConfig(String value) {
    putString(PENDING_CONFIG, value);
  }

  public long getLastFetchTime() {
    return getLong(LAST_FETCH_TIME, 0);
  }

  public void setLastFetchTime(long time) {
    putLong(LAST_FETCH_TIME, time);
  }
}
