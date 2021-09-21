package org.thoughtcrime.securesms.conversation

import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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

  override fun onResume(owner: LifecycleOwner) {
    isResumed = true

    handler.removeCallbacksAndMessages(null)
    onTick()
  }

  override fun onPause(owner: LifecycleOwner) {
    isResumed = false

    handler.removeCallbacksAndMessages(null)
  }

  private fun onTick() {
    if (isResumed) {
      onTickListener.onTick()

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
  }
}
