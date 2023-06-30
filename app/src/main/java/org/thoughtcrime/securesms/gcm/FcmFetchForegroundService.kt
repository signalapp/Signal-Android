package org.thoughtcrime.securesms.gcm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.WakeLockUtil

/**
 * Works with {@link FcmFetchManager} to exists as a service that will keep the app process running in the foreground while we fetch messages.
 */
class FcmFetchForegroundService : Service() {

  private var wakeLock: PowerManager.WakeLock? = null

  companion object {
    private val TAG = Log.tag(FcmFetchForegroundService::class.java)

    private const val WAKELOCK_TAG = "FcmForegroundService"
    private const val KEY_STOP_SELF = "stop_self"

    private val WAKELOCK_TIMEOUT = FcmFetchManager.WEBSOCKET_DRAIN_TIMEOUT

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

  override fun onCreate() {
    Log.d(TAG, "onCreate()")
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.d(TAG, "onStartCommand()")
    postForegroundNotification()

    return if (intent != null && intent.getBooleanExtra(KEY_STOP_SELF, false)) {
      WakeLockUtil.release(wakeLock, WAKELOCK_TAG)
      stopForeground(true)
      stopSelf()
      START_NOT_STICKY
    } else {
      if (wakeLock == null) {
        wakeLock = WakeLockUtil.acquire(this, PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TIMEOUT, WAKELOCK_TAG)
      }
      START_STICKY
    }
  }

  private fun postForegroundNotification() {
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
  }

  override fun onDestroy() {
    Log.i(TAG, "onDestroy()")
    WakeLockUtil.release(wakeLock, WAKELOCK_TAG)
    FcmFetchManager.onDestroyForegroundFetchService()

    wakeLock = null
  }

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }
}
