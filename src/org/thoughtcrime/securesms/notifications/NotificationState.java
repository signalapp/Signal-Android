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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
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

  public PendingIntent getMarkAsReadIntent(Context context, int notificationId, @Nullable Long threadId) {
    long[] threadArray = new long[threads.size()];
    int    index       = 0;

    for (long thread : threads) {
      if (threadId == null || threadId == thread) {
        Log.w("NotificationState", "Added thread: " + thread);
        threadArray[index++] = thread;
      }
    }

    Intent intent = new Intent(MarkReadReceiver.CLEAR_ACTION);
    intent.putExtra(MarkReadReceiver.THREAD_IDS_EXTRA, threadArray);
    intent.setPackage(context.getPackageName());

    // XXX : This is an Android bug.  If we don't pull off the extra
    // once before handing off the PendingIntent, the array will be
    // truncated to one element when the PendingIntent fires.  Thanks guys!
    Log.w("NotificationState", "Pending array off intent length: " +
        intent.getLongArrayExtra("thread_ids").length);

    return PendingIntent.getBroadcast(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public PendingIntent getWearableReplyIntent(Context context, Recipients recipients, int notificationId) {
    Intent intent = new Intent(WearReplyReceiver.REPLY_ACTION);
    intent.putExtra(WearReplyReceiver.RECIPIENT_IDS_EXTRA, recipients.getIds());
    intent.setPackage(context.getPackageName());

    return PendingIntent.getBroadcast(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public PendingIntent getQuickReplyIntent(Context context, Recipients recipients, int notificationId) {
    Intent     intent           = new Intent(context, ConversationPopupActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, (long)threads.toArray()[0]);
    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));

    return PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }


}
