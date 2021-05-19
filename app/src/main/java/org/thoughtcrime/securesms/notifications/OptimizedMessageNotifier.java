package org.thoughtcrime.securesms.notifications;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import org.session.libsession.messaging.sending_receiving.notifications.MessageNotifier;
import org.session.libsession.messaging.sending_receiving.pollers.Poller;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsession.utilities.Debouncer;
import org.session.libsignal.utilities.ThreadUtils;
import org.thoughtcrime.securesms.ApplicationContext;

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
    Poller poller = ApplicationContext.getInstance(context).poller;
    // FIXME: Open group handling
    boolean isCaughtUp = true;
    if (poller != null) {
      isCaughtUp = isCaughtUp && poller.isCaughtUp();
    }

    // FIXME: Open group handling
    /*
    if (publicChatManager != null) {
      isCaughtUp = isCaughtUp && publicChatManager.areAllCaughtUp();
    }
     */

    if (isCaughtUp) {
      performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotification(context));
    } else {
      debouncer.publish(() -> performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotification(context)));
    }
  }

  @Override
  public void updateNotification(@NonNull Context context, long threadId) {
    Poller lokiPoller = ApplicationContext.getInstance(context).poller;
    // FIXME: Open group handling
    boolean isCaughtUp = true;
    if (lokiPoller != null) {
      isCaughtUp = isCaughtUp && lokiPoller.isCaughtUp();
    }

    // FIXME: Open group handling
    /*
    if (publicChatManager != null) {
      isCaughtUp = isCaughtUp && publicChatManager.areAllCaughtUp();
    }
     */
    
    if (isCaughtUp) {
      performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotification(context, threadId));
    } else {
      debouncer.publish(() -> performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotification(context, threadId)));
    }
  }

  @Override
  public void updateNotification(@NonNull Context context, long threadId, boolean signal) {
    Poller lokiPoller = ApplicationContext.getInstance(context).poller;
    // FIXME: Open group handling
    boolean isCaughtUp = true;
    if (lokiPoller != null) {
      isCaughtUp = isCaughtUp && lokiPoller.isCaughtUp();
    }

    // FIXME: Open group handling
    /*
    if (publicChatManager != null) {
      isCaughtUp = isCaughtUp && publicChatManager.areAllCaughtUp();
    }
     */

    if (isCaughtUp) {
      performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotification(context, threadId, signal));
    } else {
      debouncer.publish(() -> performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotification(context, threadId, signal)));
    }
  }

  @Override
  public void updateNotification(@androidx.annotation.NonNull Context context, boolean signal, int reminderCount) {
    Poller lokiPoller = ApplicationContext.getInstance(context).poller;
    // FIXME: Open group handling
    boolean isCaughtUp = true;
    if (lokiPoller != null) {
      isCaughtUp = isCaughtUp && lokiPoller.isCaughtUp();
    }

    // FIXME: Open group handling
    /*
    if (publicChatManager != null) {
      isCaughtUp = isCaughtUp && publicChatManager.areAllCaughtUp();
    }
     */

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
      ThreadUtils.queue(r);
    } else {
      r.run();
    }
  }
}
