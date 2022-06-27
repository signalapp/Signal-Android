package org.thoughtcrime.securesms.stories

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.media.AudioManager
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.withClip
import androidx.media.AudioManagerCompat
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ServiceUtil

/**
 * Displays a vertical volume bar.
 */
class StoryVolumeBar @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

  private val minimum: Int
  private val maximum: Int

  private var level: Int
  private val bounds: Rect = Rect()
  private val clipBoundsF: RectF = RectF()
  private val clipPath: Path = Path()
  private val clipRadius = DimensionUnit.DP.toPixels(4f)

  private val backgroundPaint = Paint().apply {
    isAntiAlias = true
    color = ContextCompat.getColor(context, R.color.transparent_black_40)
    style = Paint.Style.STROKE
  }

  private val foregroundPaint = Paint().apply {
    isAntiAlias = true
    color = ContextCompat.getColor(context, R.color.core_white)
    style = Paint.Style.STROKE
  }

  init {
    val audioManager = ServiceUtil.getAudioManager(context)

    if (isInEditMode) {
      minimum = 0
      maximum = 100
      level = 50
    } else {
      minimum = AudioManagerCompat.getStreamMinVolume(audioManager, AudioManager.STREAM_MUSIC)
      maximum = AudioManagerCompat.getStreamMaxVolume(audioManager, AudioManager.STREAM_MUSIC)
      level = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }
  }

  fun setLevel(level: Int) {
    this.level = level
    invalidate()
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)

    backgroundPaint.strokeWidth = (measuredWidth - paddingLeft - paddingRight).toFloat()
    foregroundPaint.strokeWidth = backgroundPaint.strokeWidth
  }

  override fun onDraw(canvas: Canvas) {
    canvas.getClipBounds(bounds)
    clipBoundsF.set(bounds)
    clipPath.reset()
    clipPath.addRoundRect(clipBoundsF, clipRadius, clipRadius, Path.Direction.CW)

    canvas.withClip(clipPath) {
      canvas.drawLine(bounds.exactCenterX(), bounds.top.toFloat(), bounds.exactCenterX(), bounds.bottom.toFloat(), backgroundPaint)

      val fillPercent: Float = (level - minimum) / (maximum - minimum.toFloat())
      val fillHeight = bounds.height() * fillPercent

      canvas.drawLine(bounds.exactCenterX(), bounds.bottom.toFloat() - fillHeight, bounds.exactCenterX(), bounds.bottom.toFloat(), foregroundPaint)
    }
  }
}
