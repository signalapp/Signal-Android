package org.thoughtcrime.securesms.keyvalue;

import org.thoughtcrime.securesms.logging.Log;

public final class RemoteConfigValues {

  private static final String TAG = Log.tag(RemoteConfigValues.class);

  private static final String CURRENT_CONFIG  = "remote_config";
  private static final String PENDING_CONFIG  = "pending_remote_config";
  private static final String LAST_FETCH_TIME = "remote_config_last_fetch_time";

  private final KeyValueStore store;

  RemoteConfigValues(KeyValueStore store) {
    this.store = store;
  }

  public String getCurrentConfig() {
    return store.getString(CURRENT_CONFIG, null);
  }

  public void setCurrentConfig(String value) {
    store.beginWrite().putString(CURRENT_CONFIG, value).apply();
  }

  public String getPendingConfig() {
    return store.getString(PENDING_CONFIG, getCurrentConfig());
  }

  public void setPendingConfig(String value) {
    store.beginWrite().putString(PENDING_CONFIG, value).apply();
  }

  public long getLastFetchTime() {
    return store.getLong(LAST_FETCH_TIME, 0);
  }

  public void setLastFetchTime(long time) {
    store.beginWrite().putLong(LAST_FETCH_TIME, time).apply();
  }
}
