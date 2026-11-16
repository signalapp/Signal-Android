package org.thoughtcrime.securesms.notifications

import org.thoughtcrime.securesms.notifications.v2.ConversationId

/**
 * Single-use ids count up by 10 and stay below the first ranged id.
 *
 * Ranged ids are a base plus a database id, built by the `getNotificationIdFor*` functions. For example, thread ranges are offset by
 * thread id, story ranges by the parent story's message id.
 *
 * [NotificationIdsTest] enforces this layout and must be updated when a range is added.
 */
object NotificationIds {
  const val MESSAGE_SUMMARY: Int = 10
  const val KEY_CACHING_SERVICE: Int = 20
  const val INCOMING_MESSAGE_OBSERVER: Int = 30
  const val GENERIC_FOREGROUND_SERVICE: Int = 40
  const val FCM_FETCH: Int = 50
  const val MAY_HAVE_MESSAGES_NOTIFICATION_ID: Int = 60
  const val WEBRTC_CALL: Int = 70
  const val WEBRTC_CALL_RINGING: Int = 71
  const val VOICE_NOTE_NOW_PLAYING: Int = 80
  const val ATTACHMENT_PROGRESS: Int = 90
  const val BACKUP_PROGRESS: Int = 100
  const val BACKUP_FILE_IO_ERROR: Int = 110
  const val INITIAL_BACKUP_FAILED: Int = 120
  const val OUT_OF_REMOTE_STORAGE: Int = 130
  const val INTERNAL_ERROR: Int = 140
  const val RECONCILIATION_ERROR: Int = 141
  const val APK_UPDATE_PROMPT_INSTALL: Int = 150
  const val APK_UPDATE_FAILED_INSTALL: Int = 151
  const val APK_UPDATE_SUCCESSFUL_INSTALL: Int = 152
  const val DEVICE_TRANSFER: Int = 160
  const val NEW_LINKED_DEVICE: Int = 170
  const val UNREGISTERED_NOTIFICATION_ID: Int = 180
  const val USER_NOTIFICATION_MIGRATION: Int = 190
  const val DONOR_BADGE_FAILURE: Int = 200

  const val THREAD: Int = 10000000
  const val MESSAGE_DELIVERY_FAILURE: Int = 20000000
  const val UNREAD_REMINDER: Int = 30000000
  const val STORY_THREAD: Int = 100000000
  const val STORY_MESSAGE_DELIVERY_FAILURE: Int = 1000000000

  @JvmStatic
  fun getNotificationIdForThread(conversationId: ConversationId): Int {
    return if (conversationId.groupStoryId != null) {
      STORY_THREAD + conversationId.groupStoryId.toInt()
    } else {
      THREAD + conversationId.threadId.toInt()
    }
  }

  fun getNotificationIdForMessageDeliveryFailed(conversationId: ConversationId): Int {
    return if (conversationId.groupStoryId != null) {
      STORY_MESSAGE_DELIVERY_FAILURE + conversationId.groupStoryId.toInt()
    } else {
      MESSAGE_DELIVERY_FAILURE + conversationId.threadId.toInt()
    }
  }

  fun getNotificationIdForUnreadReminder(threadId: Long): Int {
    return UNREAD_REMINDER + threadId.toInt()
  }
}
