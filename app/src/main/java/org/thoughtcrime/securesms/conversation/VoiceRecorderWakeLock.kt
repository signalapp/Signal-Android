package org.thoughtcrime.securesms.conversation

import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.thoughtcrime.securesms.util.WakeLockUtil
import java.util.concurrent.TimeUnit

/**
 * Holds on to and manages a wake-lock for the device proximity sensor.
 *
 * This class will register itself as an observe of the given activity's lifecycle and automatically
 * release the lock if it holds one in onPause
 */
class VoiceRecorderWakeLock(
  private val activity: ComponentActivity
) : DefaultLifecycleObserver {

  private var wakeLock: PowerManager.WakeLock? = null

  init {
    activity.lifecycle.addObserver(this)
  }

  fun acquire() {
    synchronized(this) {
      if (wakeLock?.isHeld == true) {
        return
      }

      wakeLock = WakeLockUtil.acquire(activity, PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TimeUnit.HOURS.toMillis(1), "voiceRecorder")
    }
  }

  fun release() {
    synchronized(this) {
      if (wakeLock?.isHeld == true) {
        wakeLock?.release()
      }
    }
  }

  override fun onPause(owner: LifecycleOwner) {
    release()
  }
}
