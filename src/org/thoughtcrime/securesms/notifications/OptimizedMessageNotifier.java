package org.thoughtcrime.securesms.notifications;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.loki.api.LokiPublicChatManager;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Debouncer;
import org.whispersystems.signalservice.loki.api.LokiPoller;

import java.util.concurrent.TimeUnit;

public class OptimizedMessageNotifier implements MessageNotifier {
  private final MessageNotifier         wrapped;
  private final Debouncer               debouncer;

  @MainThread
  public OptimizedMessageNotifier(@NonNull MessageNotifier wrapped) {
    this.wrapped   = wrapped;
    this.debouncer = new Debouncer(TimeUnit.SECONDS.toMillis(1));
  }

  @Override
  public void setVisibleThread(long threadId) { wrapped.setVisibleThread(threadId); }

  @Override
  public void setLastDesktopActivityTimestamp(long timestamp) { wrapped.setLastDesktopActivityTimestamp(timestamp);}

  @Override
  public void notifyMessageDeliveryFailed(Context context, Recipient recipient, long threadId) {
    wrapped.notifyMessageDeliveryFailed(context, recipient, threadId);
  }

  @Override
  public void cancelDelayedNotifications() { wrapped.cancelDelayedNotifications(); }

  @Override
  public void updateNotification(@NonNull Context context) {
    LokiPoller lokiPoller = ApplicationContext.getInstance(context).lokiPoller;
    LokiPublicChatManager lokiPublicChatManager = ApplicationContext.getInstance(context).lokiPublicChatManager;
    Boolean isCaughtUp = true;
    if (lokiPoller != null) {
      isCaughtUp = isCaughtUp && lokiPoller.isCaughtUp();
    }

    if (lokiPublicChatManager != null) {
      isCaughtUp = isCaughtUp && lokiPublicChatManager.areAllCaughtUp();
    }

    if (isCaughtUp) {
      performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotification(context));
    } else {
      debouncer.publish(() -> performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotification(context)));
    }
  }

  @Override
  public void updateNotification(@NonNull Context context, long threadId) {
    LokiPoller lokiPoller = ApplicationContext.getInstance(context).lokiPoller;
    LokiPublicChatManager lokiPublicChatManager = ApplicationContext.getInstance(context).lokiPublicChatManager;
    Boolean isCaughtUp = true;
    if (lokiPoller != null) {
      isCaughtUp = isCaughtUp && lokiPoller.isCaughtUp();
    }

    if (lokiPublicChatManager != null) {
      isCaughtUp = isCaughtUp && lokiPublicChatManager.areAllCaughtUp();
    }
    
    if (isCaughtUp) {
      performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotification(context, threadId));
    } else {
      debouncer.publish(() -> performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotification(context, threadId)));
    }
  }

  @Override
  public void updateNotification(@NonNull Context context, long threadId, boolean signal) {
    LokiPoller lokiPoller = ApplicationContext.getInstance(context).lokiPoller;
    LokiPublicChatManager lokiPublicChatManager = ApplicationContext.getInstance(context).lokiPublicChatManager;
    Boolean isCaughtUp = true;
    if (lokiPoller != null) {
      isCaughtUp = isCaughtUp && lokiPoller.isCaughtUp();
    }

    if (lokiPublicChatManager != null) {
      isCaughtUp = isCaughtUp && lokiPublicChatManager.areAllCaughtUp();
    }

    if (isCaughtUp) {
      performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotification(context, threadId, signal));
    } else {
      debouncer.publish(() -> performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotification(context, threadId, signal)));
    }
  }

  @Override
  public void updateNotification(@android.support.annotation.NonNull Context context, boolean signal, int reminderCount) {
    LokiPoller lokiPoller = ApplicationContext.getInstance(context).lokiPoller;
    LokiPublicChatManager lokiPublicChatManager = ApplicationContext.getInstance(context).lokiPublicChatManager;
    Boolean isCaughtUp = true;
    if (lokiPoller != null) {
      isCaughtUp = isCaughtUp && lokiPoller.isCaughtUp();
    }

    if (lokiPublicChatManager != null) {
      isCaughtUp = isCaughtUp && lokiPublicChatManager.areAllCaughtUp();
    }

    if (isCaughtUp) {
      performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotification(context, signal, reminderCount));
    } else {
      debouncer.publish(() -> performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotification(context, signal, reminderCount)));
    }
  }

  @Override
  public void clearReminder(@NonNull Context context) { wrapped.clearReminder(context); }

  private void performOnBackgroundThreadIfNeeded(Runnable r) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      new Thread(r).start();
    } else {
      r.run();
    }
  }
}
