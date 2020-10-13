package org.thoughtcrime.securesms.loki.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.core.math.MathUtils
import network.loki.messenger.R
import java.lang.Math.abs
import kotlin.math.max

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
    /** An array if normalized to [0..1] values representing the audio signal. */
    var sampleData: FloatArray?
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
            val barValue = sampleDataHolder.computeBarValue(barIdx, barAmount)

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
                if (isParentScrolling()) {
                    touchDownX = event.x
                } else {
                    updateProgress(event, false)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                updateProgress(event, false)
            }
            MotionEvent.ACTION_UP -> {
                userSeeking = false
                if (abs(event.x - touchDownX) > scaledTouchSlop) {
                    updateProgress(event, true)
                }
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                userSeeking = false
            }
        }
        return true
    }

    private fun isParentScrolling(): Boolean {
        var parent = parent as View
        val root = rootView

        while (true) {
            when {
                parent.canScrollHorizontally(+1) -> return true
                parent.canScrollHorizontally(-1) -> return true
                parent.canScrollVertically(+1) -> return true
                parent.canScrollVertically(-1) -> return true
            }

            if (parent == root) return false

            parent = parent.parent as View
        }
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

        private var sampleDataFrom: FloatArray? = null
        private var sampleDataTo: FloatArray? = null
        private var progress = 1f // Mix between from and to values.

        private var animation: ValueAnimator? = null

        fun computeBarValue(barIdx: Int, barAmount: Int): Float {
            fun getSampleValue(sampleData: FloatArray?): Float {
                if (sampleData == null || sampleData.isEmpty())
                    return 0f
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

            return fromValue * (1f - progress) + toValue * progress
        }

        fun setSamples(sampleData: FloatArray?) {
            sampleDataFrom = sampleDataTo
            sampleDataTo = sampleData
            progress = 0f

            animation?.cancel()
            animation = ValueAnimator.ofFloat(0f, 1f).apply {
                addUpdateListener { animation ->
                    progress = animation.animatedValue as Float
                    Log.d("MTPHR", "Progress: $progress")
                    invalidateDelegate()
                }
                interpolator = DecelerateInterpolator(3f)
                duration = 500
                start()
            }
        }

        fun getSamples(): FloatArray? {
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