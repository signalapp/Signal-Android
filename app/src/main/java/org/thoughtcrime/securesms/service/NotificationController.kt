package org.thoughtcrime.securesms.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobs.UnableToStartException
import org.thoughtcrime.securesms.service.GenericForegroundService.Companion.stopForegroundTask
import org.thoughtcrime.securesms.service.GenericForegroundService.LocalBinder
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class NotificationController internal constructor(private val context: Context, val id: Int) : AutoCloseable, ServiceConnection {
  private val service = AtomicReference<GenericForegroundService?>()
  private val lock = ReentrantLock()

  private var progress = 0
  private var progressMax = 0
  private var indeterminate = false
  private var percent: Int = -1
  private var isBound: Boolean

  companion object {
    private val TAG = Log.tag(NotificationController::class.java)
  }

  init {
    isBound = bindToService()
  }

  override fun onServiceConnected(name: ComponentName, service: IBinder) {
    Log.i(TAG, "[onServiceConnected] Name: $name")

    val binder = service as LocalBinder
    val genericForegroundService = binder.service

    this.service.set(genericForegroundService)

    lock.withLock {
      updateProgressOnService()
    }
  }

  override fun onServiceDisconnected(name: ComponentName) {
    Log.i(TAG, "[onServiceDisconnected] Name: $name")
    service.set(null)
  }

  override fun close() {
    try {
      if (isBound) {
        Log.d(TAG, "[close] Unbinding service.")
        context.unbindService(this)
        isBound = false
      } else {
        Log.w(TAG, "[close] Service was not bound at the time of close()...")
      }

      if (service.get()?.hasTimedOut == true) {
        Log.w(TAG, "[close] Service has timed out, skipping stop foreground task.")
      } else {
        stopForegroundTask(context, id)
      }
    } catch (e: IllegalStateException) {
      Log.w(TAG, "[close] Failed to unbind service...", e)
    } catch (e: UnableToStartException) {
      Log.w(TAG, "[close] Failed to unbind service...", e)
    }
  }

  fun setIndeterminateProgress() {
    lock.withLock {
      setProgress(
        newProgressMax = 0,
        newProgress = 0,
        indeterminant = true
      )
    }
  }

  fun setProgress(newProgressMax: Long, newProgress: Long) {
    lock.withLock {
      setProgress(
        newProgressMax = newProgressMax.toInt(),
        newProgress = newProgress.toInt(),
        indeterminant = false
      )
    }
  }

  fun replaceTitle(title: String) {
    lock.withLock {
      service.get()?.replaceTitle(id, title)
        ?: Log.w(TAG, "Tried to update the title, but the service was no longer bound!")
    }
  }

  private fun bindToService(): Boolean {
    return context.bindService(Intent(context, GenericForegroundService::class.java), this, Context.BIND_AUTO_CREATE)
  }

  private fun setProgress(newProgressMax: Int, newProgress: Int, indeterminant: Boolean) {
    val newPercent = if (newProgressMax != 0) {
      100 * newProgress / newProgressMax
    } else {
      -1
    }

    val same = newPercent == percent && indeterminate == indeterminant

    percent = newPercent
    progress = newProgress
    progressMax = newProgressMax
    indeterminate = indeterminant

    if (!same) {
      updateProgressOnService()
    }
  }

  private fun updateProgressOnService() {
    service.get()?.replaceProgress(id, progressMax, progress, indeterminate)
      ?: Log.w(TAG, "Tried to update the progress, but the service was no longer bound!")
  }
}
