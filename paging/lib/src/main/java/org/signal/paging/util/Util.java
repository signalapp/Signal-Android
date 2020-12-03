package org.signal.paging.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.CountDownLatch;

public final class Util {

  private static volatile Handler handler;

  private Util() {}

  public static void runOnMain(final @NonNull Runnable runnable) {
    if (isMainThread()) runnable.run();
    else                getHandler().post(runnable);
  }

  public static void runOnMainSync(final @NonNull Runnable runnable) {
    if (isMainThread()) {
      runnable.run();
    } else {
      final CountDownLatch sync = new CountDownLatch(1);
      runOnMain(() -> {
        try {
          runnable.run();
        } finally {
          sync.countDown();
        }
      });
      try {
        sync.await();
      } catch (InterruptedException ie) {
        throw new AssertionError(ie);
      }
    }
  }

  public static boolean isMainThread() {
    return Looper.myLooper() == Looper.getMainLooper();
  }

  private static Handler getHandler() {
    if (handler == null) {
      synchronized (Util.class) {
        if (handler == null) {
          handler = new Handler(Looper.getMainLooper());
        }
      }
    }
    return handler;
  }
}
