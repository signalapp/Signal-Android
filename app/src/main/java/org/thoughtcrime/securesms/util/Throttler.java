package org.thoughtcrime.securesms.util;

import android.os.Handler;
import android.os.Looper;

/**
 * A class that will throttle the number of runnables executed to be at most once every specified
 * interval.
 *
 * Useful for performing actions in response to rapid user input where you want to take action on
 * the initial input but prevent follow-up spam.
 *
 * This is different from {@link Debouncer} in that it will run the first runnable immediately
 * instead of waiting for input to die down.
 *
 * See http://rxmarbles.com/#throttle
 */
public class Throttler {

  private static final int WHAT = 8675309;

  private final Handler handler;
  private final long    threshold;

  /**
   * @param threshold Only one runnable will be executed via {@link #publish(Runnable)} every
   *                  {@code threshold} milliseconds.
   */
  public Throttler(long threshold) {
    this.handler   = new Handler(Looper.getMainLooper());
    this.threshold = threshold;
  }

  public void publish(Runnable runnable) {
    if (handler.hasMessages(WHAT)) {
      return;
    }

    runnable.run();
    handler.sendMessageDelayed(handler.obtainMessage(WHAT), threshold);
  }

  public void clear() {
    handler.removeCallbacksAndMessages(null);
  }
}
