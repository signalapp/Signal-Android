package org.thoughtcrime.securesms.loki.views

import android.animation.FloatEvaluator
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
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import network.loki.messenger.R
import org.thoughtcrime.securesms.loki.utilities.*

class NewConversationButtonSetView : RelativeLayout {
    private var expandedButton: Button? = null
    private var previousAction: Int? = null
    private var isExpanded = false
    var delegate: NewConversationButtonSetViewDelegate? = null

    // region Convenience
    private val sessionButtonExpandedPosition: PointF get() { return PointF(width.toFloat() / 2 - sessionButton.expandedSize / 2 - sessionButton.shadowMargin, 0.0f) }
    private val closedGroupButtonExpandedPosition: PointF get() { return PointF(width.toFloat() - closedGroupButton.expandedSize - 2 * closedGroupButton.shadowMargin, height.toFloat() - bottomMargin - closedGroupButton.expandedSize - 2 * closedGroupButton.shadowMargin) }
    private val openGroupButtonExpandedPosition: PointF get() { return PointF(0.0f, height.toFloat() - bottomMargin - openGroupButton.expandedSize - 2 * openGroupButton.shadowMargin) }
    private val buttonRestPosition: PointF get() { return PointF(width.toFloat() / 2 - mainButton.expandedSize / 2 - mainButton.shadowMargin, height.toFloat() - bottomMargin - mainButton.expandedSize - 2 * mainButton.shadowMargin) }
    // endregion

    // region Settings
    private val minDragDistance by lazy { toPx(40, resources).toFloat() }
    private val maxDragDistance by lazy { toPx(56, resources).toFloat() }
    private val dragMargin by lazy { toPx(16, resources).toFloat() }
    private val bottomMargin by lazy { resources.getDimension(R.dimen.new_conversation_button_bottom_offset) }
    // endregion

    // region Components
    private val mainButton by lazy { Button(context, true, R.drawable.ic_plus) }
    private val sessionButton by lazy { Button(context, false, R.drawable.ic_message) }
    private val closedGroupButton by lazy { Button(context, false, R.drawable.ic_group) }
    private val openGroupButton by lazy { Button(context, false, R.drawable.ic_globe) }
    // endregion

    // region Button
    class Button : RelativeLayout {
        @DrawableRes private var iconID = 0
        private var isMain = false

        companion object {
            val animationDuration = 250.toLong()
        }

        val expandedSize by lazy { resources.getDimension(R.dimen.new_conversation_button_expanded_size) }
        val collapsedSize by lazy { resources.getDimension(R.dimen.new_conversation_button_collapsed_size) }
        val shadowMargin by lazy { toPx(6, resources).toFloat() }
        private val expandedImageViewPosition by lazy { PointF(shadowMargin, shadowMargin) }
        private val collapsedImageViewPosition by lazy { PointF(shadowMargin + (expandedSize - collapsedSize) / 2, shadowMargin + (expandedSize - collapsedSize) / 2) }

        private val imageView by lazy {
            val result = NewConversationButtonImageView(context)
            val size = collapsedSize.toInt()
            result.layoutParams = LayoutParams(size, size)
            result.setBackgroundResource(R.drawable.new_conversation_button_background)
            @ColorRes val backgroundColorID = if (isMain) R.color.accent else R.color.new_conversation_button_collapsed_background
            @ColorRes val shadowColorID = if (isMain) {
                R.color.new_conversation_button_shadow
            } else {
                if (UiModeUtilities.isDayUiMode(context)) R.color.transparent_black_30 else R.color.black
            }
            result.mainColor = resources.getColorWithID(backgroundColorID, context.theme)
            result.sessionShadowColor = resources.getColorWithID(shadowColorID, context.theme)
            result.scaleType = ImageView.ScaleType.CENTER
            result.setImageResource(iconID)
            result.imageTintList = if (isMain) {
                ColorStateList.valueOf(resources.getColorWithID(android.R.color.white, context.theme))
            } else {
                ColorStateList.valueOf(resources.getColorWithID(R.color.text, context.theme))
            }
            result
        }

        constructor(context: Context) : super(context) { throw IllegalAccessException("Use Button(context:iconID:) instead.") }
        constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { throw IllegalAccessException("Use Button(context:iconID:) instead.") }
        constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { throw IllegalAccessException("Use Button(context:iconID:) instead.") }
        constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) { throw IllegalAccessException("Use Button(context:iconID:) instead.") }

        constructor(context: Context, isMain: Boolean, @DrawableRes iconID: Int) : super(context) {
            this.iconID = iconID
            this.isMain = isMain
            disableClipping()
            val size = resources.getDimension(R.dimen.new_conversation_button_expanded_size).toInt() + 2 * shadowMargin.toInt()
            val layoutParams = LayoutParams(size, size)
            this.layoutParams = layoutParams
            addView(imageView)
            imageView.x = collapsedImageViewPosition.x
            imageView.y = collapsedImageViewPosition.y
            gravity = Gravity.TOP or Gravity.LEFT // Intentionally not Gravity.START
        }

        fun expand() {
            GlowViewUtilities.animateColorChange(context, imageView, R.color.new_conversation_button_collapsed_background, R.color.accent)
            @ColorRes val startShadowColorID = if (UiModeUtilities.isDayUiMode(context)) R.color.transparent_black_30 else R.color.black
            GlowViewUtilities.animateShadowColorChange(context, imageView, startShadowColorID, R.color.new_conversation_button_shadow)
            imageView.animateSizeChange(R.dimen.new_conversation_button_collapsed_size, R.dimen.new_conversation_button_expanded_size, animationDuration)
            animateImageViewPositionChange(collapsedImageViewPosition, expandedImageViewPosition)
        }

        fun collapse() {
            GlowViewUtilities.animateColorChange(context, imageView, R.color.accent, R.color.new_conversation_button_collapsed_background)
            @ColorRes val endShadowColorID = if (UiModeUtilities.isDayUiMode(context)) R.color.transparent_black_30 else R.color.black
            GlowViewUtilities.animateShadowColorChange(context, imageView, R.color.new_conversation_button_shadow, endShadowColorID)
            imageView.animateSizeChange(R.dimen.new_conversation_button_expanded_size, R.dimen.new_conversation_button_collapsed_size, animationDuration)
            animateImageViewPositionChange(expandedImageViewPosition, collapsedImageViewPosition)
        }

        private fun animateImageViewPositionChange(startPosition: PointF, endPosition: PointF) {
            val animation = ValueAnimator.ofObject(PointFEvaluator(), startPosition, endPosition)
            animation.duration = animationDuration
            animation.addUpdateListener { animator ->
                val point = animator.animatedValue as PointF
                imageView.x = point.x
                imageView.y = point.y
            }
            animation.start()
        }

        fun animatePositionChange(startPosition: PointF, endPosition: PointF) {
            val animation = ValueAnimator.ofObject(PointFEvaluator(), startPosition, endPosition)
            animation.duration = animationDuration
            animation.addUpdateListener { animator ->
                val point = animator.animatedValue as PointF
                x = point.x
                y = point.y
            }
            animation.start()
        }

        fun animateAlphaChange(startAlpha: Float, endAlpha: Float) {
            val animation = ValueAnimator.ofObject(FloatEvaluator(), startAlpha, endAlpha)
            animation.duration = animationDuration
            animation.addUpdateListener { animator ->
                alpha = animator.animatedValue as Float
            }
            animation.start()
        }
    }
    // endregion

    // region Lifecycle
    constructor(context: Context) : super(context) { setUpViewHierarchy() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { setUpViewHierarchy() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { setUpViewHierarchy() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) { setUpViewHierarchy() }

    private fun setUpViewHierarchy() {
        disableClipping()
        isHapticFeedbackEnabled = true
        // Set up session button
        addView(sessionButton)
        sessionButton.alpha = 0.0f
        val sessionButtonLayoutParams = sessionButton.layoutParams as LayoutParams
        sessionButtonLayoutParams.addRule(CENTER_IN_PARENT, TRUE)
        sessionButtonLayoutParams.addRule(ALIGN_PARENT_BOTTOM, TRUE)
        sessionButtonLayoutParams.bottomMargin = bottomMargin.toInt()
        // Set up closed group button
        addView(closedGroupButton)
        closedGroupButton.alpha = 0.0f
        val closedGroupButtonLayoutParams = closedGroupButton.layoutParams as LayoutParams
        closedGroupButtonLayoutParams.addRule(CENTER_IN_PARENT, TRUE)
        closedGroupButtonLayoutParams.addRule(ALIGN_PARENT_BOTTOM, TRUE)
        closedGroupButtonLayoutParams.bottomMargin = bottomMargin.toInt()
        // Set up open group button
        addView(openGroupButton)
        openGroupButton.alpha = 0.0f
        val openGroupButtonLayoutParams = openGroupButton.layoutParams as LayoutParams
        openGroupButtonLayoutParams.addRule(CENTER_IN_PARENT, TRUE)
        openGroupButtonLayoutParams.addRule(ALIGN_PARENT_BOTTOM, TRUE)
        openGroupButtonLayoutParams.bottomMargin = bottomMargin.toInt()
        // Set up main button
        addView(mainButton)
        val mainButtonLayoutParams = mainButton.layoutParams as LayoutParams
        mainButtonLayoutParams.addRule(CENTER_IN_PARENT, TRUE)
        mainButtonLayoutParams.addRule(ALIGN_PARENT_BOTTOM, TRUE)
        mainButtonLayoutParams.bottomMargin = bottomMargin.toInt()
    }
    // endregion

    // region Interaction
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val touch = PointF(event.x, event.y)
        val allButtons = listOf( mainButton, sessionButton, closedGroupButton, openGroupButton )
        val buttonsExcludingMainButton = listOf( sessionButton, closedGroupButton, openGroupButton )
        if (allButtons.none { it.contains(touch) }) { return false }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isExpanded) {
                    if (mainButton.contains(touch)) { collapse() }
                } else {
                    isExpanded = true
                    expand()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                } else {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                mainButton.x = touch.x - mainButton.expandedSize / 2
                mainButton.y = touch.y - mainButton.expandedSize / 2
                mainButton.alpha = 1 - (PointF(mainButton.x, mainButton.y).distanceTo(buttonRestPosition) / maxDragDistance)
                val buttonToExpand = buttonsExcludingMainButton.firstOrNull { button ->
                    var hasUserDraggedBeyondButton = false
                    if (button == openGroupButton && touch.isLeftOf(openGroupButton, dragMargin)) { hasUserDraggedBeyondButton = true }
                    if (button == sessionButton && touch.isAbove(sessionButton, dragMargin)) { hasUserDraggedBeyondButton = true }
                    if (button == closedGroupButton && touch.isRightOf(closedGroupButton, dragMargin)) { hasUserDraggedBeyondButton = true }
                    button.contains(touch) || hasUserDraggedBeyondButton
                }
                if (buttonToExpand != null) {
                    if (buttonToExpand == expandedButton) { return true }
                    expandedButton?.collapse()
                    buttonToExpand.expand()
                    this.expandedButton = buttonToExpand
                } else {
                    expandedButton?.collapse()
                    this.expandedButton = null
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val mainButtonCenter = PointF(width.toFloat() / 2, height.toFloat() - bottomMargin - mainButton.expandedSize / 2)
                val distanceFromMainButtonCenter = touch.distanceTo(mainButtonCenter)
                fun collapse() {
                    isExpanded = false
                    this.collapse()
                }
                if (distanceFromMainButtonCenter > (minDragDistance + mainButton.collapsedSize / 2)) {
                    if (sessionButton.contains(touch) || touch.isAbove(sessionButton, dragMargin)) { delegate?.createNewPrivateChat(); collapse() }
                    else if (closedGroupButton.contains(touch) || touch.isRightOf(closedGroupButton, dragMargin)) { delegate?.createNewClosedGroup(); collapse() }
                    else if (openGroupButton.contains(touch) || touch.isLeftOf(openGroupButton, dragMargin)) { delegate?.joinOpenGroup(); collapse() }
                    else { collapse() }
                } else {
                    val currentPosition = PointF(mainButton.x, mainButton.y)
                    mainButton.animatePositionChange(currentPosition, buttonRestPosition)
                    val endAlpha = 1.0f
                    mainButton.animateAlphaChange(mainButton.alpha, endAlpha)
                    expandedButton?.collapse()
                    this.expandedButton = null
                }
            }
        }
        previousAction = event.action
        return true
    }

    private fun expand() {
        val buttonsExcludingMainButton = listOf( sessionButton, closedGroupButton, openGroupButton )
        sessionButton.animatePositionChange(buttonRestPosition, sessionButtonExpandedPosition)
        closedGroupButton.animatePositionChange(buttonRestPosition, closedGroupButtonExpandedPosition)
        openGroupButton.animatePositionChange(buttonRestPosition, openGroupButtonExpandedPosition)
        buttonsExcludingMainButton.forEach { it.animateAlphaChange(0.0f, 1.0f) }
        postDelayed({ isExpanded = true }, Button.animationDuration)
    }

    private fun collapse() {
        val allButtons = listOf( mainButton, sessionButton, closedGroupButton, openGroupButton )
        allButtons.forEach {
            val currentPosition = PointF(it.x, it.y)
            it.animatePositionChange(currentPosition, buttonRestPosition)
            val endAlpha = if (it == mainButton) 1.0f else 0.0f
            it.animateAlphaChange(it.alpha, endAlpha)
        }
        postDelayed({ isExpanded = false }, Button.animationDuration)
    }
    // endregion
}

// region Delegate
interface NewConversationButtonSetViewDelegate {

    fun joinOpenGroup()
    fun createNewPrivateChat()
    fun createNewClosedGroup()
}
// endregion
