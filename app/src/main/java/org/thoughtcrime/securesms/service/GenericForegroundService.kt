package org.thoughtcrime.securesms.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.signal.core.util.PendingIntentFlags.mutable
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.jobs.ForegroundServiceUtil
import org.thoughtcrime.securesms.jobs.UnableToStartException
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.whispersystems.signalservice.api.util.Preconditions
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class GenericForegroundService : Service() {
  private val binder: IBinder = LocalBinder()
  private val allActiveMessages = LinkedHashMap<Int, Entry>()
  private val lock = ReentrantLock()

  var hasTimedOut = false
  private var lastPosted: Entry? = null

  companion object {
    private val TAG = Log.tag(GenericForegroundService::class.java)

    private const val NOTIFICATION_ID = 827353982
    private const val EXTRA_TITLE = "extra_title"
    private const val EXTRA_CHANNEL_ID = "extra_channel_id"
    private const val EXTRA_ICON_RES = "extra_icon_res"
    private const val EXTRA_ID = "extra_id"
    private const val EXTRA_PROGRESS = "extra_progress"
    private const val EXTRA_PROGRESS_MAX = "extra_progress_max"
    private const val EXTRA_PROGRESS_INDETERMINATE = "extra_progress_indeterminate"
    private const val ACTION_START = "start"
    private const val ACTION_STOP = "stop"

    private val NEXT_ID = AtomicInteger()
    private val DEFAULT_ENTRY = Entry("", NotificationChannels.getInstance().OTHER, R.drawable.ic_notification, -1, 0, 0, false)

    /**
     * Waits for {@param delayMillis} ms before starting the foreground task.
     *
     *
     * The delayed notification controller can also shown on demand and promoted to a regular notification controller to update the message etc.
     *
     * Do not call this method on API > 31
     */
    @JvmStatic
    fun startForegroundTaskDelayed(context: Context, task: String, delayMillis: Long, @DrawableRes iconRes: Int): DelayedNotificationController {
      Preconditions.checkArgument(Build.VERSION.SDK_INT < 31)
      Log.d(TAG, "[startForegroundTaskDelayed] Task: $task, Delay: $delayMillis")

      return DelayedNotificationController.create(delayMillis) {
        try {
          return@create startForegroundTask(context, task, DEFAULT_ENTRY.channelId, iconRes)
        } catch (e: UnableToStartException) {
          Log.w(TAG, "This should not happen on API < 31", e)
          throw AssertionError(e.cause)
        }
      }
    }

    @JvmStatic
    @JvmOverloads
    @Throws(UnableToStartException::class)
    fun startForegroundTask(
      context: Context,
      task: String,
      channelId: String = DEFAULT_ENTRY.channelId,
      @DrawableRes iconRes: Int = DEFAULT_ENTRY.iconRes
    ): NotificationController {
      val id = NEXT_ID.getAndIncrement()
      Log.i(TAG, "[startForegroundTask] Task: $task, ID: $id")

      val intent = Intent(context, GenericForegroundService::class.java).apply {
        action = ACTION_START
        putExtra(EXTRA_TITLE, task)
        putExtra(EXTRA_CHANNEL_ID, channelId)
        putExtra(EXTRA_ICON_RES, iconRes)
        putExtra(EXTRA_ID, id)
      }

      ForegroundServiceUtil.start(context, intent)

      return NotificationController(context, id)
    }

    @JvmStatic
    @Throws(UnableToStartException::class, IllegalStateException::class)
    fun stopForegroundTask(context: Context, id: Int) {
      Log.d(TAG, "[stopForegroundTask] ID: $id")

      val intent = Intent(context, GenericForegroundService::class.java).apply {
        action = ACTION_STOP
        putExtra(EXTRA_ID, id)
      }

      Log.i(TAG, "Stopping foreground service id=$id")
      ForegroundServiceUtil.startWhenCapable(context, intent)
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    checkNotNull(intent) { "Intent needs to be non-null." }
    Log.d(TAG, "[onStartCommand] Action: ${intent.action}")

    lock.withLock {
      when (val action = intent.action) {
        ACTION_START -> {
          val entry = Entry.fromIntent(intent)
          Log.i(TAG, "[onStartCommand] Adding entry: $entry")
          allActiveMessages[entry.id] = entry
        }

        ACTION_STOP -> {
          val id = intent.getIntExtra(EXTRA_ID, -1)
          val removed = allActiveMessages.remove(id)
          if (removed != null) {
            Log.i(TAG, "[onStartCommand] ID: $id, Removed: $removed")
          } else {
            Log.w(TAG, "[onStartCommand] Could not find entry to remove")
          }
        }

        else -> throw IllegalStateException("Unexpected action: $action")
      }

      updateNotification()

      return START_NOT_STICKY
    }
  }

  override fun onCreate() {
    Log.d(TAG, "[onCreate]")
    super.onCreate()
  }

  override fun onBind(intent: Intent): IBinder {
    Log.d(TAG, "[onBind]")
    return binder
  }

  override fun onUnbind(intent: Intent?): Boolean {
    Log.d(TAG, "[onUnbind]")
    return super.onUnbind(intent)
  }

  override fun onRebind(intent: Intent?) {
    Log.d(TAG, "[onRebind]")
    super.onRebind(intent)
  }

  override fun onDestroy() {
    Log.d(TAG, "[onDestroy]")
    super.onDestroy()
  }

  override fun onLowMemory() {
    Log.d(TAG, "[onLowMemory]")
    super.onLowMemory()
  }

  override fun onTrimMemory(level: Int) {
    Log.d(TAG, "[onTrimMemory] level: $level")
    super.onTrimMemory(level)
  }

  override fun onTimeout(startId: Int, foregroundServiceType: Int) {
    Log.i(TAG, "[onTimeout] startId: $startId, fgsType: $foregroundServiceType")
    stopDueToTimeout()
  }

  private fun stopDueToTimeout() {
    lock.withLock {
      hasTimedOut = true
      allActiveMessages.clear()
    }
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  fun replaceTitle(id: Int, title: String) {
    lock.withLock {
      updateEntry(id) { oldEntry ->
        oldEntry.copy(title = title)
      }
    }
  }

  fun replaceProgress(id: Int, progressMax: Int, progress: Int, indeterminate: Boolean) {
    lock.withLock {
      updateEntry(id) { oldEntry ->
        oldEntry.copy(progressMax = progressMax, progress = progress, indeterminate = indeterminate)
      }
    }
  }

  private fun updateNotification() {
    if (allActiveMessages.isNotEmpty()) {
      postObligatoryForegroundNotification(allActiveMessages.values.first())
    } else {
      Log.i(TAG, "Last request. Ending foreground service.")
      postObligatoryForegroundNotification(lastPosted ?: DEFAULT_ENTRY)

      ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
      stopSelf()
    }
  }

  private fun postObligatoryForegroundNotification(active: Entry) {
    lastPosted = active

    try {
      startForeground(
        NOTIFICATION_ID,
        NotificationCompat.Builder(this, active.channelId)
          .setSmallIcon(active.iconRes)
          .setContentTitle(active.title)
          .setProgress(active.progressMax, active.progress, active.indeterminate)
          .setContentIntent(PendingIntent.getActivity(this, 0, MainActivity.clearTop(this), mutable()))
          .setVibrate(longArrayOf(0))
          .build()
      )
    } catch (e: Exception) {
      if (Build.VERSION.SDK_INT >= 31 && e.message?.contains("Time limit", ignoreCase = true) == true) {
        Log.w(TAG, "Foreground service timed out, but not in onTimeout call", e)
        stopDueToTimeout()
      } else {
        throw e
      }
    }
  }

  private fun updateEntry(id: Int, transform: (Entry) -> Entry) {
    val oldEntry = allActiveMessages[id]
    if (oldEntry == null) {
      Log.w(TAG, "Failed to replace notification, it was not found")
      return
    }

    val newEntry = transform(oldEntry)

    if (oldEntry == newEntry) {
      Log.d(TAG, "handleReplace() skip, no change $newEntry")
      return
    }

    Log.i(TAG, "handleReplace() $newEntry")
    allActiveMessages[newEntry.id] = newEntry
    updateNotification()
  }

  private data class Entry(
    val title: String,
    val channelId: String,
    @field:DrawableRes @param:DrawableRes val iconRes: Int,
    val id: Int,
    val progressMax: Int,
    val progress: Int,
    val indeterminate: Boolean
  ) {
    override fun toString(): String {
      return "ChannelId: $channelId, ID: $id, Progress: $progress/$progressMax ${if (indeterminate) "indeterminate" else "determinate"}"
    }

    companion object {
      fun fromIntent(intent: Intent): Entry {
        return Entry(
          title = intent.getStringExtra(EXTRA_TITLE) ?: DEFAULT_ENTRY.title,
          channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: DEFAULT_ENTRY.channelId,
          iconRes = intent.getIntExtra(EXTRA_ICON_RES, DEFAULT_ENTRY.iconRes),
          id = intent.getIntExtra(EXTRA_ID, DEFAULT_ENTRY.id),
          progressMax = intent.getIntExtra(EXTRA_PROGRESS_MAX, DEFAULT_ENTRY.progressMax),
          progress = intent.getIntExtra(EXTRA_PROGRESS, DEFAULT_ENTRY.progress),
          indeterminate = intent.getBooleanExtra(EXTRA_PROGRESS_INDETERMINATE, DEFAULT_ENTRY.indeterminate)
        )
      }
    }
  }

  inner class LocalBinder : Binder() {
    val service: GenericForegroundService
      get() = this@GenericForegroundService
  }
}
