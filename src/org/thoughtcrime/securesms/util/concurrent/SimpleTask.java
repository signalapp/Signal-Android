package org.thoughtcrime.securesms.util.concurrent;

import androidx.lifecycle.Lifecycle;
import android.os.AsyncTask;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.Util;

public class SimpleTask {

  /**
   * Runs a task in the background and passes the result of the computation to a task that is run
   * on the main thread. Will only invoke the {@code foregroundTask} if the provided {@link Lifecycle}
   * is in a valid (i.e. visible) state at that time. In this way, it is very similar to
   * {@link AsyncTask}, but is safe in that you can guarantee your task won't be called when your
   * view is in an invalid state.
   */
  public static <E> void run(@NonNull Lifecycle lifecycle, @NonNull BackgroundTask<E> backgroundTask, @NonNull ForegroundTask<E> foregroundTask) {
    if (!isValid(lifecycle)) {
      return;
    }

    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
      final E result = backgroundTask.run();

      if (isValid(lifecycle)) {
        Util.runOnMain(() -> {
          if (isValid(lifecycle)) {
            foregroundTask.run(result);
          }
        });
      }
    });
  }

  /**
   * Runs a task in the background and passes the result of the computation to a task that is run on
   * the main thread. Essentially {@link AsyncTask}, but lambda-compatible.
   */
  public static <E> void run(@NonNull BackgroundTask<E> backgroundTask, @NonNull ForegroundTask<E> foregroundTask) {
    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
      final E result = backgroundTask.run();
      Util.runOnMain(() -> foregroundTask.run(result));
    });
  }

  private static boolean isValid(@NonNull Lifecycle lifecycle) {
    return lifecycle.getCurrentState().isAtLeast(Lifecycle.State.CREATED);
  }

  public interface BackgroundTask<E> {
    E run();
  }

  public interface ForegroundTask<E> {
    void run(E result);
  }
}
