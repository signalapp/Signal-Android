package org.thoughtcrime.securesms.conversation.v2.utilities

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import org.thoughtcrime.securesms.util.getAccentColor
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
        color = context.getAccentColor()
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