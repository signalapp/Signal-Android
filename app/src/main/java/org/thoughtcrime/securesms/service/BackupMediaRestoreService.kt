package org.thoughtcrime.securesms.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.signal.core.util.ByteSize
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.RestoreState
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgress
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.service.BackupMediaRestoreService.Companion.hasTimedOut
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.ReentrantLock
import javax.annotation.CheckReturnValue
import kotlin.concurrent.withLock

/**
 * Foreground service used to track progress when restoring backup media.
 *
 * Like [AttachmentProgressService], this is allowed to be started by multiple people and
 * supports that by using a set of controllers instead of just one.
 *
 * Unlike other services, this can be started without opening the app which means that
 * it can crash if we hit the 6 hour data sync limit. To handle this, we keep track of [hasTimedOut]
 * that gets set in [onTimeout] and is reset every time we open the app / when the user initiates a backup restore.
 *
 * For the initial restoration of a backup (eg [RestoreState.PENDING] / [RestoreState.RESTORING_DB]), see [BackupProgressService] instead
 */
class BackupMediaRestoreService : SafeForegroundService() {

  companion object {
    private val TAG = Log.tag(BackupMediaRestoreService::class)

    private var title: String = ""
    private var downloadedBytes = 0.bytes
    private var totalBytes = 0.bytes

    private var hasTimedOut: Boolean = false
    private val listeners: MutableSet<() -> Unit> = CopyOnWriteArraySet()
    private val controllers: LinkedHashSet<Controller> = linkedSetOf()
    private val controllerLock = ReentrantLock()

    @JvmStatic
    @CheckReturnValue
    fun start(context: Context, startingTitle: String): Controller? {
      controllerLock.withLock {
        val started = if (hasTimedOut) {
          Log.i(TAG, "[start] Service has hit the 6 hour time limit. Cannot start.")
          false
        } else if (controllers.isEmpty()) {
          Log.i(TAG, "[start] First controller. Starting.")
          SafeForegroundService.start(context, BackupMediaRestoreService::class.java)
        } else {
          Log.i(TAG, "[start] No need to start the service again. Already have an active controller.")
          true
        }

        return if (started) {
          val controller = Controller(context, startingTitle)
          controllers += controller
          onControllersChanged(context)
          controller
        } else {
          null
        }
      }
    }

    @JvmStatic
    fun stop(context: Context, fromTimeout: Boolean = false) {
      controllers.clear()
      stop(context, BackupMediaRestoreService::class.java, fromTimeout)
    }

    fun resetTimeout() {
      Log.i(TAG, "Resetting the 6 hour timeout limit.")
      hasTimedOut = false
    }

    private fun onControllersChanged(context: Context) {
      controllerLock.withLock {
        if (controllers.isNotEmpty()) {
          val originalTitle = title
          val originalDownloadedBytes = downloadedBytes
          val originalTotalBytes = totalBytes

          title = controllers.first().title
          downloadedBytes = controllers.first().downloadedBytes
          totalBytes = controllers.first().totalBytes

          if (originalTitle != title || originalDownloadedBytes != downloadedBytes || originalTotalBytes != totalBytes) {
            listeners.forEach { it() }
          }
        } else {
          Log.i(TAG, "[onControllersChanged] No controllers remaining. Stopping.")
          stop(context)
        }
      }
    }
  }

  private fun getForegroundNotification(context: Context): Notification {
    val progress = (downloadedBytes.inWholeBytes.toFloat() / totalBytes.inWholeBytes).coerceIn(0f, 1f)
    val notificationBuilder = NotificationCompat.Builder(context, NotificationChannels.getInstance().OTHER)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(title)
      .setProgress(100, (progress * 100).toInt(), false)
      .setContentIntent(PendingIntent.getActivity(context, 0, MainActivity.clearTop(context), PendingIntentFlags.mutable()))
      .setVibrate(longArrayOf(0))

    if (downloadedBytes >= 0.bytes) {
      notificationBuilder.setContentText(context.resources.getString(R.string.BackupStatus__status_size_of_size, downloadedBytes.toUnitString(), totalBytes.toUnitString()))
    }

    return notificationBuilder.build()
  }

  val listener = {
    try {
      startForeground(notificationId, getForegroundNotification(Intent()))
    } catch (e: Exception) {
      if (Build.VERSION.SDK_INT >= 31 && e.message?.contains("Time limit", ignoreCase = true) == true) {
        Log.w(TAG, "Foreground service timed out, but not in onTimeout call", e)
        stopDueToTimeout()
      } else {
        throw e
      }
    }
  }

  override val tag: String = TAG
  override val notificationId: Int = NotificationIds.BACKUP_PROGRESS

  override fun getForegroundNotification(intent: Intent): Notification {
    return getForegroundNotification(this)
  }

  override fun onCreate() {
    super.onCreate()
    listeners += listener
  }

  override fun onDestroy() {
    super.onDestroy()
    listeners -= listener
  }

  override fun onTimeout(startId: Int, fgsType: Int) {
    Log.w(TAG, "BackupProgressService has timed out. startId: $startId, foregroundServiceType: $fgsType")
    stopDueToTimeout()
  }

  private fun stopDueToTimeout() {
    controllerLock.withLock {
      hasTimedOut = true
      controllers.forEach { it.closeFromTimeout() }
      stop(context = this, fromTimeout = true)
    }

    listeners -= listener
  }

  /**
   * Use to update notification progress/state.
   */
  class Controller(private val context: Context, startingTitle: String) : AutoCloseable {

    val title: String = startingTitle
    val downloadedBytes: ByteSize
      get() = ArchiveRestoreProgress.state.completedRestoredSize

    val totalBytes: ByteSize
      get() = ArchiveRestoreProgress.state.totalRestoreSize

    fun closeFromTimeout() {
      controllerLock.withLock {
        controllers.remove(this)
      }
    }

    override fun close() {
      controllerLock.withLock {
        controllers.remove(this)
        onControllersChanged(context)
      }
    }
  }
}
