package org.thoughtcrime.securesms.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.notifications.v2.NotificationThread;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.BubbleUtil;

import java.util.Optional;

public interface MessageNotifier {
  void setVisibleThread(@Nullable NotificationThread notificationThread);
  @NonNull Optional<NotificationThread> getVisibleThread();
  void clearVisibleThread();
  void setLastDesktopActivityTimestamp(long timestamp);
  void notifyMessageDeliveryFailed(@NonNull Context context, @NonNull Recipient recipient, @NonNull NotificationThread notificationThread);
  void notifyProofRequired(@NonNull Context context, @NonNull Recipient recipient, @NonNull NotificationThread notificationThread);
  void cancelDelayedNotifications();
  void updateNotification(@NonNull Context context);
  void updateNotification(@NonNull Context context, @NonNull NotificationThread notificationThread);
  void updateNotification(@NonNull Context context, @NonNull NotificationThread notificationThread, @NonNull BubbleUtil.BubbleState defaultBubbleState);
  void updateNotification(@NonNull Context context, @NonNull NotificationThread notificationThread, boolean signal);
  void updateNotification(@NonNull Context context, @Nullable NotificationThread notificationThread, boolean signal, int reminderCount, @NonNull BubbleUtil.BubbleState defaultBubbleState);
  void clearReminder(@NonNull Context context);
  void addStickyThread(@NonNull NotificationThread notificationThread, long earliestTimestamp);
  void removeStickyThread(@NonNull NotificationThread notificationThread);


  class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
      SignalExecutors.BOUNDED.execute(() -> {
        int reminderCount = intent.getIntExtra("reminder_count", 0);
        ApplicationDependencies.getMessageNotifier().updateNotification(context, null, true, reminderCount + 1, BubbleUtil.BubbleState.HIDDEN);
      });
    }
  }
}
