package org.thoughtcrime.securesms.notifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.notifications.v2.DefaultMessageNotifier;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.signal.core.util.ServiceUtil;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Consolidates Notification Cancellation logic to one class.
 *
 * Because Bubbles are tied to Notifications, and disappear when those Notificaitons are cancelled,
 * we want to be very surgical about what notifications we dismiss and when. Behaviour on API levels
 * previous to {@link org.thoughtcrime.securesms.util.ConversationUtil#CONVERSATION_SUPPORT_VERSION}
 * is preserved.
 *
 */
public final class NotificationCancellationHelper {

  private static final String TAG = Log.tag(NotificationCancellationHelper.class);

  private NotificationCancellationHelper() {}

  public static void cancelAllMessageNotifications(@NonNull Context context) {
    cancelAllMessageNotifications(context, Collections.emptySet());
  }

  /**
   * Cancels all Message-Based notifications. Specifically, this is any notification that is not the
   * summary notification assigned to the {@link DefaultMessageNotifier#NOTIFICATION_GROUP} group.
   *
   * We utilize our wrapped cancellation methods and a counter to make sure that we do not lose
   * bubble notifications that do not have unread messages in them.
   */
  public static void cancelAllMessageNotifications(@NonNull Context context, @NonNull Set<Integer> stickyNotifications) {
    try {
      NotificationManager     notifications       = ServiceUtil.getNotificationManager(context);
      StatusBarNotification[] activeNotifications = notifications.getActiveNotifications();
      int                     activeCount         = 0;

      for (StatusBarNotification activeNotification : activeNotifications) {
        if (isSingleThreadNotification(activeNotification)) {
          activeCount++;
          if (!stickyNotifications.contains(activeNotification.getId()) && cancel(context, activeNotification.getId())) {
            activeCount--;
          }
        }
      }

      if (activeCount == 0) {
        cancelLegacy(context, NotificationIds.MESSAGE_SUMMARY);
      }
    } catch (Throwable e) {
      // XXX Appears to be a ROM bug, see #6043
      Log.w(TAG, "Canceling all notifications.", e);
      ServiceUtil.getNotificationManager(context).cancelAll();
    }
  }

  public static void cancelMessageSummaryIfSoleNotification(@NonNull Context context) {
    if (Build.VERSION.SDK_INT > 23) {
      try {
        NotificationManager     notifications       = ServiceUtil.getNotificationManager(context);
        StatusBarNotification[] activeNotifications = notifications.getActiveNotifications();
        boolean                 soleMessageSummary  = false;

        for (StatusBarNotification activeNotification : activeNotifications) {
          if (isSingleThreadNotification(activeNotification)) {
            soleMessageSummary = false;
            break;
          } else if (activeNotification.getId() == NotificationIds.MESSAGE_SUMMARY) {
            soleMessageSummary = true;
          }
        }

        if (soleMessageSummary) {
          Log.d(TAG, "Cancelling sole message summary");
          cancelLegacy(context, NotificationIds.MESSAGE_SUMMARY);
        }
      } catch (Throwable e) {
        Log.w(TAG, e);
      }
    }
  }

  /**
   * @return whether this is a non-summary notification that is a member of the NOTIFICATION_GROUP group.
   */
  private static boolean isSingleThreadNotification(@NonNull StatusBarNotification statusBarNotification) {
    return statusBarNotification.getId() != NotificationIds.MESSAGE_SUMMARY &&
           Objects.equals(statusBarNotification.getNotification().getGroup(), DefaultMessageNotifier.NOTIFICATION_GROUP);
  }

  /**
   * Attempts to cancel the given notification. If the notification is backing an active bubble, we
   * suppress it from the shade instead.
   *
   * @return Whether or not the notification is considered cancelled.
   */
  public static boolean cancel(@NonNull Context context, int notificationId) {
    Log.d(TAG, "cancel() called with: notificationId = [" + notificationId + "]");
    if (Build.VERSION.SDK_INT >= ConversationUtil.CONVERSATION_SUPPORT_VERSION) {
      return cancelWithConversationSupport(context, notificationId);
    } else {
      cancelLegacy(context, notificationId);
      return true;
    }
  }

  /**
   * Bypasses bubble check.
   */
  public static void cancelLegacy(@NonNull Context context, int notificationId) {
    Log.d(TAG, "cancelLegacy() called with: notificationId = [" + notificationId + "]");
    ServiceUtil.getNotificationManager(context).cancel(notificationId);
  }

  /**
   * Cancel method which first checks whether the notification in question is tied to an active bubble.
   *
   * @return true if the notification was cancelled.
   */
  @RequiresApi(ConversationUtil.CONVERSATION_SUPPORT_VERSION)
  private static boolean cancelWithConversationSupport(@NonNull Context context, int notificationId) {
    Log.d(TAG, "cancelWithConversationSupport() called with: notificationId = [" + notificationId + "]");
    if (isCancellable(context, notificationId)) {
      cancelLegacy(context, notificationId);
      return true;
    } else {
      return false;
    }
  }

  /**
   * Cancelling a notification tears down the bubble it backs, so we hold off while it is showing as a
   * bubble, even if it is collapsed.
   */
  @RequiresApi(ConversationUtil.CONVERSATION_SUPPORT_VERSION)
  private static boolean isCancellable(@NonNull Context context, int notificationId) {
    NotificationManager     manager       = ServiceUtil.getNotificationManager(context);
    StatusBarNotification[] notifications = manager.getActiveNotifications();
    Notification            notification  = Stream.of(notifications)
                                                  .filter(n -> n.getId() == notificationId)
                                                  .findFirst()
                                                  .map(StatusBarNotification::getNotification)
                                                  .orElse(null);

    if (notification == null                     ||
        notification.getShortcutId() == null     ||
        notification.getBubbleMetadata() == null) {
      Log.d(TAG, "isCancellable: bubbles not available or notification does not exist");
      return true;
    }

    RecipientId recipientId = ConversationUtil.getRecipientId(notification.getShortcutId());
    if (recipientId == null) {
      Log.d(TAG, "isCancellable: Unable to get recipient from shortcut id");
      return true;
    }

    Long threadId = SignalDatabase.threads().getThreadIdFor(recipientId);

    if (isThread(AppDependencies.getMessageNotifier().getVisibleThread().orElse(null), threadId)) {
      Log.d(TAG, "isCancellable: user entered full screen thread.");
      return true;
    }

    boolean showingAsBubble = (notification.flags & Notification.FLAG_BUBBLE) != 0;
    boolean hasActiveBubble = AppDependencies.getMessageNotifier().getActiveBubbleThreads().stream().anyMatch(bubbleThread -> isThread(bubbleThread, threadId));

    if (showingAsBubble || hasActiveBubble) {
      Log.d(TAG, "isCancellable: bubble is active (flag: " + showingAsBubble + ", opened: " + hasActiveBubble + "), suppressing instead.");
      suppressNotification(context, notificationId, notification);
      return false;
    }

    return true;
  }

  private static boolean isThread(@Nullable ConversationId conversationId, @Nullable Long threadId) {
    return conversationId != null && conversationId.getGroupStoryId() == null && Objects.equals(threadId, conversationId.getThreadId());
  }

  /**
   * Re-posts the notification with its shade entry suppressed, which keeps the bubble it backs.
   */
  @RequiresApi(ConversationUtil.CONVERSATION_SUPPORT_VERSION)
  private static void suppressNotification(@NonNull Context context, int notificationId, @NonNull Notification notification) {
    Notification.BubbleMetadata bubbleMetadata = notification.getBubbleMetadata();

    if (bubbleMetadata == null || bubbleMetadata.isNotificationSuppressed() || bubbleMetadata.getIntent() == null) {
      return;
    }

    Notification.BubbleMetadata suppressedMetadata = new Notification.BubbleMetadata.Builder(bubbleMetadata.getIntent(), bubbleMetadata.getIcon())
                                                                                     .setDesiredHeight(bubbleMetadata.getDesiredHeight())
                                                                                     .setSuppressNotification(true)
                                                                                     .build();

    Notification suppressed = Notification.Builder.recoverBuilder(context, notification)
                                                  .setBubbleMetadata(suppressedMetadata)
                                                  .setOnlyAlertOnce(true)
                                                  .build();

    try {
      ServiceUtil.getNotificationManager(context).notify(notificationId, suppressed);
    } catch (SecurityException e) {
      Log.w(TAG, "Unable to suppress bubble notification", e);
    }
  }
}
