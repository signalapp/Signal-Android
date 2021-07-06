package org.thoughtcrime.securesms.loki.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.core.math.MathUtils
import network.loki.messenger.R
import org.session.libsession.utilities.byteToNormalizedFloat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WaveformSeekBar : View {

    companion object {
        @JvmStatic
        fun dp(context: Context, dp: Float): Float {
            return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    dp,
                    context.resources.displayMetrics
            )
        }
    }

    private val sampleDataHolder = SampleDataHolder(::invalidate)
    /** An array of signed byte values representing the audio signal. */
    var sampleData: ByteArray?
        get() {
            return sampleDataHolder.getSamples()
        }
        set(value) {
            sampleDataHolder.setSamples(value)
            invalidate()
        }

    /** Indicates whether the user is currently interacting with the view and performing a seeking gesture. */
    private var userSeeking = false
    private var _progress: Float = 0f
    /** In [0..1] range. */
    var progress: Float
        set(value) {
            // Do not let to modify the progress value from the outside
            // when the user is currently interacting with the view.
            if (userSeeking) return

            _progress = value
            invalidate()
            progressChangeListener?.onProgressChanged(this, _progress, false)
        }
        get() {
            return _progress
        }

    var barBackgroundColor: Int = Color.LTGRAY
        set(value) {
            field = value
            invalidate()
        }

    var barProgressColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    var barGap: Float = dp(context, 2f)
        set(value) {
            field = value
            invalidate()
        }

    var barWidth: Float = dp(context, 5f)
        set(value) {
            field = value
            invalidate()
        }

    var barMinHeight: Float = barWidth
        set(value) {
            field = value
            invalidate()
        }

    var barCornerRadius: Float = dp(context, 2.5f)
        set(value) {
            field = value
            invalidate()
        }

    var barGravity: WaveGravity = WaveGravity.CENTER
        set(value) {
            field = value
            invalidate()
        }

    var progressChangeListener: ProgressChangeListener? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barRect = RectF()

    private var canvasWidth = 0
    private var canvasHeight = 0

    private var touchDownX = 0f
    private var touchDownProgress: Float = 0f
    private var scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
            : super(context, attrs, defStyleAttr) {

        val typedAttrs = context.obtainStyledAttributes(attrs, R.styleable.WaveformSeekBar)
        barWidth = typedAttrs.getDimension(R.styleable.WaveformSeekBar_bar_width, barWidth)
        barGap = typedAttrs.getDimension(R.styleable.WaveformSeekBar_bar_gap, barGap)
        barCornerRadius = typedAttrs.getDimension(
                R.styleable.WaveformSeekBar_bar_corner_radius,
                barCornerRadius)
        barMinHeight =
                typedAttrs.getDimension(R.styleable.WaveformSeekBar_bar_min_height, barMinHeight)
        barBackgroundColor = typedAttrs.getColor(
                R.styleable.WaveformSeekBar_bar_background_color,
                barBackgroundColor)
        barProgressColor =
                typedAttrs.getColor(R.styleable.WaveformSeekBar_bar_progress_color, barProgressColor)
        progress = typedAttrs.getFloat(R.styleable.WaveformSeekBar_progress, progress)
        barGravity = WaveGravity.fromString(
                typedAttrs.getString(R.styleable.WaveformSeekBar_bar_gravity))

        typedAttrs.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        canvasWidth = w
        canvasHeight = h
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val totalWidth = getAvailableWidth()
        val barAmount = (totalWidth / (barWidth + barGap)).toInt()

        var lastBarRight = paddingLeft.toFloat()

        (0 until barAmount).forEach { barIdx ->
            // Convert a signed byte to a [0..1] float.
            val barValue = byteToNormalizedFloat(sampleDataHolder.computeBarValue(barIdx, barAmount))

            val barHeight = max(barMinHeight, getAvailableHeight() * barValue)

            val top: Float = when (barGravity) {
                WaveGravity.TOP -> paddingTop.toFloat()
                WaveGravity.CENTER -> paddingTop + getAvailableHeight() * 0.5f - barHeight * 0.5f
                WaveGravity.BOTTOM -> canvasHeight - paddingBottom - barHeight
            }

            barRect.set(lastBarRight, top, lastBarRight + barWidth, top + barHeight)

            barPaint.color = if (barRect.right <= totalWidth * progress)
                barProgressColor else barBackgroundColor

            canvas.drawRoundRect(barRect, barCornerRadius, barCornerRadius, barPaint)

            lastBarRight = barRect.right + barGap
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                userSeeking = true
                touchDownX = event.x
                touchDownProgress = progress
                updateProgress(event, false)
            }
            MotionEvent.ACTION_MOVE -> {
                // Prevent any parent scrolling if the user scrolled more
                // than scaledTouchSlop on horizontal axis.
                if (abs(event.x - touchDownX) > scaledTouchSlop) {
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                updateProgress(event, false)
            }
            MotionEvent.ACTION_UP -> {
                userSeeking = false
                updateProgress(event, true)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                updateProgress(touchDownProgress, false)
                userSeeking = false
            }
        }
        return true
    }

    private fun updateProgress(event: MotionEvent, notify: Boolean) {
        updateProgress(event.x / getAvailableWidth(), notify)
    }

    private fun updateProgress(progress: Float, notify: Boolean) {
        _progress = MathUtils.clamp(progress, 0f, 1f)
        invalidate()

        if (notify) {
            progressChangeListener?.onProgressChanged(this, _progress, true)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun getAvailableWidth() = canvasWidth - paddingLeft - paddingRight
    private fun getAvailableHeight() = canvasHeight - paddingTop - paddingBottom

    private class SampleDataHolder(private val invalidateDelegate: () -> Any) {

        private var sampleDataFrom: ByteArray? = null
        private var sampleDataTo: ByteArray? = null
        private var progress = 1f // Mix between from and to values.

        private var animation: ValueAnimator? = null

        fun computeBarValue(barIdx: Int, barAmount: Int): Byte {
            /** @return The array's value at the interpolated index. */
            fun getSampleValue(sampleData: ByteArray?): Byte {
                if (sampleData == null || sampleData.isEmpty())
                    return Byte.MIN_VALUE
                else {
                    val sampleIdx = (barIdx * (sampleData.size / barAmount.toFloat())).toInt()
                    return sampleData[sampleIdx]
                }
            }

            if (progress == 1f) {
                return getSampleValue(sampleDataTo)
            }

            val fromValue = getSampleValue(sampleDataFrom)
            val toValue = getSampleValue(sampleDataTo)
            val rawResultValue = fromValue * (1f - progress) + toValue * progress
            return rawResultValue.roundToInt().toByte()
        }

        fun setSamples(sampleData: ByteArray?) {
            /** @return a mix between [sampleDataFrom] and [sampleDataTo] arrays according to the current [progress] value. */
            fun computeNewDataFromArray(): ByteArray? {
                if (sampleDataTo == null) return null
                if (sampleDataFrom == null) return sampleDataTo

                val sampleSize = min(sampleDataFrom!!.size, sampleDataTo!!.size)
                return ByteArray(sampleSize) { i -> computeBarValue(i, sampleSize) }
            }

            sampleDataFrom = computeNewDataFromArray()
            sampleDataTo = sampleData
            progress = 0f

            animation?.cancel()
            animation = ValueAnimator.ofFloat(0f, 1f).apply {
                addUpdateListener { animation ->
                    progress = animation.animatedValue as Float
                    invalidateDelegate()
                }
                interpolator = DecelerateInterpolator(3f)
                duration = 500
                start()
            }
        }

        fun getSamples(): ByteArray? {
            return sampleDataTo
        }
    }

    enum class WaveGravity {
        TOP,
        CENTER,
        BOTTOM,
        ;

        companion object {
            @JvmStatic
            fun fromString(gravity: String?): WaveGravity = when (gravity) {
                "1" -> TOP
                "2" -> CENTER
                else -> BOTTOM
            }
        }
    }

    interface ProgressChangeListener {
        fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean)
    }
}