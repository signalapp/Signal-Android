package org.thoughtcrime.securesms.util;

import android.os.Handler;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.core.os.HandlerCompat;

import org.signal.core.util.logging.Log;

/**
 * Imagine a bucket. Now imagine your tasks as little droplets. As your tasks are thrown into the
 * bucket, the tasks are executed, and the bucket fills up. If the bucket is full, the tasks
 * overflow and are discarded.
 *
 * However, the bucket has a leak! So it empties slowly over time, allowing you to put more tasks in
 * if you're patient.
 *
 * This class lets you define a bucket with a given capacity and drip interval. Imagine you had a
 * capacity of 10 and a drip interval of 1000ms. That means that you could execute 10 tasks in
 * rapid succession, but afterwards you'd only be able to execute at most 1 task per second. If you
 * waited 10 seconds, the bucket would be fully drained, and you'd be able to execute 10 tasks in
 * rapid succession again.
 *
 * This class also does something a little extra -- it keeps track of the most-recently-overflowed
 * task, and will run it the next time it 'drips' instead of leaking. This lets you have a sort of
 * "throw tasks at the bucket and forget about it" attitude, because you know the task will
 * eventually run.
 *
 * Of course, that's only if all of your tasks are equal! It's highly recommended, as with any sort
 * of limiting construct, to only submit a series of equivalent or roughly-equivalent tasks.
 *
 * Using the assumption that all tasks are equal, this class will also remove any pending tasks that
 * are waiting to run when a new one is enqueued. No point in causing a pile-up.
 */
public final class LeakyBucketLimiter {

  private static final String TAG = Log.tag(LeakyBucketLimiter.class);

  private final int     bucketCapacity;
  private final long    dripInterval;
  private final Handler handler;

  private int      bucketLevel;
  private Runnable lastOverflowedRunnable;

  private final Object RUNNABLE_TOKEN = new Object();

  public LeakyBucketLimiter(int bucketCapacity, long dripInterval, @NonNull Handler handler) {
    this.bucketCapacity = bucketCapacity;
    this.dripInterval   = dripInterval;
    this.handler        = handler;
  }

  @AnyThread
  public void run(@NonNull Runnable runnable) {
    boolean shouldRun    = false;
    boolean scheduleDrip = false;

    synchronized (this) {
      if (bucketLevel < bucketCapacity) {
        bucketLevel++;

        shouldRun    = true;
        scheduleDrip = bucketLevel == 1;
      } else {
        lastOverflowedRunnable = runnable;
      }
    }

    if (shouldRun) {
      handler.removeCallbacksAndMessages(RUNNABLE_TOKEN);
      HandlerCompat.postDelayed(handler, runnable, RUNNABLE_TOKEN, 0);
    } else {
      Log.d(TAG, "Overflowed!");
    }

    if (scheduleDrip) {
      handler.postDelayed(this::drip, dripInterval);
    }
  }

  private void drip() {
    Runnable runnable  = null;
    boolean  needsDrip = false;

    synchronized (this) {
      if (lastOverflowedRunnable == null) {
        bucketLevel = Math.max(bucketLevel - 1, 0);
      } else {
        Log.d(TAG, "Running most-recently-overflowed task.");
        runnable = lastOverflowedRunnable;
        lastOverflowedRunnable = null;
      }

      needsDrip = bucketLevel > 0;
    }

    if (runnable != null) {
      runnable.run();
    }

    if (needsDrip) {
      handler.postDelayed(this::drip, dripInterval);
    }
  }
}
