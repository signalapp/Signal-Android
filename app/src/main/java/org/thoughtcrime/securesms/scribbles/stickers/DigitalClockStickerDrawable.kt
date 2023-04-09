package org.thoughtcrime.securesms.scribbles.stickers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.TextPaint
import android.text.format.DateFormat
import org.thoughtcrime.securesms.util.toLocalDateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

/**
 * Animatable drawable of a digital clock. You can set a time, or start the animation to animate
 * the current time. Supports 12/24 hr time.
 */
class DigitalClockStickerDrawable(
  val context: Context,
  private var displayStyle: Style = Style.LIGHT_NO_BG
) :
  Drawable(), Animatable {

  companion object {
    private const val BG_PADDING = 40f
    private const val BG_CORNER_RADIUS = 40f
    private const val AM_PM_SPACING = 7f
    private const val LIGHT_BG_COLOR = 0x66FFFFFF
    private const val DARK_BG_COLOR = 0x66000000
    private const val RED_TEXT_COLOR = 0xFFFF4747.toInt()
    private const val TIME_TEXT_SIZE = 204f
    private const val AM_PM_TEXT_SIZE = 50f

    /** Box dimensions that wrap the sticker. Dimensions are relative to this value from designs.  */
    private const val STICKER_BOX_SIZE = 512f

    /** Additional scaling factor as sticker is still small within the box */
    private const val STICKER_SCALING_ADJUSTMENT = 1.2f
  }

  private val ampmTypeface = Typeface.createFromAsset(context.assets, "fonts/Inter-Medium.otf")
  private val digitTypeface = Typeface.createFromAsset(context.assets, "fonts/Hatsuishi-Regular.otf")

  private var wrapped = false
  private var animating = false
  private var scale = 1f

  private var time: Long? = null

  private val digitPaint = TextPaint().apply {
    this.typeface = digitTypeface
    this.textSize = 204f
    this.textAlign = Paint.Align.LEFT
    this.color = Color.WHITE
  }

  private val ampmPaint = TextPaint().apply {
    this.typeface = ampmTypeface
    this.textSize = 50f
    this.textAlign = Paint.Align.LEFT
    this.color = Color.WHITE
  }

  private val bgPaint = Paint().apply {
    color = Color.WHITE
  }

  init {
    setStyle(displayStyle)
  }

  override fun draw(canvas: Canvas) {
    val centerX = bounds.exactCenterX()
    val centerY = bounds.exactCenterY()

    val timeMetrics = digitPaint.fontMetrics
    val now = time?.toLocalDateTime() ?: LocalDateTime.now()
    val is24Hours = DateFormat.is24HourFormat(context)
    val timeHeight = timeMetrics.bottom + timeMetrics.top + timeMetrics.leading
    val baseline = centerY - timeHeight / 2f
    if (is24Hours) {
      digitPaint.textAlign = Paint.Align.CENTER
      val timeStr = getHoursString(now)
      val width = digitPaint.measureText(timeStr)
      if (wrapped) {
        val bgCornerRadius = getBgCornerRadius()
        val bgPadding = getBgPadding()
        canvas.drawRoundRect(
          centerX - width / 2f - bgPadding,
          baseline + timeMetrics.top - bgPadding,
          centerX + width / 2f + bgPadding,
          baseline + timeMetrics.bottom + bgPadding,
          bgCornerRadius,
          bgCornerRadius,
          bgPaint
        )
      }
      canvas.drawText(timeStr, centerX, baseline, digitPaint)
    } else {
      digitPaint.textAlign = Paint.Align.LEFT
      val timeStr = getHoursString(now)
      val timeWidth = digitPaint.measureText(timeStr)
      val amPmStr = getAmPmString(now)
      val amPmWidth = ampmPaint.measureText(amPmStr)
      val ampmSpacing = AM_PM_SPACING * scale
      val totalWidth = timeWidth + amPmWidth + ampmSpacing

      if (wrapped) {
        val bgPadding = getBgPadding()
        val bgCornerRadius = getBgCornerRadius()
        canvas.drawRoundRect(
          centerX - totalWidth / 2f - bgPadding,
          baseline + timeMetrics.top - bgPadding,
          centerX + totalWidth / 2f + bgPadding,
          baseline + timeMetrics.bottom + bgPadding,
          bgCornerRadius,
          bgCornerRadius,
          bgPaint
        )
      }

      canvas.drawText(timeStr, centerX - totalWidth / 2f, baseline, digitPaint)
      canvas.drawText(amPmStr, centerX + ampmSpacing + timeWidth - (totalWidth / 2f), baseline, ampmPaint)
    }

    if (animating) {
      scheduleSelf(this::invalidateSelf, SystemClock.uptimeMillis() + 1000)
    }
  }

  fun nextStyle() {
    setStyle(displayStyle.next())
  }

  fun setStyle(style: Style) {
    displayStyle = style
    when (style) {
      Style.LIGHT_NO_BG -> styleWhiteTextNoBg()
      Style.DARK_NO_BG -> styleBlackTextNoBg()
      Style.LIGHT -> styleLightWithBg()
      Style.DARK -> styleDarkWithBg()
      Style.DARK_WITH_RED_TEXT -> styleDarkWithRedText()
    }
    onBoundsChange(bounds)
  }

  fun getStyle(): Style {
    return displayStyle
  }

  private fun styleWhiteTextNoBg() {
    digitPaint.color = Color.WHITE
    ampmPaint.color = Color.WHITE
    wrapped = false
  }

  private fun styleBlackTextNoBg() {
    digitPaint.color = Color.BLACK
    ampmPaint.color = Color.BLACK
    wrapped = false
  }

  private fun styleLightWithBg() {
    digitPaint.color = Color.WHITE
    ampmPaint.color = Color.WHITE
    bgPaint.color = LIGHT_BG_COLOR
    wrapped = true
  }

  private fun styleDarkWithBg() {
    digitPaint.color = Color.WHITE
    ampmPaint.color = Color.WHITE
    bgPaint.color = DARK_BG_COLOR
    wrapped = true
  }

  private fun styleDarkWithRedText() {
    digitPaint.color = RED_TEXT_COLOR
    ampmPaint.color = RED_TEXT_COLOR
    bgPaint.color = DARK_BG_COLOR
    wrapped = true
  }

  private fun getBgPadding(): Float {
    return BG_PADDING * scale
  }

  private fun getBgCornerRadius(): Float {
    return BG_CORNER_RADIUS * scale
  }

  private fun getAmPmString(time: LocalDateTime): String {
    return DateTimeFormatter.ofPattern("a", Locale.getDefault()).format(time)
  }

  private fun getHoursString(time: LocalDateTime): String {
    return if (!DateFormat.is24HourFormat(context)) {
      DateTimeFormatter.ofPattern("h:mm", Locale.getDefault()).format(time)
    } else {
      DateTimeFormatter.ofPattern("H:mm", Locale.getDefault()).format(time)
    }
  }

  fun setTime(newTime: Long?) {
    time = newTime
    invalidateSelf()
  }

  override fun setAlpha(alpha: Int) = Unit
  override fun setColorFilter(colorFilter: ColorFilter?) = Unit

  override fun getOpacity(): Int {
    return PixelFormat.TRANSLUCENT
  }

  override fun onBoundsChange(bounds: Rect) {
    val dimension = min(bounds.width(), bounds.height())
    scale = (dimension / STICKER_BOX_SIZE) * STICKER_SCALING_ADJUSTMENT
    digitPaint.textSize = scale * TIME_TEXT_SIZE
    ampmPaint.textSize = scale * AM_PM_TEXT_SIZE
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

  enum class Style(val type: Int) {
    LIGHT_NO_BG(0),
    DARK_NO_BG(1),
    LIGHT(2),
    DARK(3),
    DARK_WITH_RED_TEXT(4);

    fun next(): Style {
      val values = Style.values()

      return values[(values.indexOf(this) + 1) % values.size]
    }

    companion object {
      fun fromType(type: Int) = Style.values().first { it.type == type }
    }
  }
}
