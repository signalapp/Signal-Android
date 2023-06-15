package org.thoughtcrime.securesms.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.NotificationIds;
import org.thoughtcrime.securesms.notifications.v2.NotificationFactory;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import static org.thoughtcrime.securesms.util.ConversationUtil.CONVERSATION_SUPPORT_VERSION;

/**
 * Bubble-related utility methods.
 */
public final class BubbleUtil {

  private static final String TAG = Log.tag(BubbleUtil.class);

  private BubbleUtil() {
  }

  /**
   * Checks whether we are allowed to create a bubble for the given recipient.
   *
   * In order to Bubble, a recipient must have a thread, be unblocked, and the user must not have
   * notification privacy settings enabled. Furthermore, we check the Notifications system to verify
   * that bubbles are allowed in the first place.
   */
  @RequiresApi(CONVERSATION_SUPPORT_VERSION)
  @WorkerThread
  public static boolean canBubble(@NonNull Context context, @NonNull RecipientId recipientId, @Nullable Long threadId) {
    if (threadId == null) {
      Log.d(TAG, "Cannot bubble recipient without thread");
      return false;
    }

    NotificationPrivacyPreference privacyPreference = SignalStore.settings().getMessageNotificationsPrivacy();
    if (!privacyPreference.isDisplayContact()) {
      Log.d(TAG, "Bubbles are not available when notification privacy settings are enabled.");
      return false;
    }

    Recipient recipient = Recipient.resolved(recipientId);
    if (recipient.isBlocked()) {
      Log.d(TAG, "Cannot bubble blocked recipient");
      return false;
    }

    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    NotificationChannel conversationChannel = notificationManager.getNotificationChannel(ConversationUtil.getChannelId(context, recipient),
                                                                                         ConversationUtil.getShortcutId(recipientId));

    if (conversationChannel == null) {
      Log.d(TAG, "Conversation channel was null, therefore no bubbles.");
      return false;
    }

    if (!conversationChannel.canBubble()) {
      Log.d(TAG, "Conversation channel does not allow bubbles.");
      return false;
    }

    if (Build.VERSION.SDK_INT < 31) {
      if (!notificationManager.areBubblesAllowed()) {
        Log.d(TAG, "Notification Manager does not allow bubbles.");
        return false;
      }
    } else {
      if (!notificationManager.areBubblesEnabled()) {
        Log.d(TAG, "Notification Manager disabled bubbles.");
        return false;
      }

      if (notificationManager.getBubblePreference() == NotificationManager.BUBBLE_PREFERENCE_NONE) {
        Log.d(TAG, "Bubble preference in Notification Manager was none, therefore no bubbles.");
        return false;
      }
    }
    
    return true;
  }

  /**
   * Display a bubble for a given recipient's thread.
   */
  public static void displayAsBubble(@NonNull Context context, @NonNull RecipientId recipientId, long threadId) {
    if (Build.VERSION.SDK_INT >= CONVERSATION_SUPPORT_VERSION) {
      ConversationId conversationId = ConversationId.forConversation(threadId);
      SignalExecutors.BOUNDED.execute(() -> {
        if (canBubble(context, recipientId, threadId)) {
          NotificationManager     notificationManager      = ServiceUtil.getNotificationManager(context);
          StatusBarNotification[] notifications            = notificationManager.getActiveNotifications();
          int                     threadNotificationId     = NotificationIds.getNotificationIdForThread(conversationId);
          Notification            activeThreadNotification = Stream.of(notifications)
                                                                   .filter(n -> n.getId() == threadNotificationId)
                                                                   .findFirst()
                                                                   .map(StatusBarNotification::getNotification)
                                                                   .orElse(null);

          if (activeThreadNotification != null && activeThreadNotification.deleteIntent != null) {
            ApplicationDependencies.getMessageNotifier().updateNotification(context, conversationId, BubbleState.SHOWN);
          } else {
            Recipient recipient = Recipient.resolved(recipientId);
            NotificationFactory.notifyToBubbleConversation(context, recipient, threadId);
          }
        }
      });
    }
  }

  public enum BubbleState {
    SHOWN,
    HIDDEN
  }
}
