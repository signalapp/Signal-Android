package org.thoughtcrime.securesms.notifications;

public final class NotificationIds {

  public static final int FCM_FAILURE                 = 12;
  public static final int PENDING_MESSAGES            = 1111;
  public static final int MESSAGE_SUMMARY             = 1338;
  public static final int APPLICATION_MIGRATION       = 4242;
  public static final int SMS_IMPORT_COMPLETE         = 31337;
  public static final int PRE_REGISTRATION_SMS        = 5050;
  public static final int THREAD                      = 50000;
  public static final int USER_NOTIFICATION_MIGRATION = 525600;

  private NotificationIds() { }

  public static int getNotificationIdForThread(long threadId) {
    return THREAD + (int) threadId;
  }
}
