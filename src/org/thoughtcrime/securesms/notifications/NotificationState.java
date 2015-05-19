package org.thoughtcrime.securesms.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
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

  public PendingIntent getMarkAsReadIntent(Context context, MasterSecret masterSecret) {
    Bundle extras = new Bundle();
    extras.putParcelable("master_secret", masterSecret);
    return craftIntent(context, MarkReadReceiver.CLEAR_ACTION, extras);
  }

  public PendingIntent getReplyIntent(Context context, MasterSecret masterSecret, long recipientId) {
    Bundle extras = new Bundle();
    extras.putParcelable("master_secret", masterSecret);
    extras.putLong("recipient_id", recipientId);
    return craftIntent(context, WearReplyReceiver.REPLY_ACTION, extras);
  }

  private PendingIntent craftIntent(Context context, String intentAction, Bundle extras) {
    long[] threadArray = new long[threads.size()];
    int index          = 0;

    for (long thread : threads) {
      Log.w("NotificationState", "Added thread: " + thread);
      threadArray[index++] = thread;
    }

    Intent intent = new Intent(intentAction);
    intent.putExtra("thread_ids", threadArray);
    intent.putExtras(extras);
    intent.setPackage(context.getPackageName());

    // XXX : This is an Android bug.  If we don't pull off the extra
    // once before handing off the PendingIntent, the array will be
    // truncated to one element when the PendingIntent fires.  Thanks guys!
    Log.w("NotificationState", "Pending array off intent length: " +
            intent.getLongArrayExtra("thread_ids").length);

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }
}
