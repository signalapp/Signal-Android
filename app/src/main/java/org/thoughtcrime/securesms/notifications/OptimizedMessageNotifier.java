package org.thoughtcrime.securesms.notifications;

import android.app.Application;
import android.content.Context;
import android.os.Handler;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ExceptionUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.notifications.v2.MessageNotifierV2;
import org.thoughtcrime.securesms.notifications.v2.NotificationThread;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.BubbleUtil;
import org.thoughtcrime.securesms.util.LeakyBucketLimiter;

import java.util.Optional;

/**
 * Uses a leaky-bucket strategy to limiting notification updates.
 */
public class OptimizedMessageNotifier implements MessageNotifier {

  private final LeakyBucketLimiter limiter;
  private final MessageNotifierV2  messageNotifierV2;

  @MainThread
  public OptimizedMessageNotifier(@NonNull Application context) {
    this.limiter           = new LeakyBucketLimiter(5, 1000, new Handler(SignalExecutors.getAndStartHandlerThread("signal-notifier").getLooper()));
    this.messageNotifierV2 = new MessageNotifierV2(context);
  }

  @Override
  public void setVisibleThread(@Nullable NotificationThread notificationThread) {
    getNotifier().setVisibleThread(notificationThread);
  }

  @Override
  public @NonNull Optional<NotificationThread> getVisibleThread() {
    return getNotifier().getVisibleThread();
  }

  @Override
  public void clearVisibleThread() {
    getNotifier().clearVisibleThread();
  }

  @Override
  public void setLastDesktopActivityTimestamp(long timestamp) {
    getNotifier().setLastDesktopActivityTimestamp(timestamp);
  }

  @Override
  public void notifyMessageDeliveryFailed(@NonNull Context context, @NonNull Recipient recipient, @NonNull NotificationThread notificationThread) {
    getNotifier().notifyMessageDeliveryFailed(context, recipient, notificationThread);
  }

  @Override
  public void notifyProofRequired(@NonNull Context context, @NonNull Recipient recipient, @NonNull NotificationThread notificationThread) {
    getNotifier().notifyProofRequired(context, recipient, notificationThread);
  }

  @Override
  public void cancelDelayedNotifications() {
    getNotifier().cancelDelayedNotifications();
  }

  @Override
  public void updateNotification(@NonNull Context context) {
    runOnLimiter(() -> getNotifier().updateNotification(context));
  }

  @Override
  public void updateNotification(@NonNull Context context, @NonNull NotificationThread notificationThread) {
    runOnLimiter(() -> getNotifier().updateNotification(context, notificationThread));
  }

  @Override
  public void updateNotification(@NonNull Context context, @NonNull NotificationThread notificationThread, @NonNull BubbleUtil.BubbleState defaultBubbleState) {
    runOnLimiter(() -> getNotifier().updateNotification(context, notificationThread, defaultBubbleState));
  }

  @Override
  public void updateNotification(@NonNull Context context, @NonNull NotificationThread notificationThread, boolean signal) {
    runOnLimiter(() -> getNotifier().updateNotification(context, notificationThread, signal));
  }

  @Override
  public void updateNotification(@NonNull Context context, @Nullable NotificationThread notificationThread, boolean signal, int reminderCount, @NonNull BubbleUtil.BubbleState defaultBubbleState) {
    runOnLimiter(() -> getNotifier().updateNotification(context, notificationThread, signal, reminderCount, defaultBubbleState));
  }

  @Override
  public void clearReminder(@NonNull Context context) {
    getNotifier().clearReminder(context);
  }

  @Override
  public void addStickyThread(@NonNull NotificationThread notificationThread, long earliestTimestamp) {
    getNotifier().addStickyThread(notificationThread, earliestTimestamp);
  }

  @Override
  public void removeStickyThread(@NonNull NotificationThread notificationThread) {
    getNotifier().removeStickyThread(notificationThread);
  }

  private void runOnLimiter(@NonNull Runnable runnable) {
    Throwable prettyException = new Throwable();
    limiter.run(() -> {
      try {
        runnable.run();
      } catch (RuntimeException e) {
        throw ExceptionUtil.joinStackTrace(e, prettyException);
      }
    });
  }

  private MessageNotifier getNotifier() {
    return messageNotifierV2;
  }
}
