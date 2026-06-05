package org.thoughtcrime.securesms.linkdevice

import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.thoughtcrime.securesms.util.WakeLockUtil
import kotlin.time.Duration.Companion.minutes

/**
 * Holds on to and manages a wake-lock when linking a device.
 */
class LinkDeviceWakeLock(
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

      wakeLock = WakeLockUtil.acquire(activity, PowerManager.PARTIAL_WAKE_LOCK, TIMEOUT, "linkDevice")
    }

    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  fun release() {
    synchronized(this) {
      if (wakeLock?.isHeld == true) {
        wakeLock?.release()
        wakeLock = null
      }
    }

    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  override fun onPause(owner: LifecycleOwner) {
    release()
  }
}
