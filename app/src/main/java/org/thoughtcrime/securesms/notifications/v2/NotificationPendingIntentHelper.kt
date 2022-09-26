package org.thoughtcrime.securesms.notifications.v2

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.signal.core.util.logging.Log

/**
 * Wrapper for creating pending intents that catches security exceptions thrown by Xiaomi devices randomly.
 */
object NotificationPendingIntentHelper {
  private val TAG = Log.tag(NotificationPendingIntentHelper::class.java)

  fun getBroadcast(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent? {
    return try {
      PendingIntent.getBroadcast(context, requestCode, intent, flags)
    } catch (e: SecurityException) {
      Log.w(TAG, "Too many pending intents device quirk: ${e.message}")
      null
    }
  }

  fun getActivity(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent? {
    return try {
      PendingIntent.getActivity(context, requestCode, intent, flags)
    } catch (e: SecurityException) {
      Log.w(TAG, "Too many pending intents device quirk: ${e.message}")
      null
    }
  }
}
