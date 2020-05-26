package org.thoughtcrime.securesms.util.concurrent;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

/**
 * Wraps another executor to make a new executor that only keeps around two tasks:
 * - The actively running task
 * - A single enqueued task
 *
 * If multiple tasks are enqueued while one is running, only the latest task is kept. The rest are
 * dropped.
 *
 * This is useful when you want to enqueue a bunch of tasks at unknown intervals, but only the most
 * recent one is relevant. For instance, running a query in response to changing user input.
 *
 * Based on SerialExecutor
 * https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/Executor.html
 */
public final class SerialMonoLifoExecutor implements Executor {
  private final Executor executor;

  private Runnable next;
  private Runnable active;

  public SerialMonoLifoExecutor(@NonNull Executor executor) {
    this.executor = executor;
  }

  @Override
  public synchronized void execute(@NonNull Runnable command) {
    enqueue(command);
  }

  /**
   * @return True if a pending task was replaced by this one, otherwise false.
   */
  public synchronized boolean enqueue(@NonNull Runnable command) {
    boolean performedReplace = next != null;

    next = () -> {
      try {
        command.run();
      } finally {
        scheduleNext();
      }
    };

    if (active == null) {
      scheduleNext();
    }

    return performedReplace;
  }

  private synchronized void scheduleNext() {
    active = next;
    next   = null;
    if (active != null) {
      executor.execute(active);
    }
  }
}
