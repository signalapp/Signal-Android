package org.thoughtcrime.securesms.components.registration

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import java.util.concurrent.TimeUnit

class ActionCountDownButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : MaterialButton(context, attrs, defStyle) {
  @StringRes
  private var enabledText = 0

  @StringRes
  private var disabledText = 0

  private var countDownToTime: Long = 0
  private var listener: Listener? = null

  /**
   * Starts a count down to the specified {@param time}.
   */
  fun startCountDownTo(time: Long) {
    if (time > 0) {
      countDownToTime = time
      updateCountDown()
    }
  }

  private fun setActionEnabled() {
    setText(enabledText)
    isEnabled = true
    alpha = 1.0f
  }

  private fun updateCountDown() {
    val remainingMillis = countDownToTime - System.currentTimeMillis()
    if (remainingMillis > 0) {
      isEnabled = false
      alpha = 0.5f
      val totalRemainingSeconds = TimeUnit.MILLISECONDS.toSeconds(remainingMillis).toInt()
      val minutesRemaining = totalRemainingSeconds / 60
      val secondsRemaining = totalRemainingSeconds % 60
      text = resources.getString(disabledText, minutesRemaining, secondsRemaining)
      listener?.onRemaining(this, totalRemainingSeconds)
      postDelayed({ updateCountDown() }, 250)
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
