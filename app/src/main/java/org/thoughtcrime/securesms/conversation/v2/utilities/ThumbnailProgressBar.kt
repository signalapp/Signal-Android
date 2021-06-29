package org.thoughtcrime.securesms.conversation.v2.utilities

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Interpolator
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import androidx.core.content.res.ResourcesCompat
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import network.loki.messenger.R
import kotlin.math.sin

class ThumbnailProgressBar: View {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val firstX: Double
    get() = sin(SystemClock.elapsedRealtime() / 300.0) * 1.5

    private val secondX: Double
    get() = sin(SystemClock.elapsedRealtime() / 300.0 + (Math.PI/4)) * 1.5

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ResourcesCompat.getColor(resources, R.color.accent, null)
    }

    private val objectRect = Rect()
    private val drawingRect = Rect()

    override fun dispatchDraw(canvas: Canvas?) {
        if (canvas == null) return

        getDrawingRect(objectRect)
        drawingRect.set(objectRect)

        val coercedFX = firstX
        val coercedSX = secondX

        val firstMeasuredX = objectRect.left + (objectRect.width() * coercedFX)
        val secondMeasuredX = objectRect.left + (objectRect.width() * coercedSX)

        drawingRect.set(
                (if (firstMeasuredX < secondMeasuredX) firstMeasuredX else secondMeasuredX).toInt(),
                objectRect.top,
                (if (firstMeasuredX < secondMeasuredX) secondMeasuredX else firstMeasuredX).toInt(),
                objectRect.bottom
        )

        canvas.drawRect(drawingRect, paint)
        invalidate()
    }
}