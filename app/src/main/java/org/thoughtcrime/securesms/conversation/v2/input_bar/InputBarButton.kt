package org.thoughtcrime.securesms.conversation.v2.input_bar

import android.animation.PointFEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PointF
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.annotation.DrawableRes
import network.loki.messenger.R
import org.thoughtcrime.securesms.loki.utilities.*
import org.thoughtcrime.securesms.loki.views.GlowViewUtilities
import org.thoughtcrime.securesms.loki.views.InputBarButtonImageViewContainer

class InputBarButton : RelativeLayout {
    private var isSendButton = false
    @DrawableRes private var iconID = 0

    companion object {
        val animationDuration = 250.toLong()
    }

    private val expandedImageViewPosition by lazy { PointF(0.0f, 0.0f) }
    private val collapsedImageViewPosition by lazy { PointF((expandedSize - collapsedSize) / 2, (expandedSize - collapsedSize) / 2) }
    private val defaultColorID by lazy { if (isSendButton) R.color.accent else R.color.input_bar_button_background }

    val expandedSize by lazy { resources.getDimension(R.dimen.input_bar_button_expanded_size) }
    val collapsedSize by lazy { resources.getDimension(R.dimen.input_bar_button_collapsed_size) }

    private val imageViewContainer by lazy {
        val result = InputBarButtonImageViewContainer(context)
        val size = collapsedSize.toInt()
        result.layoutParams = LayoutParams(size, size)
        result.setBackgroundResource(R.drawable.input_bar_button_background)
        result.mainColor = resources.getColorWithID(defaultColorID, context.theme)
        result
    }

    private val imageView by lazy {
        val result = ImageView(context)
        val size = toPx(16, resources)
        result.layoutParams = LayoutParams(size, size)
        result.scaleType = ImageView.ScaleType.CENTER_INSIDE
        result.setImageResource(iconID)
        result.imageTintList = ColorStateList.valueOf(resources.getColorWithID(R.color.text, context.theme))
        result
    }

    constructor(context: Context) : super(context) { throw IllegalAccessException("Use InputBarButton(context:iconID:) instead.") }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { throw IllegalAccessException("Use InputBarButton(context:iconID:) instead.") }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { throw IllegalAccessException("Use InputBarButton(context:iconID:) instead.") }

    constructor(context: Context, @DrawableRes iconID: Int, isSendButton: Boolean = false) : super(context) {
        this.isSendButton = isSendButton
        this.iconID = iconID
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
        GlowViewUtilities.animateColorChange(context, imageViewContainer, defaultColorID, R.color.accent)
        imageViewContainer.animateSizeChange(R.dimen.input_bar_button_collapsed_size, R.dimen.input_bar_button_expanded_size, animationDuration)
        animateImageViewContainerPositionChange(collapsedImageViewPosition, expandedImageViewPosition)
    }

    fun collapse() {
        GlowViewUtilities.animateColorChange(context, imageViewContainer, R.color.accent, defaultColorID)
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
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                expand()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                } else {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { collapse() }
        }
        return true
    }
}