package org.thoughtcrime.securesms.notifications;

import android.content.Context;

import org.thoughtcrime.securesms.recipients.Recipient;

import androidx.annotation.NonNull;

public interface MessageNotifier {
  void setVisibleThread(long threadId);
  void setLastDesktopActivityTimestamp(long timestamp);
  void notifyMessageDeliveryFailed(Context context, Recipient recipient, long threadId);
  void cancelDelayedNotifications();
  void updateNotification(@NonNull Context context);
  void updateNotification(@NonNull Context context, long threadId);
  void updateNotification(@NonNull Context context, long threadId, boolean signal);
  void updateNotification(@android.support.annotation.NonNull Context context, boolean signal, int reminderCount);
  void clearReminder(@NonNull Context context);
}
