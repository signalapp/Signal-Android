package org.thoughtcrime.securesms.jobmanager;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import androidx.work.Data;

/**
 * A wrapper around {@link Data} that does its best to throw an exception whenever a key isn't
 * present in the {@link Data} object.
 */
public class SafeData {

  private final static int  INVALID_INT  = Integer.MIN_VALUE;
  private final static long INVALID_LONG = Long.MIN_VALUE;

  private final Data data;

  public SafeData(@NonNull Data data) {
    this.data = data;
  }

  public int getInt(@NonNull String key) {
    int value = data.getInt(key, INVALID_INT);

    if (value == INVALID_INT) {
      throw new IllegalStateException("Missing key: " + key);
    }

    return value;
  }

  public long getLong(@NonNull String key) {
    long value = data.getLong(key, INVALID_LONG);

    if (value == INVALID_LONG) {
      throw new IllegalStateException("Missing key: " + key);
    }

    return value;
  }

  public @NonNull String getString(@NonNull String key) {
    String value = data.getString(key);

    if (value == null) {
      throw new IllegalStateException("Missing key: " + key);
    }

    return value;
  }

  public @Nullable String getNullableString(@NonNull String key) {
    return data.getString(key);
  }

  public @NonNull String[] getStringArray(@NonNull String key) {
    String[] value = data.getStringArray(key);

    if (value == null) {
      throw new IllegalStateException("Missing key: " + key);
    }

    return value;
  }

  public @NonNull long[] getLongArray(@NonNull String key) {
    long[] value = data.getLongArray(key);

    if (value == null) {
      throw new IllegalStateException("Missing key: " + key);
    }

    return value;
  }

  public boolean getBoolean(@NonNull String key, boolean defaultValue) {
    return data.getBoolean(key, defaultValue);
  }
}
