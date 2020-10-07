package org.thoughtcrime.securesms.components

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import network.loki.messenger.R
import java.lang.IllegalArgumentException
import java.lang.Math.abs

class WaveformSeekBar : View {

    companion object {
        @JvmStatic
        inline fun dp(context: Context, dp: Float): Float {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.resources.displayMetrics
            )
        }

        @JvmStatic
        inline fun smooth(values: FloatArray, neighborWeight: Float = 1f): FloatArray {
            if (values.size < 3) return values

            val result = FloatArray(values.size)
            result[0] = values[0]
            result[values.size - 1] == values[values.size - 1]
            for (i in 1 until values.size - 1) {
                result[i] =
                    (values[i] + values[i - 1] * neighborWeight + values[i + 1] * neighborWeight) / (1f + neighborWeight * 2f)
            }
            return result
        }
    }

    var sample: FloatArray = floatArrayOf(0f)
        set(value) {
            if (value.isEmpty()) throw IllegalArgumentException("Sample array cannot be empty")

//            field = smooth(value, 0.25f)
            field = value
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

    var waveBackgroundColor: Int = Color.LTGRAY
        set(value) {
            field = value
            invalidate()
        }

    var waveProgressColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    var waveGap: Float =
        dp(
            context,
            2f
        )
        set(value) {
            field = value
            invalidate()
        }

    var waveWidth: Float =
        dp(
            context,
            5f
        )
        set(value) {
            field = value
            invalidate()
        }

    var waveMinHeight: Float = waveWidth
        set(value) {
            field = value
            invalidate()
        }

    var waveCornerRadius: Float =
        dp(
            context,
            2.5f
        )
        set(value) {
            field = value
            invalidate()
        }

    var waveGravity: WaveGravity =
        WaveGravity.CENTER
        set(value) {
            field = value
            invalidate()
        }

    var progressChangeListener: ProgressChangeListener? = null

    private val postponedProgressUpdateHandler = Handler(Looper.getMainLooper())
    private val postponedProgressUpdateRunnable = Runnable {
        progressChangeListener?.onProgressChanged(this, progress, true)
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waveRect = RectF()
    private val progressCanvas = Canvas()

    private var canvasWidth = 0
    private var canvasHeight = 0
    private var maxValue =
        dp(
            context,
            2f
        )
    private var touchDownX = 0f
    private var scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
            : super(context, attrs, defStyleAttr) {

        val typedAttrs = context.obtainStyledAttributes(attrs,
            R.styleable.WaveformSeekBar
        )

        waveWidth = typedAttrs.getDimension(R.styleable.WaveformSeekBar_wave_width, waveWidth)
        waveGap = typedAttrs.getDimension(R.styleable.WaveformSeekBar_wave_gap, waveGap)
        waveCornerRadius = typedAttrs.getDimension(
            R.styleable.WaveformSeekBar_wave_corner_radius,
            waveCornerRadius
        )
        waveMinHeight =
            typedAttrs.getDimension(R.styleable.WaveformSeekBar_wave_min_height, waveMinHeight)
        waveBackgroundColor = typedAttrs.getColor(
            R.styleable.WaveformSeekBar_wave_background_color,
            waveBackgroundColor
        )
        waveProgressColor =
            typedAttrs.getColor(R.styleable.WaveformSeekBar_wave_progress_color, waveProgressColor)
        progress = typedAttrs.getFloat(R.styleable.WaveformSeekBar_wave_progress, progress)
        waveGravity =
            WaveGravity.fromString(
                typedAttrs.getString(R.styleable.WaveformSeekBar_wave_gravity)
            )

        typedAttrs.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        canvasWidth = w
        canvasHeight = h
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val totalWidth = getAvailableWith()

        maxValue = sample.max()!!
        val step = (totalWidth / (waveGap + waveWidth)) / sample.size

        var lastWaveRight = paddingLeft.toFloat()

        var i = 0f
        while (i < sample.size) {

            var waveHeight = if (maxValue != 0f) {
                getAvailableHeight() * (sample[i.toInt()] / maxValue)
            } else {
                waveMinHeight
            }

            if (waveHeight < waveMinHeight) {
                waveHeight = waveMinHeight
            }

            val top: Float = when (waveGravity) {
                WaveGravity.TOP -> paddingTop.toFloat()
                WaveGravity.CENTER -> paddingTop + getAvailableHeight() / 2f - waveHeight / 2f
                WaveGravity.BOTTOM -> canvasHeight - paddingBottom - waveHeight
            }

            waveRect.set(lastWaveRight, top, lastWaveRight + waveWidth, top + waveHeight)

            wavePaint.color = if (waveRect.right <= totalWidth * progress)
                waveProgressColor else waveBackgroundColor

            canvas.drawRoundRect(waveRect, waveCornerRadius, waveCornerRadius, wavePaint)

            lastWaveRight = waveRect.right + waveGap

            if (lastWaveRight + waveWidth > totalWidth + paddingLeft)
                break

            i += 1f / step
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
                    updateProgress(event, true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                updateProgress(event, true)
            }
            MotionEvent.ACTION_UP -> {
                userSeeking = false
                if (abs(event.x - touchDownX) > scaledTouchSlop) {
                    updateProgress(event, false)
                }

                performClick()
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

    private fun updateProgress(event: MotionEvent, delayNotification: Boolean) {
        _progress = event.x / getAvailableWith()
        invalidate()

        postponedProgressUpdateHandler.removeCallbacks(postponedProgressUpdateRunnable)
        if (delayNotification) {
            // Re-post delayed user update notification to throttle a bit.
            postponedProgressUpdateHandler.postDelayed(postponedProgressUpdateRunnable, 150)
        } else {
            postponedProgressUpdateRunnable.run()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun getAvailableWith() = canvasWidth - paddingLeft - paddingRight
    private fun getAvailableHeight() = canvasHeight - paddingTop - paddingBottom

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