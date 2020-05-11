package org.thoughtcrime.securesms.gcm;

import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.PowerManagerCompat;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.WakeLockUtil;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Facilitates the retrieval of messages via provided {@link Strategy}'s.
 */
public class MessageRetriever {

  private static final String TAG = Log.tag(MessageRetriever.class);

  private static final String WAKE_LOCK_TAG  = "MessageRetriever";

  private static final Semaphore ACTIVE_LOCK = new Semaphore(2);

  /**
   * @return False if the retrieval failed and should be rescheduled, otherwise true.
   */
  @WorkerThread
  public boolean retrieveMessages(@NonNull Context context, Strategy... strategies) {
    if (shouldIgnoreFetch(context)) {
      Log.i(TAG, "Skipping retrieval -- app is in the foreground.");
      return true;
    }

    if (!ACTIVE_LOCK.tryAcquire()) {
      Log.i(TAG, "Skipping retrieval -- there's already one enqueued.");
      return true;
    }

    synchronized (this) {
      PowerManager.WakeLock wakeLock = null;

      try {
        wakeLock = WakeLockUtil.acquire(context, PowerManager.PARTIAL_WAKE_LOCK, TimeUnit.SECONDS.toMillis(60), WAKE_LOCK_TAG);

        TextSecurePreferences.setNeedsMessagePull(context, true);

        long         startTime    = System.currentTimeMillis();
        PowerManager powerManager = ServiceUtil.getPowerManager(context);
        boolean      doze         = PowerManagerCompat.isDeviceIdleMode(powerManager);
        boolean      network      = new NetworkConstraint.Factory(ApplicationContext.getInstance(context)).create().isMet();

        if (doze || !network) {
          Log.w(TAG, "We may be operating in a constrained environment. Doze: " + doze + " Network: " + network);
        }

        boolean success = false;

        for (Strategy strategy : strategies) {
          if (shouldIgnoreFetch(context)) {
            Log.i(TAG, "Stopping further strategy attempts -- app is in the foreground." + logSuffix(startTime));
            success = true;
            break;
          }

          Log.i(TAG, "Attempting strategy: " + strategy.toString() + logSuffix(startTime));

          if (strategy.run()) {
            Log.i(TAG, "Strategy succeeded: " + strategy.toString() + logSuffix(startTime));
            success = true;
            break;
          } else {
            Log.w(TAG, "Strategy failed: " + strategy.toString() + logSuffix(startTime));
          }
        }

        if (success) {
          TextSecurePreferences.setNeedsMessagePull(context, false);
        } else {
          Log.w(TAG, "All strategies failed!" + logSuffix(startTime));
        }

        return success;
      } finally {
        WakeLockUtil.release(wakeLock, WAKE_LOCK_TAG);
        ACTIVE_LOCK.release();
      }
    }
  }

  /**
   * @return True if there is no need to execute a message fetch, because the websocket will take
   *         care of it.
   */
  public static boolean shouldIgnoreFetch(@NonNull Context context) {
    return ApplicationContext.getInstance(context).isAppVisible() &&
           !ApplicationDependencies.getSignalServiceNetworkAccess().isCensored(context);
  }

  private static String logSuffix(long startTime) {
    return " (" + (System.currentTimeMillis() - startTime) + " ms elapsed)";
  }

  /**
   * A method of retrieving messages.
   */
  public interface Strategy {
    /**
     * @return False if the message retrieval failed and should be retried, otherwise true.
     */
    @WorkerThread
    boolean run();
  }
}
