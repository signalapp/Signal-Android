package org.thoughtcrime.securesms.util;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;

import java.util.LinkedList;
import java.util.List;

/**
 * Manages our app startup flow.
 */
public final class AppStartup {

  /** The time to wait after Application#onCreate() to see if any UI rendering starts */
  private final long UI_WAIT_TIME = 500;

  /** The maximum amount of time we'll wait for critical rendering events to finish.  */
  private final long FAILSAFE_RENDER_TIME = 2500;

  private static final String TAG = Log.tag(AppStartup.class);

  private static final AppStartup INSTANCE = new AppStartup();

  private final List<Task> blocking;
  private final List<Task> nonBlocking;
  private final List<Task> postRender;
  private final Handler    postRenderHandler;

  private int outstandingCriticalRenderEvents;

  private long applicationStartTime;
  private long renderStartTime;
  private long renderEndTime;

  public static @NonNull AppStartup getInstance() {
    return INSTANCE;
  }

  private AppStartup() {
    this.blocking          = new LinkedList<>();
    this.nonBlocking       = new LinkedList<>();
    this.postRender        = new LinkedList<>();
    this.postRenderHandler = new Handler(Looper.getMainLooper());
  }

  public void onApplicationCreate() {
    this.applicationStartTime = System.currentTimeMillis();
  }

  /**
   * Schedules a task that must happen during app startup in a blocking fashion.
   */
  @MainThread
  public @NonNull AppStartup addBlocking(@NonNull String name, @NonNull Runnable task) {
    blocking.add(new Task(name, task));
    return this;
  }

  /**
   * Schedules a task that should not block app startup, but should still happen as quickly as
   * possible.
   */
  @MainThread
  public @NonNull AppStartup addNonBlocking(@NonNull Runnable task) {
    nonBlocking.add(new Task("", task));
    return this;
  }

  /**
   * Schedules a task that should only be executed after all critical UI has been rendered. If no
   * UI will be shown (i.e. the Application was created in the background), this will simply happen
   * a short delay after {@link Application#onCreate()}.
   * @param task
   * @return
   */
  @MainThread
  public @NonNull AppStartup addPostRender(@NonNull Runnable task) {
    postRender.add(new Task("", task));
    return this;
  }

  /**
   * Indicates a UI event critical to initial rendering has started. This will delay tasks that were
   * scheduled via {@link #addPostRender(Runnable)}. You MUST call
   * {@link #onCriticalRenderEventEnd()} for each invocation of this method.
   */
  @MainThread
  public void onCriticalRenderEventStart() {
    if (outstandingCriticalRenderEvents == 0 && postRender.size() > 0) {
      Log.i(TAG, "Received first critical render event.");
      renderStartTime = System.currentTimeMillis();

      postRenderHandler.removeCallbacksAndMessages(null);
      postRenderHandler.postDelayed(() -> {
        Log.w(TAG, "Reached the failsafe event for post-render! Either someone forgot to call #onRenderEnd(), the activity was started while the phone was locked, or app start is taking a very long time.");
        executePostRender();
      }, FAILSAFE_RENDER_TIME);
    }

    outstandingCriticalRenderEvents++;
  }

  /**
   * Indicates a UI event critical to initial rendering has ended. Should only be paired with
   * {@link #onCriticalRenderEventStart()}.
   */
  @MainThread
  public void onCriticalRenderEventEnd() {
    if (outstandingCriticalRenderEvents <= 0) {
      Log.w(TAG, "Too many end events! onCriticalRenderEventStart/End was mismanaged.");
    }

    outstandingCriticalRenderEvents = Math.max(outstandingCriticalRenderEvents - 1, 0);

    if (outstandingCriticalRenderEvents == 0 && postRender.size() > 0) {
      renderEndTime = System.currentTimeMillis();

      Log.i(TAG, "First render has finished. " +
                 "Cold Start: " + (renderEndTime - applicationStartTime) + " ms, " +
                 "Render Time: " + (renderEndTime - renderStartTime) + " ms");

      postRenderHandler.removeCallbacksAndMessages(null);
      executePostRender();
    }
  }

  /**
   * Begins all pending task execution.
   */
  @MainThread
  public void execute() {
    Stopwatch stopwatch = new Stopwatch("init");

    for (Task task : blocking) {
      task.getRunnable().run();
      stopwatch.split(task.getName());
    }
    blocking.clear();

    for (Task task : nonBlocking) {
      SignalExecutors.BOUNDED.execute(task.getRunnable());
    }
    nonBlocking.clear();

    stopwatch.split("schedule-non-blocking");
    stopwatch.stop(TAG);

    postRenderHandler.postDelayed(() -> {
      Log.i(TAG, "Assuming the application has started in the background. Running post-render tasks.");
      executePostRender();
    }, UI_WAIT_TIME);
  }

  private void executePostRender() {
    for (Task task : postRender) {
      SignalExecutors.BOUNDED.execute(task.getRunnable());
    }
    postRender.clear();
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
