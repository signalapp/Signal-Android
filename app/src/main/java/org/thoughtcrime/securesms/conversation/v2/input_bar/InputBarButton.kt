package org.thoughtcrime.securesms.conversation.v2.input_bar

import android.animation.PointFEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PointF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.annotation.DrawableRes
import network.loki.messenger.R
import org.thoughtcrime.securesms.conversation.v2.messages.VisibleMessageView
import org.thoughtcrime.securesms.loki.utilities.*
import org.thoughtcrime.securesms.loki.views.GlowViewUtilities
import org.thoughtcrime.securesms.loki.views.InputBarButtonImageViewContainer
import java.util.*
import kotlin.math.abs

class InputBarButton : RelativeLayout {
    private val gestureHandler = Handler(Looper.getMainLooper())
    private var isSendButton = false
    private var hasOpaqueBackground = false
    private var isGIFButton = false
    @DrawableRes private var iconID = 0
    private var longPressCallback: Runnable? = null
    private var onDownTimestamp = 0L
    var snIsEnabled = true
    var onPress: (() -> Unit)? = null
    var onMove: ((MotionEvent) -> Unit)? = null
    var onCancel: ((MotionEvent) -> Unit)? = null
    var onUp: ((MotionEvent) -> Unit)? = null
    var onLongPress: (() -> Unit)? = null

    companion object {
        const val animationDuration = 250.toLong()
        const val longPressDurationThreshold = 250L // ms
    }

    private val expandedImageViewPosition by lazy { PointF(0.0f, 0.0f) }
    private val collapsedImageViewPosition by lazy { PointF((expandedSize - collapsedSize) / 2, (expandedSize - collapsedSize) / 2) }
    private val colorID by lazy {
        if (hasOpaqueBackground) {
            R.color.input_bar_button_background_opaque
        } else if (isSendButton) {
            R.color.accent
        } else {
            R.color.input_bar_button_background
        }
    }

    val expandedSize by lazy { resources.getDimension(R.dimen.input_bar_button_expanded_size) }
    val collapsedSize by lazy { resources.getDimension(R.dimen.input_bar_button_collapsed_size) }

    private val imageViewContainer by lazy {
        val result = InputBarButtonImageViewContainer(context)
        val size = collapsedSize.toInt()
        result.layoutParams = LayoutParams(size, size)
        result.setBackgroundResource(R.drawable.input_bar_button_background)
        result.mainColor = resources.getColorWithID(colorID, context.theme)
        if (hasOpaqueBackground) {
            result.strokeColor = resources.getColorWithID(R.color.input_bar_button_background_opaque_border, context.theme)
        }
        result
    }

    private val imageView by lazy {
        val result = ImageView(context)
        val size = if (isGIFButton) toPx(24, resources) else toPx(16, resources)
        result.layoutParams = LayoutParams(size, size)
        result.scaleType = ImageView.ScaleType.CENTER_INSIDE
        result.setImageResource(iconID)
        val colorID = if (isSendButton) R.color.black else R.color.text
        result.imageTintList = ColorStateList.valueOf(resources.getColorWithID(colorID, context.theme))
        result
    }

    constructor(context: Context) : super(context) { throw IllegalAccessException("Use InputBarButton(context:iconID:) instead.") }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { throw IllegalAccessException("Use InputBarButton(context:iconID:) instead.") }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { throw IllegalAccessException("Use InputBarButton(context:iconID:) instead.") }

    constructor(context: Context, @DrawableRes iconID: Int, isSendButton: Boolean = false,
        hasOpaqueBackground: Boolean = false, isGIFButton: Boolean = false) : super(context) {
        this.isSendButton = isSendButton
        this.iconID = iconID
        this.hasOpaqueBackground = hasOpaqueBackground
        this.isGIFButton = isGIFButton
        val size = resources.getDimension(R.dimen.input_bar_button_expanded_size).toInt()
        val layoutParams = LayoutParams(size, size)
        this.layoutParams = layoutParams
        addView(imageViewContainer)
        imageViewContainer.x = collapsedImageViewPosition.x
        imageViewContainer.y = collapsedImageViewPosition.y
        imageViewContainer.addView(imageView)
        val imageViewLayoutParams = imageView.layoutParams as LayoutParams
        imageViewLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT)
        imageView.layoutParams = imageViewLayoutParams
        gravity = Gravity.TOP or Gravity.LEFT // Intentionally not Gravity.START
        isHapticFeedbackEnabled = true
    }

    fun expand() {
        GlowViewUtilities.animateColorChange(context, imageViewContainer, colorID, R.color.accent)
        imageViewContainer.animateSizeChange(R.dimen.input_bar_button_collapsed_size, R.dimen.input_bar_button_expanded_size, animationDuration)
        animateImageViewContainerPositionChange(collapsedImageViewPosition, expandedImageViewPosition)
    }

    fun collapse() {
        GlowViewUtilities.animateColorChange(context, imageViewContainer, R.color.accent, colorID)
        imageViewContainer.animateSizeChange(R.dimen.input_bar_button_expanded_size, R.dimen.input_bar_button_collapsed_size, animationDuration)
        animateImageViewContainerPositionChange(expandedImageViewPosition, collapsedImageViewPosition)
    }

    private fun animateImageViewContainerPositionChange(startPosition: PointF, endPosition: PointF) {
        val animation = ValueAnimator.ofObject(PointFEvaluator(), startPosition, endPosition)
        animation.duration = animationDuration
        animation.addUpdateListener { animator ->
            val point = animator.animatedValue as PointF
            imageViewContainer.x = point.x
            imageViewContainer.y = point.y
        }
        animation.start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!snIsEnabled) { return false }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> onDown(event)
            MotionEvent.ACTION_MOVE -> onMove(event)
            MotionEvent.ACTION_UP -> onUp(event)
            MotionEvent.ACTION_CANCEL -> onCancel(event)
        }
        return true
    }

    private fun onDown(event: MotionEvent) {
        expand()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        longPressCallback?.let { gestureHandler.removeCallbacks(it) }
        val newLongPressCallback = Runnable { onLongPress?.invoke() }
        this.longPressCallback = newLongPressCallback
        gestureHandler.postDelayed(newLongPressCallback, InputBarButton.longPressDurationThreshold)
        onDownTimestamp = Date().time
    }

    private fun onMove(event: MotionEvent) {
        onMove?.invoke(event)
    }

    private fun onCancel(event: MotionEvent) {
        onCancel?.invoke(event)
        collapse()
        longPressCallback?.let { gestureHandler.removeCallbacks(it) }
    }

    private fun onUp(event: MotionEvent) {
        onUp?.invoke(event)
        collapse()
        if ((Date().time - onDownTimestamp) < InputBarButton.longPressDurationThreshold) {
            longPressCallback?.let { gestureHandler.removeCallbacks(it) }
            onPress?.invoke()
        }
    }
}