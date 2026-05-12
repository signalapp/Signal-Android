/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.reactions.animated

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Handles gesture recognition for triggering and controlling the reaction panel.
 * Detects long-press, touch scrubbing, and swipe gestures.
 */
class ReactionGestureHandler(
    private val context: Context,
    private val config: ReactionConfig,
    private val onLongPress: () -> Unit,
    private val onReactionSelected: (emoji: String, index: Int) -> Unit,
    private val onDismiss: () -> Unit
) : GestureDetector.OnGestureListener {

    private val gestureDetector = GestureDetector(context, this)
    private val handler = Handler(Looper.getMainLooper())

    private var isReactionPanelVisible = false
    private var longPressStarted = false
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val longPressRunnable = Runnable {
        longPressStarted = true
        if (config.hapticFeedbackEnabled) {
            // Haptic feedback for long-press detection
            view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        onLongPress()
    }

    private var view: View? = null
    private val longPressTimeout = 500L // 500ms for long-press detection

    /**
     * Attach this gesture handler to a view.
     */
    fun attachToView(view: View) {
        this.view = view
        view.setOnTouchListener { _, event ->
            onTouchEvent(event)
        }
    }

    /**
     * Process touch events for gesture recognition.
     * Handles long-press detection, scrubbing, and swipe-to-dismiss.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        lastX = event.x
        lastY = event.y

        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handleActionDown(event)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                handleActionMove(event)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handleActionUp(event)
                true
            }
            else -> gestureDetector.onTouchEvent(event)
        }
    }

    private fun handleActionDown(event: MotionEvent) {
        startX = event.x
        startY = event.y
        longPressStarted = false

        // Start long-press timer
        handler.postDelayed(longPressRunnable, longPressTimeout)
    }

    private fun handleActionMove(event: MotionEvent) {
        val deltaX = abs(event.x - startX)
        val deltaY = abs(event.y - startY)
        val distance = sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()

        // Cancel long-press if moved too far
        val deadzone = 16f // 16dp deadzone
        if (distance > deadzone && !longPressStarted) {
            handler.removeCallbacks(longPressRunnable)
        }

        // Handle swipe-to-dismiss if panel is visible
        if (isReactionPanelVisible && config.dismissOnSwipeOut) {
            handleSwipeDismiss(event)
        }
    }

    private fun handleActionUp(event: MotionEvent) {
        handler.removeCallbacks(longPressRunnable)
        longPressStarted = false
    }

    /**
     * Handles swipe-to-dismiss gesture.
     * Dismisses reaction panel if user swipes far enough outside bounds.
     */
    private fun handleSwipeDismiss(event: MotionEvent) {
        val panelBounds = view?.let {
            android.graphics.Rect().apply {
                it.getDrawingRect(this)
            }
        } ?: return

        val swipeThreshold = 100f // 100dp swipe threshold
        val isOutsideBounds = !panelBounds.contains(event.x.toInt(), event.y.toInt())

        if (isOutsideBounds) {
            val distanceFromBounds = calculateDistanceFromBounds(event.x, event.y, panelBounds)
            if (distanceFromBounds > swipeThreshold) {
                onDismiss()
                isReactionPanelVisible = false
            }
        }
    }

    /**
     * Calculates distance from a point to the nearest edge of a rectangle.
     */
    private fun calculateDistanceFromBounds(
        x: Float,
        y: Float,
        bounds: android.graphics.Rect
    ): Float {
        val closestX = when {
            x < bounds.left -> bounds.left.toFloat()
            x > bounds.right -> bounds.right.toFloat()
            else -> x
        }

        val closestY = when {
            y < bounds.top -> bounds.top.toFloat()
            y > bounds.bottom -> bounds.bottom.toFloat()
            else -> y
        }

        val dx = (x - closestX)
        val dy = (y - closestY)
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    /**
     * Handle emoji selection via touch scrubbing.
     * Detects which emoji the user is currently hovering over.
     */
    fun handleEmojiSelection(
        touchX: Float,
        touchY: Float,
        emojiViews: List<View>
    ): Pair<Int, View>? {
        emojiViews.forEachIndexed { index, view ->
            val viewBounds = android.graphics.Rect()
            view.getDrawingRect(viewBounds)

            val location = IntArray(2)
            view.getLocationOnScreen(location)
            viewBounds.offset(location[0], location[1])

            // Add padding for easier touch target
            val padding = 16
            viewBounds.inset(-padding, -padding)

            if (viewBounds.contains(touchX.toInt(), touchY.toInt())) {
                return Pair(index, view)
            }
        }
        return null
    }

    /**
     * Set whether the reaction panel is currently visible.
     */
    fun setPanelVisible(visible: Boolean) {
        isReactionPanelVisible = visible
    }

    /**
     * Cleanup and release resources.
     */
    fun cleanup() {
        handler.removeCallbacks(longPressRunnable)
        view = null
    }

    // GestureDetector.OnGestureListener implementation
    override fun onDown(e: MotionEvent) = true
    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent) = true
    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ) = true

    override fun onLongPress(e: MotionEvent) {
        // Long press handled via timer above
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ) = true
}
