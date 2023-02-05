package org.thoughtcrime.securesms.notifications;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.notifications.v2.ConversationId;

public final class NotificationIds {

  public static final int FCM_FAILURE                    = 12;
  public static final int PENDING_MESSAGES               = 1111;
  public static final int MESSAGE_SUMMARY                = 1338;
  public static final int APPLICATION_MIGRATION          = 4242;
  public static final int SMS_IMPORT_COMPLETE            = 31337;
  public static final int PRE_REGISTRATION_SMS           = 5050;
  public static final int THREAD                         = 50000;
  public static final int INTERNAL_ERROR                 = 258069;
  public static final int LEGACY_SQLCIPHER_MIGRATION     = 494949;
  public static final int USER_NOTIFICATION_MIGRATION    = 525600;
  public static final int DEVICE_TRANSFER                = 625420;
  public static final int DONOR_BADGE_FAILURE            = 630001;
  public static final int FCM_FETCH                      = 630002;
  public static final int SMS_EXPORT_SERVICE             = 630003;
  public static final int SMS_EXPORT_COMPLETE            = 630004;
  public static final int STORY_THREAD                   = 700000;
  public static final int MESSAGE_DELIVERY_FAILURE       = 800000;
  public static final int STORY_MESSAGE_DELIVERY_FAILURE = 900000;
  public static final int UNREGISTERED_NOTIFICATION_ID   = 20230102;

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
}
