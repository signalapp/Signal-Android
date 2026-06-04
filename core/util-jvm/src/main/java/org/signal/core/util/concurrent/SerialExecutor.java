package org.signal.core.util.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

/**
 * From https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/Executor.html
 */
public final class SerialExecutor implements Executor {
  private final Queue<Runnable> tasks    = new ArrayDeque<>();
  private final Executor        executor;
  private       Runnable        active;

  public SerialExecutor(@NotNull Executor executor) {
    this.executor = executor;
  }

  public synchronized void execute(final Runnable r) {
    tasks.offer(() -> {
      try {
        r.run();
      } finally {
        scheduleNext();
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
}
