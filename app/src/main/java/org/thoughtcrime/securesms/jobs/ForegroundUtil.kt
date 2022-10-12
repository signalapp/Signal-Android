package org.thoughtcrime.securesms.jobs

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.service.GenericForegroundService
import org.thoughtcrime.securesms.service.NotificationController
import org.thoughtcrime.securesms.util.ServiceUtil
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Helps start foreground services from the background.
 */
object ForegroundUtil {

  private val TAG = Log.tag(ForegroundUtil::class.java)

  private val updateMutex: ReentrantLock = ReentrantLock()
  private var activeLatch: CountDownLatch? = null

  @Throws(GenericForegroundService.UnableToStartException::class)
  @JvmStatic
  fun requireForegroundTask(context: Context, task: String): NotificationController {
    val alarmManager = ServiceUtil.getAlarmManager(context)

    if (Build.VERSION.SDK_INT < 31 || ApplicationDependencies.getAppForegroundObserver().isForegrounded || !alarmManager.canScheduleExactAlarms()) {
      return GenericForegroundService.startForegroundTask(context, task)
    }

    val latch: CountDownLatch? = updateMutex.withLock {
      if (activeLatch == null) {
        if (alarmManager.canScheduleExactAlarms()) {
          activeLatch = CountDownLatch(1)
          val pendingIntent = PendingIntent.getBroadcast(context, 0, Intent(context, Receiver::class.java), PendingIntentFlags.mutable())
          alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1000, pendingIntent)
        } else {
          Log.w(TAG, "Unable to schedule alarm")
        }
      }
      activeLatch
    }

    if (latch != null) {
      try {
        if (!latch.await(1, TimeUnit.MINUTES)) {
          Log.w(TAG, "Time ran out waiting for foreground")
        }
      } catch (e: InterruptedException) {
        Log.w(TAG, "Interrupted while waiting for foreground")
      }
    }

    return GenericForegroundService.startForegroundTask(context, task)
  }

  class Receiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      updateMutex.withLock {
        activeLatch?.countDown()
        activeLatch = null
      }
    }
  }
}
