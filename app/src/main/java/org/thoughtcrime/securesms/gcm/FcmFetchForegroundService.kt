package org.thoughtcrime.securesms.gcm

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds

/**
 * Works with {@link FcmFetchManager} to exists as a service that will keep the app process running in the foreground while we fetch messages.
 */
class FcmFetchForegroundService : Service() {

  private val TAG = Log.tag(FcmFetchForegroundService::class.java)

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(
      NotificationIds.FCM_FETCH,
      NotificationCompat.Builder(this, NotificationChannels.OTHER)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.BackgroundMessageRetriever_checking_for_messages))
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setProgress(0, 0, true)
        .setContentIntent(PendingIntent.getActivity(this, 0, MainActivity.clearTop(this), 0))
        .setVibrate(longArrayOf(0))
        .build()
    )

    return START_STICKY
  }

  override fun onDestroy() {
    Log.i(TAG, "onDestroy()")
  }

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }
}
