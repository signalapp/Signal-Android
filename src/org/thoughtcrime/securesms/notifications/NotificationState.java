package org.thoughtcrime.securesms.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.notifications.MessageNotifier.NotificationStateChangeListener;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class NotificationState {

  private final LinkedList<NotificationItem> notifications = new LinkedList<NotificationItem>();
  private final Set<Long>                    threads       = new HashSet<Long>();

  private final boolean signal;
  private final int     reminderCount;
  private       int     notificationCount = 0;

  private NotificationStateChangeListener stateChangeListener;

  public NotificationState(Context      context,
                           MasterSecret masterSecret,
                           boolean      signal,
                           int          reminderCount)
  {
    this.signal              = signal;
    this.reminderCount       = reminderCount;
    this.stateChangeListener = new NotificationStateChangeListener(context, masterSecret);
  }

  public void addNotification(NotificationItem item) {
    notifications.addFirst(item);
    threads.add(item.getThreadId());
    notificationCount++;
  }

  public boolean hasMultipleThreads() {
    return threads.size() > 1;
  }

  public int getMessageCount() {
    return notificationCount;
  }

  public List<NotificationItem> getNotifications() {
    return notifications;
  }

  public Bitmap getContactPhoto() {
    return notifications.get(0).getIndividualRecipient().getContactPhoto();
  }

  public boolean getSignal() {
    return signal;
  }

  public int getReminderCount() {
    return reminderCount;
  }

  public NotificationStateChangeListener getListener() {
    return stateChangeListener;
  }

  public PendingIntent getMarkAsReadIntent(Context context, MasterSecret masterSecret) {
    long[] threadArray = new long[threads.size()];
    int index          = 0;

    for (long thread : threads) {
      Log.w("NotificationState", "Added thread: " + thread);
      threadArray[index++] = thread;
    }

    Intent intent = new Intent(MarkReadReceiver.CLEAR_ACTION);
    intent.putExtra("thread_ids", threadArray);
    intent.putExtra("master_secret", masterSecret);
    intent.setPackage(context.getPackageName());

    // XXX : This is an Android bug.  If we don't pull off the extra
    // once before handing off the PendingIntent, the array will be
    // truncated to one element when the PendingIntent fires.  Thanks guys!
    Log.w("NotificationState", "Pending array off intent length: " +
        intent.getLongArrayExtra("thread_ids").length);

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }
}
