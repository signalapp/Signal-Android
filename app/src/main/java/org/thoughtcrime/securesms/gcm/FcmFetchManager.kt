package org.thoughtcrime.securesms.gcm

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.signal.core.util.PendingIntentFlags.mutable
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MessageFetchJob
import org.thoughtcrime.securesms.messages.WebSocketDrainer
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor
import kotlin.time.Duration.Companion.minutes

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

  private val KEEP_ALIVE_TOKEN = "FcmFetch"

  val WEBSOCKET_DRAIN_TIMEOUT: Long
    get() {
      return if (AppDependencies.signalServiceNetworkAccess.isCensored()) {
        2.minutes.inWholeMilliseconds
      } else {
        5.minutes.inWholeMilliseconds
      }
    }

  @Volatile
  private var activeCount = 0

  @Volatile
  private var highPriority = false

  @JvmStatic
  fun startBackgroundService(context: Context) {
    Log.i(TAG, "Starting in the background.")
    context.startService(Intent(context, FcmFetchBackgroundService::class.java))
    SignalLocalMetrics.FcmServiceStartSuccess.onFcmStarted()
  }

  @JvmStatic
  fun startForegroundService(context: Context) {
    Log.i(TAG, "Starting in the foreground.")
    if (FcmFetchForegroundService.startServiceIfNecessary(context)) {
      SignalLocalMetrics.FcmServiceStartSuccess.onFcmStarted()
    } else {
      SignalLocalMetrics.FcmServiceStartFailure.onFcmFailedToStart()
    }
  }

  private fun postMayHaveMessagesNotification(context: Context) {
    if (RemoteConfig.fcmMayHaveMessagesNotificationKillSwitch) {
      Log.w(TAG, "May have messages notification kill switch")
      return
    }

    Log.w(TAG, "Notifying the user that they may have new messages.")

    val mayHaveMessagesNotification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().ADDITIONAL_MESSAGE_NOTIFICATIONS)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.getString(R.string.FcmFetchManager__you_may_have_messages))
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setContentIntent(PendingIntent.getActivity(context, 0, MainActivity.clearTop(context), mutable()))
      .setVibrate(longArrayOf(0))
      .setOnlyAlertOnce(true)
      .build()

    NotificationManagerCompat.from(context)
      .notify(NotificationIds.MAY_HAVE_MESSAGES_NOTIFICATION_ID, mayHaveMessagesNotification)
  }

  private fun cancelMayHaveMessagesNotification(context: Context) {
    NotificationManagerCompat.from(context).cancel(NotificationIds.MAY_HAVE_MESSAGES_NOTIFICATION_ID)
  }

  private fun fetch(context: Context) {
    val hasHighPriorityContext = highPriority

    val metricId = SignalLocalMetrics.PushWebsocketFetch.startFetch()
    val success = retrieveMessages(context)
    if (success) {
      SignalLocalMetrics.PushWebsocketFetch.onDrained(metricId)
      cancelMayHaveMessagesNotification(context)
    } else {
      SignalLocalMetrics.PushWebsocketFetch.onTimedOut(metricId)
      if (hasHighPriorityContext) {
        postMayHaveMessagesNotification(context)
      }
    }

    synchronized(this) {
      activeCount--

      if (activeCount <= 0) {
        Log.i(TAG, "No more active. Stopping.")
        context.stopService(Intent(context, FcmFetchBackgroundService::class.java))
        FcmFetchForegroundService.stopServiceIfNecessary(context)
        highPriority = false
      }
    }
  }

  @JvmStatic
  fun onForeground(context: Context) {
    cancelMayHaveMessagesNotification(context)
  }

  @JvmStatic
  fun enqueueFetch(context: Context, highPriority: Boolean) {
    synchronized(this) {
      if (highPriority) {
        this.highPriority = true
      }
      val performedReplace = EXECUTOR.enqueue { fetch(context) }
      if (performedReplace) {
        Log.i(TAG, "Already have one running and one enqueued. Ignoring.")
      } else {
        activeCount++
        Log.i(TAG, "Incrementing active count to $activeCount")
      }
    }
  }

  @JvmStatic
  fun retrieveMessages(context: Context): Boolean {
    val success = WebSocketDrainer.blockUntilDrainedAndProcessed(WEBSOCKET_DRAIN_TIMEOUT, KEEP_ALIVE_TOKEN)

    if (success) {
      Log.i(TAG, "Successfully retrieved messages.")
    } else {
      if (Build.VERSION.SDK_INT >= 26) {
        Log.w(TAG, "[API ${Build.VERSION.SDK_INT}] Failed to retrieve messages. Scheduling on the system JobScheduler (API " + Build.VERSION.SDK_INT + ").")
        FcmJobService.schedule(context)
      } else {
        Log.w(TAG, "[API ${Build.VERSION.SDK_INT}] Failed to retrieve messages. Scheduling on JobManager (API " + Build.VERSION.SDK_INT + ").")
        AppDependencies.jobManager.add(MessageFetchJob())
      }
    }

    return success
  }
}
