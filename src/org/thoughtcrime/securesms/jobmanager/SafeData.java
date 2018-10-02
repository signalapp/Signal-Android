package org.thoughtcrime.securesms.jobmanager;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import androidx.work.Data;

/**
 * A wrapper around {@link Data} that does its best to throw an exception whenever a key isn't
 * present in the {@link Data} object.
 */
public class SafeData {

  private final Data data;

  public SafeData(@NonNull Data data) {
    this.data = data;
  }

  public int getInt(@NonNull String key) {
    assertKeyPresence(key);
    return data.getInt(key, -1);
  }

  public long getLong(@NonNull String key) {
    assertKeyPresence(key);
    return data.getLong(key, -1);
  }

  public String getString(@NonNull String key) {
    assertKeyPresence(key);
    return data.getString(key);
  }

  public String[] getStringArray(@NonNull String key) {
    assertKeyPresence(key);
    return data.getStringArray(key);
  }

  public long[] getLongArray(@NonNull String key) {
    assertKeyPresence(key);
    return data.getLongArray(key);
  }

  public boolean getBoolean(@NonNull String key) {
    assertKeyPresence(key);
    return data.getBoolean(key, false);
  }

  private void assertKeyPresence(@NonNull String key) {
    if (!data.getKeyValueMap().containsKey(key)) {
      throw new IllegalStateException("Missing key: " + key);
    }
  }
}
