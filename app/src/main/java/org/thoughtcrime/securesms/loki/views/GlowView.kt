package org.thoughtcrime.securesms.loki.views

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import network.loki.messenger.R
import org.thoughtcrime.securesms.loki.utilities.getColorWithID
import org.thoughtcrime.securesms.loki.utilities.toPx
import kotlin.math.roundToInt

interface GlowView {
    var mainColor: Int
    var sessionShadowColor: Int
}

object GlowViewUtilities {

    fun animateColorChange(context: Context, view: GlowView, @ColorRes startColorID: Int, @ColorRes endColorID: Int) {
        val startColor = context.resources.getColorWithID(startColorID, context.theme)
        val endColor = context.resources.getColorWithID(endColorID, context.theme)
        val animation = ValueAnimator.ofObject(ArgbEvaluator(), startColor, endColor)
        animation.duration = 250
        animation.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            view.mainColor = color
        }
        animation.start()
    }

    fun animateShadowColorChange(context: Context, view: GlowView, @ColorRes startColorID: Int, @ColorRes endColorID: Int) {
        val startColor = context.resources.getColorWithID(startColorID, context.theme)
        val endColor = context.resources.getColorWithID(endColorID, context.theme)
        val animation = ValueAnimator.ofObject(ArgbEvaluator(), startColor, endColor)
        animation.duration = 250
        animation.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            view.sessionShadowColor = color
        }
        animation.start()
    }
}

class PNModeView : LinearLayout, GlowView {
    @ColorInt override var mainColor: Int = 0
        set(newValue) { field = newValue; paint.color = newValue }
    @ColorInt var strokeColor: Int = 0
        set(newValue) { field = newValue; strokePaint.color = newValue }
    @ColorInt override var sessionShadowColor: Int = 0
        set(newValue) { field = newValue; paint.setShadowLayer(toPx(4, resources).toFloat(), 0.0f, 0.0f, newValue) }

    private val paint: Paint by lazy {
        val result = Paint()
        result.style = Paint.Style.FILL
        result.isAntiAlias = true
        result
    }

    private val strokePaint: Paint by lazy {
        val result = Paint()
        result.style = Paint.Style.STROKE
        result.isAntiAlias = true
        result.strokeWidth = toPx(1, resources).toFloat()
        result
    }

    // region Lifecycle
    constructor(context: Context) : super(context) { }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { }

    init {
        setWillNotDraw(false)
    }
    // endregion

    // region Updating
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = resources.getDimension(R.dimen.pn_option_corner_radius)
        c.drawRoundRect(0.0f, 0.0f, w, h, r, r, paint)
        c.drawRoundRect(0.0f, 0.0f, w, h, r, r, strokePaint)
        super.onDraw(c)
    }
    // endregion
}

class NewConversationButtonImageView : androidx.appcompat.widget.AppCompatImageView, GlowView {
    @ColorInt override var mainColor: Int = 0
    set(newValue) { field = newValue; paint.color = newValue }
    @ColorInt override var sessionShadowColor: Int = 0
    set(newValue) { field = newValue; paint.setShadowLayer(toPx(6, resources).toFloat(), 0.0f, 0.0f, newValue) }

    private val paint: Paint by lazy {
        val result = Paint()
        result.style = Paint.Style.FILL
        result.isAntiAlias = true
        result
    }

    // region Lifecycle
    constructor(context: Context) : super(context) { }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { }

    init {
        setWillNotDraw(false)
    }
    // endregion

    // region Updating
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawCircle(w / 2, h / 2, w / 2, paint)
        super.onDraw(c)
    }
    // endregion
}

class PathDotView : View, GlowView {
    @ColorInt override var mainColor: Int = 0
        set(newValue) { field = newValue; paint.color = newValue }
    @ColorInt override var sessionShadowColor: Int = 0
        set(newValue) { field = newValue; paint.setShadowLayer(toPx(4, resources).toFloat(), 0.0f, 0.0f, newValue) }

    private val paint: Paint by lazy {
        val result = Paint()
        result.style = Paint.Style.FILL
        result.isAntiAlias = true
        result
    }

    // region Lifecycle
    constructor(context: Context) : super(context) { }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { }

    init {
        setWillNotDraw(false)
    }
    // endregion

    // region Updating
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawCircle(w / 2, h / 2, w / 2, paint)
        super.onDraw(c)
    }
    // endregion
}

class InputBarButtonImageViewContainer : RelativeLayout, GlowView {
    @ColorInt override var mainColor: Int = 0
        set(newValue) { field = newValue; fillPaint.color = newValue }
    @ColorInt var strokeColor: Int = 0
        set(newValue) { field = newValue; strokePaint.color = newValue }
    @ColorInt override var sessionShadowColor: Int = 0 // Unused

    private val fillPaint: Paint by lazy {
        val result = Paint()
        result.style = Paint.Style.FILL
        result.isAntiAlias = true
        result
    }

    private val strokePaint: Paint by lazy {
        val result = Paint()
        result.style = Paint.Style.STROKE
        result.isAntiAlias = true
        result.strokeWidth = 1.0f
        result.alpha = (255 * 0.2f).roundToInt()
        result
    }

    // region Lifecycle
    constructor(context: Context) : super(context) { }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { }

    init {
        setWillNotDraw(false)
    }
    // endregion

    // region Updating
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawCircle(w / 2, h / 2, w / 2, fillPaint)
        if (strokeColor != 0) {
            c.drawCircle(w / 2, h / 2, w / 2, strokePaint)
        }
        super.onDraw(c)
    }
    // endregion
}
