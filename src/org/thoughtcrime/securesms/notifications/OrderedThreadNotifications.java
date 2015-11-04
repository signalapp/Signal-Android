package org.thoughtcrime.securesms.notifications;

import java.util.LinkedHashSet;
import java.util.Map;

public class OrderedThreadNotifications {

    private final LinkedHashSet<Long> orderedThreads;
    private final Map<Long, NotificationState> threadNotificationMapping;

    public OrderedThreadNotifications(LinkedHashSet<Long> orderedThreads, Map<Long, NotificationState> threadNotificationMapping) {
        this.orderedThreads = orderedThreads;
        this.threadNotificationMapping = threadNotificationMapping;
    }

    public LinkedHashSet<Long> getOrderedThreads() {
        return orderedThreads;
    }

    public NotificationState getNotificationState(Long threadId) {
        return threadNotificationMapping.get(threadId);
    }
}