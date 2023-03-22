package org.thoughtcrime.securesms.gcm

import android.content.Context
import android.content.Intent
import android.os.Build
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.ForegroundServiceUtil
import org.thoughtcrime.securesms.jobs.PushNotificationReceiveJob
import org.thoughtcrime.securesms.messages.WebSocketStrategy
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor

/**
 * Our goals with FCM processing are as follows:
 * (1) Ensure some service is active for the duration of the fetch and processing stages.
 * (2) Do not make unnecessary network requests.
 *
 * To fulfill goal 1, this class will not stop the services until there is no more running
 * requests.
 *
 * To fulfill goal 2, this class  will not enqueue a fetch if there are already 2 active fetches
 * (or rather, 1 active and 1 waiting, since we use a single thread executor).
 *
 * Unfortunately we can't do this all in [FcmReceiveService] because it won't let us process
 * the next FCM message until [FcmReceiveService.onMessageReceived] returns,
 * but as soon as that method returns, it could also destroy the service. By not letting us control
 * when the service is destroyed, we can't accomplish both goals within that service.
 */
object FcmFetchManager {

  private val TAG = Log.tag(FcmFetchManager::class.java)

  private val EXECUTOR = SerialMonoLifoExecutor(SignalExecutors.UNBOUNDED)

  @Volatile
  private var activeCount = 0

  @Volatile
  private var startedForeground = false

  /**
   * @return True if a service was successfully started, otherwise false.
   */
  @JvmStatic
  fun enqueue(context: Context, foreground: Boolean): Boolean {
    synchronized(this) {
      try {
        if (foreground) {
          Log.i(TAG, "Starting in the foreground.")
          ForegroundServiceUtil.startWhenCapableOrThrow(context, Intent(context, FcmFetchForegroundService::class.java))
          startedForeground = true
        } else {
          Log.i(TAG, "Starting in the background.")
          context.startService(Intent(context, FcmFetchBackgroundService::class.java))
        }

        val performedReplace = EXECUTOR.enqueue { fetch(context) }

        if (performedReplace) {
          Log.i(TAG, "Already have one running and one enqueued. Ignoring.")
        } else {
          activeCount++
          Log.i(TAG, "Incrementing active count to $activeCount")
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to start service!", e)
        return false
      }
    }

    return true
  }

  private fun fetch(context: Context) {
    retrieveMessages(context)

    synchronized(this) {
      activeCount--

      if (activeCount <= 0) {
        Log.i(TAG, "No more active. Stopping.")
        context.stopService(Intent(context, FcmFetchBackgroundService::class.java))

        if (startedForeground) {
          try {
            context.startService(FcmFetchForegroundService.buildStopIntent(context))
          } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to stop the foreground notification!", e)
          }

          startedForeground = false
        }
      }
    }
  }

  @JvmStatic
  fun retrieveMessages(context: Context) {
    val success = ApplicationDependencies.getBackgroundMessageRetriever().retrieveMessages(context, WebSocketStrategy())

    if (success) {
      Log.i(TAG, "Successfully retrieved messages.")
    } else {
      if (Build.VERSION.SDK_INT >= 26) {
        Log.w(TAG, "[API ${Build.VERSION.SDK_INT}] Failed to retrieve messages. Scheduling on the system JobScheduler (API " + Build.VERSION.SDK_INT + ").")
        FcmJobService.schedule(context)
      } else {
        Log.w(TAG, "[API ${Build.VERSION.SDK_INT}] Failed to retrieve messages. Scheduling on JobManager (API " + Build.VERSION.SDK_INT + ").")
        ApplicationDependencies.getJobManager().add(PushNotificationReceiveJob())
      }
    }
  }
}
