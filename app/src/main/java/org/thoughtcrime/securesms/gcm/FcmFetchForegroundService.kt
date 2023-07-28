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
import org.thoughtcrime.securesms.jobs.ForegroundServiceUtil
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
    private const val MAX_BLOCKING_TIME_MS = 500L

    private val WAKELOCK_TIMEOUT = FcmFetchManager.WEBSOCKET_DRAIN_TIMEOUT

    /**
     * Android's requirement for calling [startForeground] is enforced _even if your service was stopped before it started_.
     * That means we can't just stop it normally, since we don't know if it got to start yet.
     * The safest thing to do is to just tell it to start so it can call [startForeground] and then stop itself.
     * Fun.
     */
    private fun buildStopIntent(context: Context): Intent {
      return Intent(context, FcmFetchForegroundService::class.java).apply {
        putExtra(KEY_STOP_SELF, true)
      }
    }

    enum class State {
      STOPPED,
      STARTED,
      STOPPING,
      RESTARTING
    }

    private var foregroundServiceState: State = State.STOPPED

    /**
     * Attempts to start the foreground service if it isn't already running.
     *
     * @return false if we failed to start the foreground service
     */
    fun startServiceIfNecessary(context: Context): Boolean {
      synchronized(this) {
        when (foregroundServiceState) {
          State.STOPPING -> foregroundServiceState = State.RESTARTING
          State.STOPPED -> {
            foregroundServiceState = try {
              startForegroundFetchService(context)
              State.STARTED
            } catch (e: IllegalStateException) {
              Log.e(TAG, "Failed to start foreground service", e)
              State.STOPPED
              return false
            }
          }
          else -> Log.i(TAG, "Already started foreground service")
        }
      }
      return true
    }

    fun stopServiceIfNecessary(context: Context) {
      synchronized(this) {
        when (foregroundServiceState) {
          State.STARTED -> {
            foregroundServiceState = State.STOPPING
            try {
              context.startService(buildStopIntent(context))
            } catch (e: IllegalStateException) {
              Log.w(TAG, "Failed to stop the foreground service, assuming already stopped", e)
              foregroundServiceState = State.STOPPED
            }
          }
          State.RESTARTING -> foregroundServiceState = State.STOPPED
          else -> Log.i(TAG, "No service to stop")
        }
      }
    }

    private fun onServiceDestroyed(context: Context) {
      synchronized(this) {
        Log.i(TAG, "Fcm fetch service destroyed")
        when (foregroundServiceState) {
          State.RESTARTING -> {
            foregroundServiceState = State.STOPPED
            Log.i(TAG, "Restarting service.")
            startServiceIfNecessary(context)
          }
          else -> {
            foregroundServiceState = State.STOPPED
          }
        }
      }
    }

    private fun startForegroundFetchService(context: Context) {
      ForegroundServiceUtil.startWhenCapableOrThrow(context, Intent(context, FcmFetchForegroundService::class.java), MAX_BLOCKING_TIME_MS)
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
    onServiceDestroyed(this)

    wakeLock = null
  }

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }
}
