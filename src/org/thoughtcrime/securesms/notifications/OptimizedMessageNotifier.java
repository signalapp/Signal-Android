package org.thoughtcrime.securesms.notifications;

import android.content.Context;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.loki.api.LokiPublicChatManager;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.Throttler;
import org.whispersystems.signalservice.loki.api.LokiPoller;

import java.util.concurrent.TimeUnit;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

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
    Boolean isCatchUp = false;
    if (lokiPoller != null && lokiPublicChatManager != null) {
      isCatchUp = lokiPoller.isCatchUp() && lokiPublicChatManager.isAllCatchUp();
    }

    if (isCatchUp) {
      wrapped.updateNotification(context);
    } else {
      debouncer.publish(() -> wrapped.updateNotification(context));
    }
  }

  @Override
  public void updateNotification(@NonNull Context context, long threadId) {
    LokiPoller lokiPoller = ApplicationContext.getInstance(context).lokiPoller;
    LokiPublicChatManager lokiPublicChatManager = ApplicationContext.getInstance(context).lokiPublicChatManager;
    Boolean isCatchUp = false;
    if (lokiPoller != null && lokiPublicChatManager != null) {
      isCatchUp = lokiPoller.isCatchUp() && lokiPublicChatManager.isAllCatchUp();
    }

    if (isCatchUp) {
      wrapped.updateNotification(context, threadId);
    } else {
      debouncer.publish(() -> wrapped.updateNotification(context, threadId));
    }
  }

  @Override
  public void updateNotification(@NonNull Context context, long threadId, boolean signal) {
    LokiPoller lokiPoller = ApplicationContext.getInstance(context).lokiPoller;
    LokiPublicChatManager lokiPublicChatManager = ApplicationContext.getInstance(context).lokiPublicChatManager;
    Boolean isCatchUp = false;
    if (lokiPoller != null && lokiPublicChatManager != null) {
      isCatchUp = lokiPoller.isCatchUp() && lokiPublicChatManager.isAllCatchUp();
    }

    if (isCatchUp) {
      wrapped.updateNotification(context, threadId, signal);
    } else {
      debouncer.publish(() -> wrapped.updateNotification(context, threadId, signal));
    }
  }

  @Override
  public void updateNotification(@android.support.annotation.NonNull Context context, boolean signal, int reminderCount) {
    LokiPoller lokiPoller = ApplicationContext.getInstance(context).lokiPoller;
    LokiPublicChatManager lokiPublicChatManager = ApplicationContext.getInstance(context).lokiPublicChatManager;
    Boolean isCatchUp = false;
    if (lokiPoller != null && lokiPublicChatManager != null) {
      isCatchUp = lokiPoller.isCatchUp() && lokiPublicChatManager.isAllCatchUp();
    }

    if (isCatchUp) {
      wrapped.updateNotification(context, signal, reminderCount);
    } else {
      debouncer.publish(() -> wrapped.updateNotification(context, signal, reminderCount));
    }
  }


  @Override
  public void clearReminder(@NonNull Context context) { wrapped.clearReminder(context); }
}
