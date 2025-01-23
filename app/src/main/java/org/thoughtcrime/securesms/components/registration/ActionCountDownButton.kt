package org.thoughtcrime.securesms.components.registration

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ActionCountDownButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : MaterialButton(context, attrs, defStyle) {
  @StringRes
  private var enabledText = 0

  @StringRes
  private var disabledText = 0

  private var countDownToTime: Duration = 0.seconds
  private var listener: Listener? = null

  private var updateRunnable = Runnable {
    updateCountDown()
  }

  /**
   * Starts a count down to the specified {@param time}.
   */
  fun startCountDownTo(time: Duration) {
    if (time > 0.seconds) {
      countDownToTime = time
      removeCallbacks(updateRunnable)
      updateCountDown()
    } else {
      setText(enabledText)
      isEnabled = false
      alpha = 0.5f
    }
  }

  private fun setActionEnabled() {
    setText(enabledText)
    isEnabled = true
    alpha = 1.0f
  }

  private fun updateCountDown() {
    val remaining = countDownToTime - System.currentTimeMillis().milliseconds
    if (remaining > 1.seconds) {
      isEnabled = false
      alpha = 0.5f
      val totalRemainingSeconds = remaining.inWholeSeconds.toInt()
      val minutesRemaining = totalRemainingSeconds / 60
      val secondsRemaining = totalRemainingSeconds % 60

      text = resources.getString(disabledText, minutesRemaining, secondsRemaining)
      listener?.onRemaining(this, totalRemainingSeconds)
      postDelayed(updateRunnable, 250)
    } else {
      setActionEnabled()
    }
  }

  fun setListener(listener: Listener?) {
    this.listener = listener
  }

  fun setTextResources(@StringRes enabled: Int, @StringRes disabled: Int) {
    enabledText = enabled
    disabledText = disabled
  }

  interface Listener {
    fun onRemaining(view: ActionCountDownButton, secondsRemaining: Int)
  }
}
