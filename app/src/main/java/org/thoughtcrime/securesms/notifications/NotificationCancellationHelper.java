package org.thoughtcrime.securesms.notifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
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
import java.util.Optional;
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
   * Attempts to cancel the given notification. Notifications tied to an expanded bubble are left
   * untouched; notifications tied to a collapsed bubble are suppressed rather than cancelled, so
   * the bubble itself is preserved.
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
   * Cancel method which first checks whether the notification in question is tied to a bubble that
   * may or may not be displayed by the user.
   *
   * Cancelling a notification also dismisses its bubble, so collapsed bubble notifications are
   * re-posted with suppression instead: removed from the shade and badge cleared, bubble kept.
   *
   * @return true if the notification was cancelled. Bubble notifications are reported as not
   * cancelled so the summary stays alive; cancelling the summary can cancel its bubbled children.
   */
  @RequiresApi(ConversationUtil.CONVERSATION_SUPPORT_VERSION)
  private static boolean cancelWithConversationSupport(@NonNull Context context, int notificationId) {
    Log.d(TAG, "cancelWithConversationSupport() called with: notificationId = [" + notificationId + "]");

    NotificationManager     manager       = ServiceUtil.getNotificationManager(context);
    StatusBarNotification[] notifications = manager.getActiveNotifications();
    Notification            notification  = Stream.of(notifications)
                                                  .filter(n -> n.getId() == notificationId)
                                                  .findFirst()
                                                  .map(StatusBarNotification::getNotification)
                                                  .orElse(null);

    if (notification == null                 ||
        notification.getShortcutId() == null ||
        notification.getBubbleMetadata() == null)
    {
      Log.d(TAG, "cancelWithConversationSupport: bubbles not available or notification does not exist, cancelling.");
      cancelLegacy(context, notificationId);
      return true;
    }

    RecipientId recipientId = ConversationUtil.getRecipientId(notification.getShortcutId());
    if (recipientId == null) {
      Log.d(TAG, "cancelWithConversationSupport: unable to get recipient from shortcut id, cancelling.");
      cancelLegacy(context, notificationId);
      return true;
    }

    Long                     threadId            = SignalDatabase.threads().getThreadIdFor(recipientId);
    Optional<ConversationId> focusedThread       = AppDependencies.getMessageNotifier().getVisibleThread();
    Long                     focusedThreadId     = focusedThread.map(ConversationId::getThreadId).orElse(null);
    Long                     focusedGroupStoryId = focusedThread.map(ConversationId::getGroupStoryId).orElse(null);

    if (Objects.equals(threadId, focusedThreadId) && focusedGroupStoryId == null) {
      Log.d(TAG, "cancelWithConversationSupport: user entered full screen thread, cancelling.");
      cancelLegacy(context, notificationId);
      return true;
    }

    ConversationId activeBubble         = AppDependencies.getMessageNotifier().getVisibleBubbleThread();
    Long           activeBubbleThreadId = activeBubble != null ? activeBubble.getThreadId() : null;

    if (Objects.equals(threadId, activeBubbleThreadId)) {
      Log.d(TAG, "cancelWithConversationSupport: bubble is currently expanded, not cancelling.");
      return false;
    }

    if ((notification.flags & Notification.FLAG_BUBBLE) == 0) {
      // No active bubble to preserve; suppression only has an effect while bubbled.
      Log.d(TAG, "cancelWithConversationSupport: not currently bubbled, cancelling.");
      cancelLegacy(context, notificationId);
      return true;
    }

    if (notification.getBubbleMetadata().isNotificationSuppressed()) {
      Log.d(TAG, "cancelWithConversationSupport: bubble notification already suppressed.");
      return false;
    }

    if (Build.VERSION.SDK_INT < 31) {
      // Background suppression has no effect on API 30, preserve previous behaviour.
      Log.d(TAG, "cancelWithConversationSupport: background suppression unsupported on API 30, not cancelling.");
      return false;
    }

    Log.d(TAG, "cancelWithConversationSupport: suppressing collapsed bubble notification.");
    suppressNotification(context, notificationId, notification);
    return false;
  }

  /**
   * Re-posts the notification with suppression flags set: removed from the shade, bubble kept.
   * Mirrors the system-handled swipe-dismiss of a bubbled notification. The original metadata is
   * cloned with only the presentation flags changed.
   */
  @RequiresApi(31)
  private static void suppressNotification(@NonNull Context context, int notificationId, @NonNull Notification notification) {
    Notification.BubbleMetadata original = notification.getBubbleMetadata();
    if (original == null) {
      Log.w(TAG, "suppressNotification: no bubble metadata, skipping.");
      return;
    }

    Notification.BubbleMetadata.Builder bubbleBuilder;
    if (original.getIntent() != null && original.getIcon() != null) {
      bubbleBuilder = new Notification.BubbleMetadata.Builder(original.getIntent(), original.getIcon());
    } else if (original.getShortcutId() != null) {
      bubbleBuilder = new Notification.BubbleMetadata.Builder(original.getShortcutId());
    } else {
      Log.w(TAG, "suppressNotification: bubble metadata incomplete, skipping.");
      return;
    }

    Notification.BubbleMetadata bubbleMetadata = bubbleBuilder.setAutoExpandBubble(false)
                                                              .setSuppressNotification(true)
                                                              .setDesiredHeight(original.getDesiredHeight())
                                                              .setDeleteIntent(original.getDeleteIntent())
                                                              .build();

    Notification.Builder builder = Notification.Builder.recoverBuilder(context, notification)
                                                       .setBubbleMetadata(bubbleMetadata)
                                                       .setOnlyAlertOnce(true);

    ServiceUtil.getNotificationManager(context).notify(notificationId, builder.build());
  }
}
