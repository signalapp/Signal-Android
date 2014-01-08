package org.thoughtcrime.securesms.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

import org.whispersystems.textsecure.crypto.MasterSecret;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class NotificationState {

  private final LinkedList<NotificationItem> notifications = new LinkedList<NotificationItem>();
  private final Set<Long>                    threads       = new HashSet<Long>();

  private int notificationCount = 0;

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

  public PendingIntent getMarkAsReadIntent(Context context, MasterSecret masterSecret) {
    Intent intent = new Intent(MarkReadReceiver.CLEAR_ACTION);
    intent.putExtra("master_secret", masterSecret);
    intent.setPackage(context.getPackageName());

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }
}
