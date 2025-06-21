package org.thoughtcrime.securesms.registrationv3.ui.restore

import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.thoughtcrime.securesms.util.WakeLockUtil
import kotlin.time.Duration.Companion.minutes

/**
 * Holds on to and manages a wake-lock when restoring a remote backup.
 */
class RemoteRestoreWakeLock(
  private val activity: ComponentActivity
) : DefaultLifecycleObserver {

  companion object {
    private val TIMEOUT = 10.minutes.inWholeMilliseconds
  }

  private var wakeLock: PowerManager.WakeLock? = null

  init {
    activity.lifecycle.addObserver(this)
  }

  fun acquire() {
    synchronized(this) {
      if (wakeLock?.isHeld == true) {
        return
      }

      wakeLock = WakeLockUtil.acquire(activity, PowerManager.PARTIAL_WAKE_LOCK, TIMEOUT, "remoteRestore")
    }
  }

  fun release() {
    synchronized(this) {
      if (wakeLock?.isHeld == true) {
        wakeLock?.release()
        wakeLock = null
      }
    }
  }

  override fun onPause(owner: LifecycleOwner) {
    release()
  }
}
