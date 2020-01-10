package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.SignalUncaughtExceptionHandler;

/**
 * Simple, encrypted key-value store.
 */
public final class SignalStore {

  private SignalStore() {}

  /**
   * Ensures any pending writes are finished. Only intended to be called by
   * {@link SignalUncaughtExceptionHandler}.
   */
  public static void blockUntilAllWritesFinished() {
    getStore().blockUntilAllWritesFinished();
  }

  private static @NonNull KeyValueStore getStore() {
    return ApplicationDependencies.getKeyValueStore();
  }

  private static void putBlob(@NonNull String key, byte[] value) {
    getStore().beginWrite().putBlob(key, value).apply();
  }

  private static void putBoolean(@NonNull String key, boolean value) {
    getStore().beginWrite().putBoolean(key, value).apply();
  }

  private static void putFloat(@NonNull String key, float value) {
    getStore().beginWrite().putFloat(key, value).apply();
  }

  private static void putInteger(@NonNull String key, int value) {
    getStore().beginWrite().putInteger(key, value).apply();
  }

  private static void putLong(@NonNull String key, long value) {
    getStore().beginWrite().putLong(key, value).apply();
  }

  private static void putString(@NonNull String key, String value) {
    getStore().beginWrite().putString(key, value).apply();
  }
}
