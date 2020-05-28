package org.thoughtcrime.securesms.notifications;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.messages.InitialMessageRetriever;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Throttler;

import java.util.concurrent.TimeUnit;

/**
 * Wraps another {@link MessageNotifier} and throttles it while {@link InitialMessageRetriever} is
 * running.
 */
public class OptimizedMessageNotifier implements MessageNotifier {

  private final MessageNotifier         wrapped;
  private final Throttler               throttler;
  private final InitialMessageRetriever retriever;

  @MainThread
  public OptimizedMessageNotifier(@NonNull MessageNotifier wrapped) {
    this.wrapped   = wrapped;
    this.throttler = new Throttler(TimeUnit.SECONDS.toMillis(5));
    this.retriever = ApplicationDependencies.getInitialMessageRetriever();
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
    if (retriever.isCaughtUp()) {
      wrapped.updateNotification(context);
    } else {
      throttler.publish(() -> wrapped.updateNotification(context));
    }
  }

  @Override
  public void updateNotification(@NonNull Context context, long threadId) {
    if (retriever.isCaughtUp()) {
      wrapped.updateNotification(context, threadId);
    } else {
      throttler.publish(() -> wrapped.updateNotification(context));
    }
  }

  @Override
  public void updateNotification(@NonNull Context context, long threadId, boolean signal) {
    if (retriever.isCaughtUp()) {
      wrapped.updateNotification(context, threadId, signal);
    } else {
      throttler.publish(() -> wrapped.updateNotification(context));
    }
  }

  @Override
  public void updateNotification(@NonNull Context context, long threadId, boolean signal, int reminderCount) {
    if (retriever.isCaughtUp()) {
      wrapped.updateNotification(context, threadId, signal, reminderCount);
    } else {
      throttler.publish(() -> wrapped.updateNotification(context));
    }
  }

  @Override
  public void clearReminder(@NonNull Context context) {
    wrapped.clearReminder(context);
  }
}
