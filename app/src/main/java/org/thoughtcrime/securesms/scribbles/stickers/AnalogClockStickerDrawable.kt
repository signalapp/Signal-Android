package org.thoughtcrime.securesms.scribbles.stickers

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.appcompat.content.res.AppCompatResources
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.toLocalDateTime
import java.time.LocalDateTime
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Animatable drawable of an analog clock. You can set a time, or start the animation to animate
 * the current time.
 */
class AnalogClockStickerDrawable(val context: Context) : Drawable(), Animatable {

  private var clockFace: Drawable = AppCompatResources.getDrawable(context, R.drawable.clock_face_1)!!
  private var minuteHand: Drawable = AppCompatResources.getDrawable(context, R.drawable.clock_minute_hand_1)!!
  private var hourHand: Drawable = AppCompatResources.getDrawable(context, R.drawable.clock_hour_hand_1)!!
  private var clockCenter: Drawable? = null

  /** Percentage of hour hand height that should shoot past the center point **/
  private var hourOffset = 0.28f

  /** Percentage of minute hand height that should shoot past the center point **/
  private var minuteOffset = 0.2f

  private var animating = false
  private var displayStyle = Style.STANDARD

  private var time: Long? = null

  override fun draw(canvas: Canvas) {
    clockFace.draw(canvas)

    val now = time?.toLocalDateTime() ?: LocalDateTime.now()
    val hourDeg = computeHourRotationDeg(now)
    val minuteDeg = computeMinuteRotationDeg(now)

    canvas.save()
    canvas.rotate(hourDeg, bounds.exactCenterX(), bounds.exactCenterY())
    hourHand.draw(canvas)
    canvas.restore()

    canvas.save()
    canvas.rotate(minuteDeg, bounds.exactCenterX(), bounds.exactCenterY())
    minuteHand.draw(canvas)
    canvas.restore()

    if (animating) {
      scheduleSelf(this::invalidateSelf, SystemClock.uptimeMillis() + 1000)
    }

    clockCenter?.draw(canvas)
  }

  fun nextFace() {
    setStyle(displayStyle.next())
  }

  fun setStyle(style: Style) {
    displayStyle = style
    when (displayStyle) {
      Style.STANDARD -> clockFace1()
      Style.BLOCKY -> clockFace2()
      Style.LIGHT -> clockFace3()
      Style.GREEN -> clockFace4()
    }
    onBoundsChange(bounds)
  }

  fun getStyle(): Style {
    return displayStyle
  }

  fun setTime(newTime: Long?) {
    time = newTime
    invalidateSelf()
  }

  private fun clockFace1() {
    clockFace = AppCompatResources.getDrawable(context, R.drawable.clock_face_1)!!
    minuteHand = AppCompatResources.getDrawable(context, R.drawable.clock_minute_hand_1)!!
    hourHand = AppCompatResources.getDrawable(context, R.drawable.clock_hour_hand_1)!!
    clockCenter = null

    hourOffset = 0.28f
    minuteOffset = 0.2f
  }

  private fun clockFace2() {
    clockFace = AppCompatResources.getDrawable(context, R.drawable.clock_face_2)!!
    minuteHand = AppCompatResources.getDrawable(context, R.drawable.clock_minute_hand_2)!!
    hourHand = AppCompatResources.getDrawable(context, R.drawable.clock_hour_hand_2)!!
    clockCenter = null

    hourOffset = 0.238f
    minuteOffset = 0.1623f
  }

  private fun clockFace3() {
    clockFace = AppCompatResources.getDrawable(context, R.drawable.clock_face_3)!!
    minuteHand = AppCompatResources.getDrawable(context, R.drawable.clock_minute_hand_3)!!
    hourHand = AppCompatResources.getDrawable(context, R.drawable.clock_hour_hand_3)!!
    clockCenter = null

    hourOffset = 0f
    minuteOffset = 0f
  }

  private fun clockFace4() {
    clockFace = AppCompatResources.getDrawable(context, R.drawable.clock_face_4)!!
    minuteHand = AppCompatResources.getDrawable(context, R.drawable.clock_minute_hand_4)!!
    hourHand = AppCompatResources.getDrawable(context, R.drawable.clock_hour_hand_4)!!
    clockCenter = AppCompatResources.getDrawable(context, R.drawable.clock_center_cover_4)

    hourOffset = 0f
    minuteOffset = 0f
  }

  override fun setAlpha(alpha: Int) = Unit

  override fun setColorFilter(colorFilter: ColorFilter?) = Unit

  override fun getOpacity(): Int {
    return PixelFormat.TRANSLUCENT
  }

  override fun onBoundsChange(bounds: Rect) {
    val dimen = min(bounds.width(), bounds.height())
    val scale: Float = dimen.toFloat() / clockFace.intrinsicWidth.toFloat()
    val centerX = bounds.centerX()
    val centerY = bounds.centerY()

    val hourW = (hourHand.intrinsicWidth * scale).roundToInt()
    val hourH = (hourHand.intrinsicHeight * scale).roundToInt()

    val minuteW = (minuteHand.intrinsicWidth * scale).roundToInt()
    val minuteH = (minuteHand.intrinsicHeight * scale).roundToInt()

    if (bounds.width() > bounds.height()) {
      val diff = (bounds.width() - bounds.height()) / 2
      clockFace.setBounds(bounds.left + diff, bounds.top, bounds.right - diff, bounds.bottom)
    } else {
      val diff = (bounds.height() - bounds.width()) / 2
      clockFace.setBounds(bounds.left, bounds.top - diff, bounds.right, bounds.bottom + diff)
    }
    val hourVertical = (hourH * hourOffset).roundToInt()
    val minuteVertical = (minuteH * minuteOffset).roundToInt()
    hourHand.setBounds(centerX - hourW / 2, (centerY - hourH + hourVertical), centerX + hourW / 2, centerY + hourVertical)
    minuteHand.setBounds(centerX - minuteW / 2, (centerY - minuteH + minuteVertical), centerX + minuteW / 2, centerY + minuteVertical)

    val centerVal = clockCenter
    if (centerVal != null) {
      val centerW = (centerVal.intrinsicWidth * scale).roundToInt()
      val centerH = (centerVal.intrinsicHeight * scale).roundToInt()

      centerVal.setBounds(centerX - centerW / 2, centerY - centerH / 2, centerX + centerW / 2, centerY + centerH / 2)
    }
  }

  override fun getIntrinsicWidth(): Int {
    return clockFace.intrinsicWidth
  }

  override fun getIntrinsicHeight(): Int {
    return clockFace.intrinsicHeight
  }

  override fun start() {
    animating = true
    invalidateSelf()
  }

  override fun stop() {
    animating = false
    unscheduleSelf(this::invalidateSelf)
  }

  override fun isRunning(): Boolean {
    return animating
  }

  private fun computeHourRotationDeg(localDateTime: LocalDateTime): Float {
    val hour = localDateTime.hour % 12
    val minute = localDateTime.minute
    val seconds = localDateTime.second

    return 360f * (hour + (minute / 60f) + (seconds / 3600f)) / 12f
  }

  private fun computeMinuteRotationDeg(localDateTime: LocalDateTime): Float {
    val minute = localDateTime.minute
    val seconds = localDateTime.second

    return 360f * (minute + (seconds / 60f)) / 60f
  }

  enum class Style(val type: Int) {
    STANDARD(0),
    BLOCKY(1),
    LIGHT(2),
    GREEN(3);

    fun next(): Style {
      val values = Style.values()

      return values[(values.indexOf(this) + 1) % values.size]
    }

    companion object {
      fun fromType(type: Int) = Style.values().first { it.type == type }
    }
  }
}
