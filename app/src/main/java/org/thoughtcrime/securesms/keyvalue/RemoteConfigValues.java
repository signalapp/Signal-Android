package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;

import java.util.Collections;
import java.util.List;

public final class RemoteConfigValues extends SignalStoreValues {

  private static final String TAG = Log.tag(RemoteConfigValues.class);

  private static final String CURRENT_CONFIG  = "remote_config";
  private static final String PENDING_CONFIG  = "pending_remote_config";
  private static final String LAST_FETCH_TIME = "remote_config_last_fetch_time";
  private static final String ETAG            = "etag";

  RemoteConfigValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Collections.emptyList();
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

  public String getETag() {
    return getString(ETAG, "");
  }

  public void setETag(String etag) {
    putString(ETAG, etag);
  }
}
