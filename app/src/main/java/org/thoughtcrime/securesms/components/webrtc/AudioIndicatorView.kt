package org.thoughtcrime.securesms.components.webrtc

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.service.webrtc.WebRtcActionProcessor
import org.thoughtcrime.securesms.util.visible

/**
 * An indicator shown for each participant in a call which shows the state of their audio.
 */
class AudioIndicatorView(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {

  companion object {
    private const val SIDE_BAR_SHRINK_FACTOR = 0.75f
  }

  private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = Color.WHITE
  }

  private val barRect = RectF()
  private val barWidth = DimensionUnit.DP.toPixels(3f)
  private val barRadius = DimensionUnit.DP.toPixels(32f)
  private val barPadding = DimensionUnit.DP.toPixels(3f)
  private var middleBarAnimation: ValueAnimator? = null
  private var sideBarAnimation: ValueAnimator? = null

  private var showAudioLevel = false
  private var lastAudioLevel: CallParticipant.AudioLevel? = null

  init {
    inflate(context, R.layout.audio_indicator_view, this)
    setWillNotDraw(false)

    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.transparent_black_70))
  }

  private val micMuted: View = findViewById(R.id.mic_muted)

  fun bind(microphoneEnabled: Boolean, level: CallParticipant.AudioLevel?) {
    setBackgroundResource(R.drawable.circle_tintable)

    this.visible = !microphoneEnabled || level != null

    micMuted.visible = !microphoneEnabled

    val wasShowingAudioLevel = showAudioLevel
    showAudioLevel = microphoneEnabled && level != null

    if (showAudioLevel) {
      val scaleFactor = when (level!!) {
        CallParticipant.AudioLevel.LOWEST -> 0.1f
        CallParticipant.AudioLevel.LOW -> 0.3f
        CallParticipant.AudioLevel.MEDIUM -> 0.5f
        CallParticipant.AudioLevel.HIGH -> 0.65f
        CallParticipant.AudioLevel.HIGHEST -> 0.8f
      }

      middleBarAnimation?.end()

      middleBarAnimation = createAnimation(middleBarAnimation, height * scaleFactor)
      middleBarAnimation?.start()

      sideBarAnimation?.end()

      var finalHeight = height * scaleFactor
      if (level != CallParticipant.AudioLevel.LOWEST) {
        finalHeight *= SIDE_BAR_SHRINK_FACTOR
      }

      sideBarAnimation = createAnimation(sideBarAnimation, finalHeight)
      sideBarAnimation?.start()
    }

    if (showAudioLevel != wasShowingAudioLevel || level != lastAudioLevel) {
      invalidate()
    }

    lastAudioLevel = level
  }

  private fun createAnimation(current: ValueAnimator?, finalHeight: Float): ValueAnimator {
    val currentHeight = current?.animatedValue as? Float ?: 0f

    return ValueAnimator.ofFloat(currentHeight, finalHeight).apply {
      duration = WebRtcActionProcessor.AUDIO_LEVELS_INTERVAL.toLong()
      interpolator = DecelerateInterpolator()
    }
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    val middleBarHeight = middleBarAnimation?.animatedValue as? Float
    val sideBarHeight = sideBarAnimation?.animatedValue as? Float
    if (showAudioLevel && middleBarHeight != null && sideBarHeight != null) {
      val audioLevelWidth = 3 * barWidth + 2 * barPadding
      val xOffsetBase = (width - audioLevelWidth) / 2

      canvas.drawBar(
        xOffset = xOffsetBase,
        size = sideBarHeight
      )

      canvas.drawBar(
        xOffset = barPadding + barWidth + xOffsetBase,
        size = middleBarHeight
      )

      canvas.drawBar(
        xOffset = 2 * (barPadding + barWidth) + xOffsetBase,
        size = sideBarHeight
      )

      if (middleBarAnimation?.isRunning == true || sideBarAnimation?.isRunning == true) {
        invalidate()
      }
    }
  }

  private fun Canvas.drawBar(xOffset: Float, size: Float) {
    val yOffset = (height - size) / 2
    barRect.set(xOffset, yOffset, xOffset + barWidth, height - yOffset)
    drawRoundRect(barRect, barRadius, barRadius, barPaint)
  }
}
