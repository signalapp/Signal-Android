package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

/**
 * An implementation of the {@link PreferenceDataStore} interface to let us link preference screens
 * to the {@link SignalStore}.
 */
public class SignalPreferenceDataStore extends PreferenceDataStore {

  private final KeyValueStore store;

  SignalPreferenceDataStore(@NonNull KeyValueStore store) {
    this.store = store;
  }

  @Override
  public void putString(String key, @Nullable String value) {
    store.beginWrite().putString(key, value).apply();
  }

  @Override
  public void putInt(String key, int value) {
    store.beginWrite().putInteger(key, value).apply();
  }

  @Override
  public void putLong(String key, long value) {
    store.beginWrite().putLong(key, value).apply();
  }

  @Override
  public void putFloat(String key, float value) {
    store.beginWrite().putFloat(key, value).apply();
  }

  @Override
  public void putBoolean(String key, boolean value) {
    store.beginWrite().putBoolean(key, value).apply();
  }

  @Override
  public @Nullable String getString(String key, @Nullable String defValue) {
    return store.getString(key, defValue);
  }

  @Override
  public int getInt(String key, int defValue) {
    return store.getInteger(key, defValue);
  }

  @Override
  public long getLong(String key, long defValue) {
    return store.getLong(key, defValue);
  }

  @Override
  public float getFloat(String key, float defValue) {
    return store.getFloat(key, defValue);
  }

  @Override
  public boolean getBoolean(String key, boolean defValue) {
    return store.getBoolean(key, defValue);
  }
}
