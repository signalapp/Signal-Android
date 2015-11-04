package org.thoughtcrime.securesms.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.ConversationActivity;
import org.thoughtcrime.securesms.ConversationPopupActivity;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase.VibrateState;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class NotificationState {

  private final LinkedList<NotificationItem> notifications = new LinkedList<>();
  private final Set<Long>                    threads       = new HashSet<>();

  private int notificationCount = 0;

  public void addNotification(NotificationItem item) {
    notifications.addFirst(item);
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

  public int getThreadCount() {
    return threads.size();
  }

  public int getMessageCount() {
    return notificationCount;
  }

  public List<NotificationItem> getNotifications() {
    return notifications;
  }

  public OrderedThreadNotifications orderedThreadNotifications() {
    LinkedHashSet<Long> orderedThreads = new LinkedHashSet<>();
    Map<Long, NotificationState> threadNotificationMapping = new HashMap<>();

    ListIterator<NotificationItem> iterator = notifications.listIterator(notifications.size());

    while(iterator.hasPrevious()) {
      NotificationItem item = iterator.previous();
      Long threadId = item.getThreadId();

      orderedThreads.remove(threadId);
      orderedThreads.add(threadId);

      if(!threadNotificationMapping.containsKey(threadId)) {
        threadNotificationMapping.put(threadId, new NotificationState());
      }

      NotificationState notificationState = threadNotificationMapping.get(threadId);
      notificationState.addNotification(item);
    }

    return new OrderedThreadNotifications(orderedThreads, threadNotificationMapping);
  }

  public PendingIntent getMarkAsReadIntent(Context context, Integer threadId) {
    long[] threadArray = new long[threads.size()];
    int    index       = 0;

    for (long thread : threads) {
      Log.w("NotificationState", "Added thread: " + thread);
      threadArray[index++] = thread;
    }

    Intent intent = new Intent(MarkReadReceiver.CLEAR_ACTION);
    intent.putExtra(MarkReadReceiver.THREAD_IDS_EXTRA, threadArray);
    intent.setPackage(context.getPackageName());

    // XXX : This is an Android bug.  If we don't pull off the extra
    // once before handing off the PendingIntent, the array will be
    // truncated to one element when the PendingIntent fires.  Thanks guys!
    Log.w("NotificationState", "Pending array off intent length: " +
        intent.getLongArrayExtra("thread_ids").length);

    return PendingIntent.getBroadcast(context, threadId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public PendingIntent getMarkAllAsReadIntent(Context context) {
    return getMarkAsReadIntent(context, 0);
  }

  public PendingIntent getWearableReplyIntent(Context context, Recipients recipients) {
    if (threads.size() != 1) throw new AssertionError("We only support replies to single thread notifications!");

    Intent intent = new Intent(WearReplyReceiver.REPLY_ACTION);
    intent.putExtra(WearReplyReceiver.RECIPIENT_IDS_EXTRA, recipients.getIds());
    intent.setPackage(context.getPackageName());

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public PendingIntent getQuickReplyIntent(Context context, Recipients recipients) {
    if (threads.size() != 1) throw new AssertionError("We only support replies to single thread notifications!");

    Intent     intent           = new Intent(context, ConversationPopupActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, (long)threads.toArray()[0]);
    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));

    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }


  public Set<Long> getThreads() {
    return threads;
  }
}
