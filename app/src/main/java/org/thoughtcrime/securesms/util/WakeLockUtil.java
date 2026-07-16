package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.core.util.ServiceUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class WakeLockUtil {

  private static final String TAG = Log.tag(WakeLockUtil.class);

  private static final Map<WakeLock, Acquisition> activeAcquisitions = new ConcurrentHashMap<>();
  private static final AtomicLong                 cumulativeHeldMs   = new AtomicLong(0);

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

      activeAcquisitions.put(wakeLock, new Acquisition(SystemClock.elapsedRealtime(), timeout));

      return wakeLock;
    } catch (Exception e) {
      Log.w(TAG, "Failed to acquire wakelock with tag: " + tag, e);
      return null;
    }
  }

  /**
   * @param tag will be prefixed with "signal:" if it does not already start with it.
   */
  public static void release(@Nullable WakeLock wakeLock, @NonNull String tag) {
    tag = prefixTag(tag);

    if (wakeLock != null) {
      recordHeldTime(wakeLock);
    }

    try {
      if (wakeLock == null) {
        Log.d(TAG, "Wakelock was null. Skipping. Tag: " + tag);
      } else if (wakeLock.isHeld()) {
        wakeLock.release();
      } else {
        Log.d(TAG, "Wakelock wasn't held at time of release: " + tag);
      }
    } catch (Exception e) {
      Log.w(TAG, "Failed to release wakelock with tag: " + tag, e);
    }
  }

  /**
   * The cumulative time, in milliseconds, that wakelocks acquired through this class have been held
   * since process start. Includes the in-progress duration of any currently-held locks.
   */
  public static long getCumulativeHeldMs() {
    long total = cumulativeHeldMs.get();
    long now   = SystemClock.elapsedRealtime();

    for (Acquisition acquisition : activeAcquisitions.values()) {
      total += acquisition.heldMs(now);
    }

    return total;
  }

  /**
   * The number of wakelocks acquired through this class that are currently being tracked as held.
   */
  public static int getActiveLockCount() {
    return activeAcquisitions.size();
  }

  private static void recordHeldTime(@NonNull WakeLock wakeLock) {
    Acquisition acquisition = activeAcquisitions.remove(wakeLock);
    if (acquisition != null) {
      cumulativeHeldMs.addAndGet(acquisition.heldMs(SystemClock.elapsedRealtime()));
    }
  }

  private static String prefixTag(@NonNull String tag) {
    return tag.startsWith("signal:") ? tag : "signal:" + tag;
  }

  private static final class Acquisition {
    private final long acquiredAtMs;
    private final long timeoutMs;

    Acquisition(long acquiredAtMs, long timeoutMs) {
      this.acquiredAtMs = acquiredAtMs;
      this.timeoutMs    = timeoutMs;
    }

    long heldMs(long now) {
      long elapsed = Math.max(0, now - acquiredAtMs);
      return timeoutMs > 0 ? Math.min(elapsed, timeoutMs) : elapsed;
    }
  }
}
