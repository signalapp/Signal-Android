package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ByteSerializer;
import org.signal.core.util.StringSerializer;
import org.thoughtcrime.securesms.database.model.databaseprotos.SignalStoreList;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class SignalStoreValues {

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

  <T> T getObject(@NonNull String key, @Nullable T defaultValue, @NonNull ByteSerializer<T> serializer) {
    byte[] blob = store.getBlob(key, null);
    if (blob == null) {
      return defaultValue;
    } else {
      return serializer.deserialize(blob);
    }
  }

  <T> List<T> getList(@NonNull String key, @NonNull StringSerializer<T> serializer) {
    byte[] blob = getBlob(key, null);
    if (blob == null) {
      return Collections.emptyList();
    }

    try {
      SignalStoreList signalStoreList = SignalStoreList.ADAPTER.decode(blob);

      return signalStoreList.contents
                            .stream()
                            .map(serializer::deserialize)
                            .collect(Collectors.toList());

    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
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

  <T> void putObject(@NonNull String key, T value, @NonNull ByteSerializer<T> serializer) {
    putBlob(key, serializer.serialize(value));
  }

  <T> void putList(@NonNull String key, @NonNull List<T> values, @NonNull StringSerializer<T> serializer) {
    putBlob(key, new SignalStoreList.Builder()
                                    .contents(values.stream()
                                                    .map(serializer::serialize)
                                                    .collect(Collectors.toList()))
                                    .build()
                                    .encode());
  }

  void remove(@NonNull String key) {
    store.beginWrite().remove(key).apply();
  }
}
