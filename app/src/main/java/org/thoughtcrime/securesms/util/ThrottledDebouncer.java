package org.thoughtcrime.securesms.util;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

/**
 * Mixes the behavior of {@link Throttler} and {@link Debouncer}.
 *
 * Like a throttler, it will limit the number of runnables to be executed to be at most once every
 * specified interval, while allowing the first runnable to be run immediately.
 *
 * However, like a debouncer, instead of completely discarding runnables that are published in the
 * throttling period, the most recent one will be saved and run at the end of the throttling period.
 *
 * Useful for publishing a set of identical or near-identical tasks that you want to be responsive
 * and guaranteed, but limited in execution frequency.
 */
public class ThrottledDebouncer {

  private static final int WHAT = 24601;

  private final OverflowHandler handler;
  private final long            threshold;

  /**
   * @param threshold Only one runnable will be executed via {@link #publish(Runnable)} every
   *                  {@code threshold} milliseconds.
   */
  @MainThread
  public ThrottledDebouncer(long threshold) {
    this.handler   = new OverflowHandler();
    this.threshold = threshold;
  }

  @MainThread
  public void publish(Runnable runnable) {
    if (handler.hasMessages(WHAT)) {
      handler.setRunnable(runnable);
    } else {
      runnable.run();
      handler.sendMessageDelayed(handler.obtainMessage(WHAT), threshold);
    }
  }

  @MainThread
  public void clear() {
    handler.removeCallbacksAndMessages(null);
  }

  private static class OverflowHandler extends Handler {

    public OverflowHandler() {
      super(Looper.getMainLooper());
    }

    private Runnable runnable;

    @Override
    public void handleMessage(Message msg) {
      if (msg.what == WHAT && runnable != null) {
        runnable.run();
        runnable = null;
      }
    }

    public void setRunnable(@NonNull Runnable runnable) {
      this.runnable = runnable;
    }
  }
}
