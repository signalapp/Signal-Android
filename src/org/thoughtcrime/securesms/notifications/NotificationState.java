package org.thoughtcrime.securesms.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.ConversationActivity;
import org.thoughtcrime.securesms.ConversationPopupActivity;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase.VibrateState;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

public class NotificationState {

  private final LinkedList<NotificationItem> notifications = new LinkedList<>();
  private final LinkedHashSet<Long>          threads       = new LinkedHashSet<>();

  private int notificationCount = 0;

  public NotificationState() {}

  public NotificationState(@NonNull List<NotificationItem> items) {
    for (NotificationItem item : items) {
      addNotification(item);
    }
  }

  public void addNotification(NotificationItem item) {
    notifications.addFirst(item);

    if (threads.contains(item.getThreadId())) {
      threads.remove(item.getThreadId());
    }

    threads.add(item.getThreadId());
    notificationCount++;
  }

  public @Nullable Uri getRingtone() {
    if (!notifications.isEmpty()) {
      Recipients recipients = notifications.getFirst().getRecipients();

      if (recipients != null) {
        return recipients.getRingtone();
      }
    }

    return null;
  }

  public VibrateState getVibrate() {
    if (!notifications.isEmpty()) {
      Recipients recipients = notifications.getFirst().getRecipients();

      if (recipients != null) {
        return recipients.getVibrate();
      }
    }

    return VibrateState.DEFAULT;
  }

  public boolean hasMultipleThreads() {
    return threads.size() > 1;
  }

  public LinkedHashSet<Long> getThreads() {
    return threads;
  }

  public int getThreadCount() {
    return threads.size();
  }

  public int getMessageCount() {
    return notificationCount;
  }

  public List<NotificationItem> getNotifications() {
    return notifications;
  }

  public List<NotificationItem> getNotificationsForThread(long threadId) {
    LinkedList<NotificationItem> list = new LinkedList<>();

    for (NotificationItem item : notifications) {
      if (item.getThreadId() == threadId) list.addFirst(item);
    }

    return list;
  }

  public PendingIntent getMarkAsReadIntent(Context context, int notificationId) {
    long[] threadArray = new long[threads.size()];
    int    index       = 0;

    for (long thread : threads) {
      Log.w("NotificationState", "Added thread: " + thread);
      threadArray[index++] = thread;
    }

    Intent intent = new Intent(MarkReadReceiver.CLEAR_ACTION);
    intent.setClass(context, MarkReadReceiver.class);
    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));
    intent.putExtra(MarkReadReceiver.THREAD_IDS_EXTRA, threadArray);
    intent.putExtra(MarkReadReceiver.NOTIFICATION_ID_EXTRA, notificationId);

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public PendingIntent getRemoteReplyIntent(Context context, Recipients recipients) {
    if (threads.size() != 1) throw new AssertionError("We only support replies to single thread notifications!");

    Intent intent = new Intent(RemoteReplyReceiver.REPLY_ACTION);
    intent.setClass(context, RemoteReplyReceiver.class);
    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));
    intent.putExtra(RemoteReplyReceiver.RECIPIENT_IDS_EXTRA, recipients.getIds());
    intent.setPackage(context.getPackageName());

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public PendingIntent getAndroidAutoReplyIntent(Context context, Recipients recipients) {
    if (threads.size() != 1) throw new AssertionError("We only support replies to single thread notifications!");

    Intent intent = new Intent(AndroidAutoReplyReceiver.REPLY_ACTION);
    intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
    intent.setClass(context, AndroidAutoReplyReceiver.class);
    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));
    intent.putExtra(AndroidAutoReplyReceiver.RECIPIENT_IDS_EXTRA, recipients.getIds());
    intent.putExtra(AndroidAutoReplyReceiver.THREAD_ID_EXTRA, (long)threads.toArray()[0]);
    intent.setPackage(context.getPackageName());

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public PendingIntent getAndroidAutoHeardIntent(Context context, int notificationId) {
    long[] threadArray = new long[threads.size()];
    int    index       = 0;
    for (long thread : threads) {
      Log.w("NotificationState", "getAndroidAutoHeardIntent Added thread: " + thread);
      threadArray[index++] = thread;
    }

    Intent intent = new Intent(AndroidAutoHeardReceiver.HEARD_ACTION);
    intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
    intent.setClass(context, AndroidAutoHeardReceiver.class);
    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));
    intent.putExtra(AndroidAutoHeardReceiver.THREAD_IDS_EXTRA, threadArray);
    intent.putExtra(AndroidAutoHeardReceiver.NOTIFICATION_ID_EXTRA, notificationId);
    intent.setPackage(context.getPackageName());

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public PendingIntent getQuickReplyIntent(Context context, Recipients recipients) {
    if (threads.size() != 1) throw new AssertionError("We only support replies to single thread notifications! " + threads.size());

    Intent     intent           = new Intent(context, ConversationPopupActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, (long)threads.toArray()[0]);
    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));

    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public PendingIntent getDeleteIntent(Context context) {
    int       index = 0;
    long[]    ids   = new long[notifications.size()];
    boolean[] mms   = new boolean[ids.length];

    for (NotificationItem notificationItem : notifications) {
      ids[index] = notificationItem.getId();
      mms[index++]   = notificationItem.isMms();
    }

    Intent intent = new Intent(context, DeleteNotificationReceiver.class);
    intent.setAction(DeleteNotificationReceiver.DELETE_NOTIFICATION_ACTION);
    intent.putExtra(DeleteNotificationReceiver.EXTRA_IDS, ids);
    intent.putExtra(DeleteNotificationReceiver.EXTRA_MMS, mms);
    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }


}
