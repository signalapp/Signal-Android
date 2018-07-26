package org.thoughtcrime.securesms.logging;

import android.support.annotation.NonNull;

public class UncaughtExceptionLogger implements Thread.UncaughtExceptionHandler {

  private static final String TAG = UncaughtExceptionLogger.class.getSimpleName();

  private final Thread.UncaughtExceptionHandler originalHandler;
  private final PersistentLogger                persistentLogger;

  public UncaughtExceptionLogger(@NonNull Thread.UncaughtExceptionHandler originalHandler, @NonNull PersistentLogger persistentLogger) {
    this.originalHandler  = originalHandler;
    this.persistentLogger = persistentLogger;
  }

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    Log.e(TAG, "", e);
    originalHandler.uncaughtException(t, e);
  }
}
