package org.thoughtcrime.securesms.notifications;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.notifications.v2.ConversationId;

public final class NotificationIds {

  public static final int FCM_FAILURE                       = 12;
  public static final int ATTACHMENT_PROGRESS               = 50;
  public static final int BACKUP_PROGRESS                   = 51;
  public static final int APK_UPDATE_PROMPT_INSTALL         = 666;
  public static final int APK_UPDATE_FAILED_INSTALL         = 667;
  public static final int APK_UPDATE_SUCCESSFUL_INSTALL     = 668;
  public static final int PENDING_MESSAGES                  = 1111;
  public static final int MESSAGE_SUMMARY                   = 1338;
  public static final int APPLICATION_MIGRATION             = 4242;
  public static final int SMS_IMPORT_COMPLETE               = 31337;
  public static final int MAY_HAVE_MESSAGES_NOTIFICATION_ID = 31365;
  public static final int PRE_REGISTRATION_SMS              = 5050;
  public static final int THREAD                            = 50000;
  public static final int MAX_THREAD                        = THREAD + 100_000;
  public static final int INTERNAL_ERROR                    = 258069;
  public static final int RECONCILIATION_ERROR              = 258070;
  public static final int LEGACY_SQLCIPHER_MIGRATION        = 494949;
  public static final int USER_NOTIFICATION_MIGRATION       = 525600;
  public static final int DEVICE_TRANSFER                   = 625420;
  public static final int DONOR_BADGE_FAILURE               = 630001;
  public static final int FCM_FETCH                         = 630002;
  public static final int SMS_EXPORT_SERVICE                = 630003;
  public static final int SMS_EXPORT_COMPLETE               = 630004;
  public static final int STORY_THREAD                      = 700000;
  public static final int MAX_STORY_THREAD                  = STORY_THREAD + 100_000;
  public static final int MESSAGE_DELIVERY_FAILURE          = 800000;
  public static final int STORY_MESSAGE_DELIVERY_FAILURE    = 900000;
  public static final int UNREGISTERED_NOTIFICATION_ID      = 20230102;
  public static final int NEW_LINKED_DEVICE                 = 120400;
  public static final int OUT_OF_REMOTE_STORAGE             = 120500;
  public static final int INITIAL_BACKUP_FAILED             = 120501;
  public static final int MANUAL_BACKUP_NOT_CREATED         = 120502;

  private NotificationIds() { }

  public static int getNotificationIdForThread(@NonNull ConversationId conversationId) {
    if (conversationId.getGroupStoryId() != null) {
      return STORY_THREAD + conversationId.getGroupStoryId().intValue();
    } else {
      return THREAD + (int) conversationId.getThreadId();
    }
  }

  public static int getNotificationIdForMessageDeliveryFailed(@NonNull ConversationId conversationId) {
    if (conversationId.getGroupStoryId() != null) {
      return STORY_MESSAGE_DELIVERY_FAILURE + conversationId.getGroupStoryId().intValue();
    } else {
      return MESSAGE_DELIVERY_FAILURE + (int) conversationId.getThreadId();
    }
  }

  public static boolean isMessageNotificationId(int id) {
    return (id >= THREAD && id < (MAX_THREAD)) || (id >= STORY_THREAD && id < MAX_STORY_THREAD);
  }
}
