package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import java.util.List;

abstract class SignalStoreValues {

  private final KeyValueStore store;

  SignalStoreValues(@NonNull KeyValueStore store) {
    this.store = store;
  }

  @NonNull KeyValueStore getStore() {
    return store;
  }

  abstract void onFirstEverAppLaunch();

  abstract @NonNull List<String> getKeysToIncludeInBackup();

  String getString(String key, String defaultValue) {
    return store.getString(key, defaultValue);
  }

  int getInteger(String key, int defaultValue) {
    return store.getInteger(key, defaultValue);
  }

  long getLong(String key, long defaultValue) {
    return store.getLong(key, defaultValue);
  }

  boolean getBoolean(String key, boolean defaultValue) {
    return store.getBoolean(key, defaultValue);
  }

  float getFloat(String key, float defaultValue) {
    return store.getFloat(key, defaultValue);
  }

  byte[] getBlob(String key, byte[] defaultValue) {
    return store.getBlob(key, defaultValue);
  }

  void putBlob(@NonNull String key, byte[] value) {
    store.beginWrite().putBlob(key, value).apply();
  }

  void putBoolean(@NonNull String key, boolean value) {
    store.beginWrite().putBoolean(key, value).apply();
  }

  void putFloat(@NonNull String key, float value) {
    store.beginWrite().putFloat(key, value).apply();
  }

  void putInteger(@NonNull String key, int value) {
    store.beginWrite().putInteger(key, value).apply();
  }

  void putLong(@NonNull String key, long value) {
    store.beginWrite().putLong(key, value).apply();
  }

  void putString(@NonNull String key, String value) {
    store.beginWrite().putString(key, value).apply();
  }
}
