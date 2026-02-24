package org.signal.core.util.concurrent;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;

import java.util.concurrent.Executor;

import io.reactivex.rxjava3.observers.DefaultObserver;

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

    SignalExecutors.BOUNDED.execute(() -> {
      final E result = backgroundTask.run();

      if (isValid(lifecycle)) {
        ThreadUtil.runOnMain(() -> {
          if (isValid(lifecycle)) {
            foregroundTask.run(result);
          }
        });
      }
    });
  }

  /**
   * Runs a task in the background and passes the result of the computation to a task that is run
   * on the main thread. Will only invoke the {@code foregroundTask} if the provided {@link Lifecycle}
   * is or enters in the future a valid (i.e. visible) state. In this way, it is very similar to
   * {@link AsyncTask}, but is safe in that you can guarantee your task won't be called when your
   * view is in an invalid state.
   */
  public static <E> void runWhenValid(@NonNull Lifecycle lifecycle, @NonNull BackgroundTask<E> backgroundTask, @NonNull ForegroundTask<E> foregroundTask) {
    lifecycle.addObserver(new LifecycleEventObserver() {
      @Override public void onStateChanged(@NonNull LifecycleOwner lifecycleOwner, @NonNull Lifecycle.Event event) {
        if (isValid(lifecycle)) {
          lifecycle.removeObserver(this);

          SignalExecutors.BOUNDED.execute(() -> {
            final E result = backgroundTask.run();

            if (isValid(lifecycle)) {
              ThreadUtil.runOnMain(() -> {
                if (isValid(lifecycle)) {
                  foregroundTask.run(result);
                }
              });
            }
          });
        }
      }
    });
  }

  /**
   * Runs a task in the background and passes the result of the computation to a task that is run on
   * the main thread. Essentially {@link AsyncTask}, but lambda-compatible.
   */
  public static <E> void run(@NonNull BackgroundTask<E> backgroundTask, @NonNull ForegroundTask<E> foregroundTask) {
    run(SignalExecutors.BOUNDED, backgroundTask, foregroundTask);
  }

  /**
   * Runs a task on the specified {@link Executor} and passes the result of the computation to a
   * task that is run on the main thread. Essentially {@link AsyncTask}, but lambda-compatible.
   */
  public static <E> void run(@NonNull Executor executor, @NonNull BackgroundTask<E> backgroundTask, @NonNull ForegroundTask<E> foregroundTask) {
    executor.execute(() -> {
      final E result = backgroundTask.run();
      ThreadUtil.runOnMain(() -> foregroundTask.run(result));
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
