package org.thoughtcrime.securesms.conversation

import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.Util
import java.util.concurrent.TimeUnit

/**
 * Lifecycle-aware class which will call onTick every 1 minute.
 * Used to ensure that conversation timestamps are updated appropriately.
 */
class ConversationUpdateTick(
  private val onTickListener: OnTickListener
) : DefaultLifecycleObserver {

  private val handler = Handler(Looper.getMainLooper())
  private var isResumed = false
  private var lastTick = -1L

  constructor(onTickListener: () -> Unit) : this(object : OnTickListener {
    override fun onTick() {
      onTickListener()
    }
  })

  override fun onResume(owner: LifecycleOwner) {
    isResumed = true

    handler.removeCallbacksAndMessages(null)

    if (lastTick > 0) {
      val timeSinceLastTick = System.currentTimeMillis() - lastTick
      if (timeSinceLastTick < 0) {
        Log.w(TAG, "Time since last tick is invalid. Reinitializing and posting update in $TIMEOUT ms")

        lastTick = System.currentTimeMillis()
        handler.postDelayed(this::onTick, TIMEOUT)
      }

      val timeUntilNextTick = Util.clamp(TIMEOUT - timeSinceLastTick, 0, TIMEOUT)
      if (timeUntilNextTick == 0L) {
        Log.i(TAG, "Last tick outside timeout period. Posting update immediately")
        handler.post(this::onTick)
      } else {
        Log.i(TAG, "Last tick within timeout period. Posting update in $timeUntilNextTick ms")
        handler.postDelayed(this::onTick, timeUntilNextTick)
      }
    } else {
      Log.i(TAG, "No time since last tick. Initialising and posting update in $TIMEOUT ms")

      lastTick = System.currentTimeMillis()
      handler.postDelayed(this::onTick, TIMEOUT)
    }
  }

  override fun onPause(owner: LifecycleOwner) {
    isResumed = false

    handler.removeCallbacksAndMessages(null)
  }

  private fun onTick() {
    if (isResumed) {
      onTickListener.onTick()
      lastTick = System.currentTimeMillis()

      handler.removeCallbacksAndMessages(null)
      handler.postDelayed(this::onTick, TIMEOUT)
    }
  }

  interface OnTickListener {
    fun onTick()
  }

  companion object {
    @VisibleForTesting
    val TIMEOUT = TimeUnit.MINUTES.toMillis(1)

    private val TAG = Log.tag(ConversationUpdateTick::class.java)
  }
}
