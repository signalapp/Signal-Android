package org.thoughtcrime.securesms.notifications;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.LeakyBucketLimiter;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

/**
 * Uses a leaky-bucket strategy to limiting notification updates.
 */
public class OptimizedMessageNotifier implements MessageNotifier {

  private final MessageNotifier    wrapped;
  private final LeakyBucketLimiter limiter;

  @MainThread
  public OptimizedMessageNotifier(@NonNull MessageNotifier wrapped) {
    this.wrapped = wrapped;
    this.limiter = new LeakyBucketLimiter(5, 1000, new Handler(SignalExecutors.getAndStartHandlerThread("signal-notifier").getLooper()));
  }

  @Override
  public void setVisibleThread(long threadId) {
    wrapped.setVisibleThread(threadId);
  }

  @Override
  public void clearVisibleThread() {
    wrapped.clearVisibleThread();
  }

  @Override
  public void setLastDesktopActivityTimestamp(long timestamp) {
    wrapped.setLastDesktopActivityTimestamp(timestamp);
  }

  @Override
  public void notifyMessageDeliveryFailed(Context context, Recipient recipient, long threadId) {
    wrapped.notifyMessageDeliveryFailed(context, recipient, threadId);
  }

  @Override
  public void cancelDelayedNotifications() {
    wrapped.cancelDelayedNotifications();
  }

  @Override
  public void updateNotification(@NonNull Context context) {
    runOnLimiter(() -> wrapped.updateNotification(context));
  }

  @Override
  public void updateNotification(@NonNull Context context, long threadId) {
    runOnLimiter(() -> wrapped.updateNotification(context, threadId));
  }

  @Override
  public void updateNotification(@NonNull Context context, long threadId, boolean signal) {
    runOnLimiter(() -> wrapped.updateNotification(context, threadId, signal));
  }

  @Override
  public void updateNotification(@NonNull Context context, long threadId, boolean signal, int reminderCount) {
    runOnLimiter(() -> wrapped.updateNotification(context, threadId, signal, reminderCount));
  }

  @Override
  public void clearReminder(@NonNull Context context) {
    wrapped.clearReminder(context);
  }

  private void runOnLimiter(@NonNull Runnable runnable) {
    Throwable prettyException = new Throwable();
    limiter.run(() -> {
      try {
        runnable.run();
      } catch (RuntimeException e) {
        throw Util.appendStackTrace(e, prettyException);
      }
    });
  }
}
