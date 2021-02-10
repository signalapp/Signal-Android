package org.thoughtcrime.securesms.service;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;

import java.util.Objects;

/**
 * Represents a delayed foreground notification.
 * <p>
 * With this, you can {@link #close()} it to dismiss or prevent it showing if it hasn't already.
 * <p>
 * You can also {@link #showNow()} to show if it's not already. This returns a regular {@link NotificationController} which can be updated if required.
 */
public abstract class DelayedNotificationController implements AutoCloseable {

  private static final String TAG = Log.tag(DelayedNotificationController.class);

  public static final long SHOW_WITHOUT_DELAY = 0;
  public static final long DO_NOT_SHOW        = -1;

  private DelayedNotificationController() {}

  static DelayedNotificationController create(long delayMillis, @NonNull Create createTask) {
    if (delayMillis == SHOW_WITHOUT_DELAY) return new Shown(createTask.create());
    if (delayMillis == DO_NOT_SHOW)        return new NoShow();
    if (delayMillis > 0)                   return new DelayedShow(delayMillis, createTask);

    throw new IllegalArgumentException("Illegal delay " + delayMillis);
  }

  /**
   * Show the foreground notification if it's not already showing.
   * <p>
   * If it does show, it returns a regular {@link NotificationController} which you can use to update its message or progress.
   */
  public abstract @Nullable NotificationController showNow();

  @Override
  public void close() {
  }

  private static final class NoShow extends DelayedNotificationController {

    @Override
    public @Nullable NotificationController showNow() {
      return null;
    }
  }

  private static final class Shown extends DelayedNotificationController {

    private final NotificationController controller;

    Shown(@NonNull NotificationController controller) {
      this.controller = controller;
    }

    @Override
    public void close() {
      this.controller.close();
    }

    @Override
    public NotificationController showNow() {
      return controller;
    }
  }

  private static final class DelayedShow extends DelayedNotificationController {

    private final Create                 createTask;
    private final Handler                handler;
    private final Runnable               start;
    private       NotificationController notificationController;
    private       boolean                isClosed;

    private DelayedShow(long delayMillis, @NonNull Create createTask) {
      this.createTask = createTask;
      this.handler    = new Handler(Looper.getMainLooper());
      this.start      = this::start;

      handler.postDelayed(start, delayMillis);
    }

    private void start() {
      SignalExecutors.BOUNDED.execute(this::showNowInner);
    }

    public synchronized @NonNull NotificationController showNow() {
      if (isClosed) {
        throw new AssertionError("showNow called after close");
      }
      return Objects.requireNonNull(showNowInner());
    }

    private synchronized @Nullable NotificationController showNowInner() {
      if (notificationController != null) {
        return notificationController;
      }

      if (!isClosed) {
        Log.i(TAG, "Starting foreground service");
        notificationController = createTask.create();
        return notificationController;
      } else {
        Log.i(TAG, "Did not start foreground service as close has been called");
        return null;
      }
    }

    @Override
    public synchronized void close() {
      handler.removeCallbacks(start);
      isClosed = true;
      if (notificationController != null) {
        Log.d(TAG, "Closing");
        notificationController.close();
      } else {
        Log.d(TAG, "Never showed");
      }
    }
  }

  public interface Create {
    @NonNull NotificationController create();
  }
}
