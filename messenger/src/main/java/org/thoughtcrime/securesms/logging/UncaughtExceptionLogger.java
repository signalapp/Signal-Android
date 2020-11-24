package org.thoughtcrime.securesms.logging;

import androidx.annotation.NonNull;

public class UncaughtExceptionLogger implements Thread.UncaughtExceptionHandler {

  private static final String TAG = UncaughtExceptionLogger.class.getSimpleName();

  private final Thread.UncaughtExceptionHandler originalHandler;

  public UncaughtExceptionLogger(@NonNull Thread.UncaughtExceptionHandler originalHandler) {
    this.originalHandler = originalHandler;
  }

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    Log.e(TAG, "", e);
    Log.blockUntilAllWritesFinished();
    originalHandler.uncaughtException(t, e);
  }
}
