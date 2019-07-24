package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.logging.Log;

public class WakeLockUtil {

  private static final String TAG = WakeLockUtil.class.getSimpleName();

  /**
   * Run a runnable with a wake lock. Ensures that the lock is safely acquired and released.
   *
   * @param tag will be prefixed with "signal:" if it does not already start with it.
   */
  public static void runWithLock(@NonNull Context context, int lockType, long timeout, @NonNull String tag, @NonNull Runnable task) {
    WakeLock wakeLock = null;
    try {
      wakeLock = acquire(context, lockType, timeout, tag);
      task.run();
    } finally {
      if (wakeLock != null) {
        release(wakeLock, tag);
      }
    }
  }

  /**
   * @param tag will be prefixed with "signal:" if it does not already start with it.
   */
  public static WakeLock acquire(@NonNull Context context, int lockType, long timeout, @NonNull String tag) {
    tag = prefixTag(tag);
    try {
      PowerManager powerManager = ServiceUtil.getPowerManager(context);
      WakeLock     wakeLock     = powerManager.newWakeLock(lockType, tag);

      wakeLock.acquire(timeout);
      Log.d(TAG, "Acquired wakelock with tag: " + tag);

      return wakeLock;
    } catch (Exception e) {
      Log.w(TAG, "Failed to acquire wakelock with tag: " + tag, e);
      return null;
    }
  }

  /**
   * @param tag will be prefixed with "signal:" if it does not already start with it.
   */
  public static void release(@NonNull WakeLock wakeLock, @NonNull String tag) {
    tag = prefixTag(tag);
    try {
      if (wakeLock.isHeld()) {
        wakeLock.release();
        Log.d(TAG, "Released wakelock with tag: " + tag);
      } else {
        Log.d(TAG, "Wakelock wasn't held at time of release: " + tag);
      }
    } catch (Exception e) {
      Log.w(TAG, "Failed to release wakelock with tag: " + tag, e);
    }
  }

  private static String prefixTag(@NonNull String tag) {
    return tag.startsWith("signal:") ? tag : "signal:" + tag;
  }
}
