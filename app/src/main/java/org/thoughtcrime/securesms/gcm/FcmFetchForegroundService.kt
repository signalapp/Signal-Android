package org.thoughtcrime.securesms.gcm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds

/**
 * Works with {@link FcmFetchManager} to exists as a service that will keep the app process running in the foreground while we fetch messages.
 */
class FcmFetchForegroundService : Service() {

  companion object {
    private val TAG = Log.tag(FcmFetchForegroundService::class.java)
    private const val KEY_STOP_SELF = "stop_self"

    /**
     * Android's requirement for calling [startForeground] is enforced _even if your service was stopped before it started_.
     * That means we can't just stop it normally, since we don't know if it got to start yet.
     * The safest thing to do is to just tell it to start so it can call [startForeground] and then stop itself.
     * Fun.
     */
    fun buildStopIntent(context: Context): Intent {
      return Intent(context, FcmFetchForegroundService::class.java).apply {
        putExtra(KEY_STOP_SELF, true)
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(
      NotificationIds.FCM_FETCH,
      NotificationCompat.Builder(this, NotificationChannels.getInstance().OTHER)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.BackgroundMessageRetriever_checking_for_messages))
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setProgress(0, 0, true)
        .setContentIntent(PendingIntent.getActivity(this, 0, MainActivity.clearTop(this), PendingIntentFlags.mutable()))
        .setVibrate(longArrayOf(0))
        .build()
    )

    return if (intent != null && intent.getBooleanExtra(KEY_STOP_SELF, false)) {
      stopForeground(true)
      stopSelf()
      START_NOT_STICKY
    } else {
      START_STICKY
    }
  }

  override fun onDestroy() {
    Log.i(TAG, "onDestroy()")
  }

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }
}
