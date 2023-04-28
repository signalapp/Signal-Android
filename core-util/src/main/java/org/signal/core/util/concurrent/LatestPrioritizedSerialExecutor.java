package org.signal.core.util.concurrent;

import androidx.annotation.NonNull;

import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * A serial executor that will order pending tasks by a specified priority, and will only keep a single task of a given priority, preferring the latest.
 *
 * So imagine a world where the following tasks were all enqueued (meaning they're all waiting to be executed):
 *
 * execute(0, runnableA);
 * execute(3, runnableC1);
 * execute(3, runnableC2);
 * execute(2, runnableB);
 *
 * You'd expect the execution order to be:
 * - runnableC2
 * - runnableB
 * - runnableA
 *
 * (We order by priority, and C1 was replaced by C2)
 */
public final class LatestPrioritizedSerialExecutor {
  private final Queue<PriorityRunnable> tasks;
  private final Executor                executor;
  private       Runnable                active;

  public LatestPrioritizedSerialExecutor(@NonNull Executor executor) {
    this.executor = executor;
    this.tasks    = new PriorityQueue<>();
  }

  /**
   * Execute with a priority. Higher priorities are executed first.
   */
  public synchronized void execute(int priority, @NonNull Runnable r) {
    Iterator<PriorityRunnable> iterator = tasks.iterator();
    while (iterator.hasNext()) {
      if (iterator.next().getPriority() == priority) {
        iterator.remove();
      }
    }

    tasks.offer(new PriorityRunnable(priority) {
      @Override
      public void run() {
        try {
          r.run();
        } finally {
          scheduleNext();
        }
      }
    });
    if (active == null) {
      scheduleNext();
    }
  }

  private synchronized void scheduleNext() {
    if ((active = tasks.poll()) != null) {
      executor.execute(active);
    }
  }

  private abstract static class PriorityRunnable implements Runnable, Comparable<PriorityRunnable> {
    private final int priority;

    public PriorityRunnable(int priority) {
      this.priority = priority;
    }

    public int getPriority() {
      return priority;
    }

    @Override
    public final int compareTo(PriorityRunnable other) {
      return other.getPriority() - this.getPriority();
    }
  }
}
