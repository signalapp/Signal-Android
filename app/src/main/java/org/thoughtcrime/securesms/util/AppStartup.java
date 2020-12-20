package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;

import java.util.LinkedList;
import java.util.List;

public final class AppStartup {

  private static final String TAG = Log.tag(AppStartup.class);

  private final List<Task> blocking;
  private final List<Task> deferred;

  public AppStartup() {
    this.blocking = new LinkedList<>();
    this.deferred = new LinkedList<>();
  }

  public @NonNull
  AppStartup addBlocking(@NonNull String name, @NonNull Runnable task) {
    blocking.add(new Task(name, task));
    return this;
  }

  public @NonNull
  AppStartup addDeferred(@NonNull Runnable task) {
    deferred.add(new Task("", task));
    return this;
  }

  public void execute() {
    Stopwatch stopwatch = new Stopwatch("init");

    for (Task task : blocking) {
      task.getRunnable().run();
      stopwatch.split(task.getName());
    }

    for (Task task : deferred) {
      SignalExecutors.BOUNDED.execute(task.getRunnable());
    }

    stopwatch.split("schedule-deferred");
    stopwatch.stop(TAG);
  }

  private class Task {
    private final String   name;
    private final Runnable runnable;

    protected Task(@NonNull String name, @NonNull Runnable runnable) {
      this.name = name;
      this.runnable = runnable;
    }

    @NonNull String getName() {
      return name;
    }

    public @NonNull Runnable getRunnable() {
      return runnable;
    }
  }
}
