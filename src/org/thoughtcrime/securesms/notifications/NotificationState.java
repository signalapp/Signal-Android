package org.thoughtcrime.securesms.notifications;

import android.graphics.Bitmap;

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
    return notifications.get(0).getRecipients().getPrimaryRecipient().getContactPhoto();
  }

}
