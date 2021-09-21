package org.thoughtcrime.securesms.components

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView
import org.thoughtcrime.securesms.R

class PlaybackSpeedToggleTextView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

  private val speeds: IntArray = context.resources.getIntArray(R.array.PlaybackSpeedToggleTextView__speeds)
  private val labels: Array<String> = context.resources.getStringArray(R.array.PlaybackSpeedToggleTextView__speed_labels)
  private var currentSpeedIndex = 0
  private var requestedSpeed: Float? = null

  var playbackSpeedListener: PlaybackSpeedListener? = null

  init {
    text = getCurrentLabel()
    super.setOnClickListener {
      currentSpeedIndex = getNextSpeedIndex()
      text = getCurrentLabel()
      requestedSpeed = getCurrentSpeed()

      playbackSpeedListener?.onPlaybackSpeedChanged(getCurrentSpeed())
    }

    isClickable = false
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent?): Boolean {
    if (isClickable) {
      when (event?.action) {
        MotionEvent.ACTION_DOWN -> zoomIn()
        MotionEvent.ACTION_UP -> zoomOut()
        MotionEvent.ACTION_CANCEL -> zoomOut()
      }
    }

    return super.onTouchEvent(event)
  }

  fun clearRequestedSpeed() {
    requestedSpeed = null
  }

  fun setCurrentSpeed(speed: Float) {
    if (speed == getCurrentSpeed() || (requestedSpeed != null && requestedSpeed != speed)) {
      if (requestedSpeed == speed) {
        requestedSpeed = null
      }

      return
    }

    requestedSpeed = null

    val outOf100 = (speed * 100).toInt()
    val index = speeds.indexOf(outOf100)

    if (index != -1) {
      currentSpeedIndex = index
      text = getCurrentLabel()
    } else {
      throw IllegalArgumentException("Invalid Speed $speed")
    }
  }

  private fun getNextSpeedIndex(): Int = (currentSpeedIndex + 1) % speeds.size

  private fun getCurrentSpeed(): Float = speeds[currentSpeedIndex] / 100f

  private fun getCurrentLabel(): String = labels[currentSpeedIndex]

  private fun zoomIn() {
    animate()
      .setInterpolator(DecelerateInterpolator())
      .setDuration(150L)
      .scaleX(1.2f)
      .scaleY(1.2f)
  }

  private fun zoomOut() {
    animate()
      .setInterpolator(DecelerateInterpolator())
      .setDuration(150L)
      .scaleX(1f)
      .scaleY(1f)
  }

  override fun setOnClickListener(l: OnClickListener?) {
    throw UnsupportedOperationException()
  }

  interface PlaybackSpeedListener {
    fun onPlaybackSpeedChanged(speed: Float)
  }
}
