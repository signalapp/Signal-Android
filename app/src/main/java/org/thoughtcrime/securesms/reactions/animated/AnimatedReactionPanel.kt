/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.reactions.animated

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiImageView

/**
 * Custom animated reaction panel that appears on long-press of messages.
 * Displays a horizontal strip of customizable emoji reactions with smooth animations.
 * 
 * Features:
 * - Staggered emoji appearance animation
 * - Scale and highlight on selection
 * - Haptic feedback on interactions
 * - Responsive positioning that avoids screen edges
 * - Customizable reaction set and animation parameters
 */
class AnimatedReactionPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var config = ReactionConfig()
    private val animationController = ReactionAnimationController(config)
    private var gestureHandler: ReactionGestureHandler? = null

    private var emojiViews = mutableListOf<EmojiImageView>()
    private var selectedEmojiIndex = -1
    private var selectedEmojiView: View? = null

    private var onReactionSelectedListener: ((emoji: String, index: Int) -> Unit)? = null
    private var onPanelVisibleListener: ((isVisible: Boolean) -> Unit)? = null

    init {
        initializePanel()
    }

    private fun initializePanel() {
        // Set up panel appearance
        setBackgroundResource(R.drawable.conversation_reaction_overlay_background)
        elevation = 8f.toPx(context).toFloat()
        clipToOutline = true
    }

    /**
     * Configure the reaction panel with custom settings.
     */
    fun configure(newConfig: ReactionConfig) {
        this.config = newConfig
        animationController.updateConfig(newConfig)
        recreateEmojiViews()
    }

    /**
     * Create and layout emoji views based on config.
     */
    private fun recreateEmojiViews() {
        // Remove existing emoji views
        emojiViews.forEach { removeView(it) }
        emojiViews.clear()

        val panelLayout = ConstraintLayout(context)
        panelLayout.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )

        // Create emoji views
        config.reactions.forEachIndexed { index, emoji ->
            val emojiView = EmojiImageView(context).apply {
                layoutParams = LayoutParams(
                    config.emojiSize.toPx(context),
                    config.emojiSize.toPx(context)
                ).apply {
                    marginStart = (config.emojiSpacing / 2).toPx(context)
                    marginEnd = (config.emojiSpacing / 2).toPx(context)
                }
                setImageEmoji(emoji)
                alpha = 0f
                scaleX = 0f
                scaleY = 0f
            }
            emojiViews.add(emojiView)
            addView(emojiView)
        }
    }

    /**
     * Show the reaction panel with animation.
     */
    fun show(
        anchorView: View,
        messageView: View,
        onReactionSelected: (emoji: String, index: Int) -> Unit
    ) {
        this.onReactionSelectedListener = onReactionSelected

        // Position panel relative to message
        val panelPosition = animationController.calculateOptimalPanelPosition(
            messageView,
            this,
            height,
            width
        )

        x = panelPosition.first.toFloat()
        y = panelPosition.second.toFloat()

        // Show with animation
        visibility = View.VISIBLE
        animationController.createPanelAppearAnimation(this).start()

        // Animate emoji appearance
        animationController.createEmojiAppearAnimations(emojiViews).start()

        // Set up gesture handler
        setupGestureHandling(anchorView)

        onPanelVisibleListener?.invoke(true)
    }

    /**
     * Setup gesture recognition for the reaction panel.
     */
    private fun setupGestureHandling(anchorView: View) {
        if (gestureHandler == null) {
            gestureHandler = ReactionGestureHandler(
                context = context,
                config = config,
                onLongPress = { /* Already visible, ignore */ },
                onReactionSelected = { emoji, index ->
                    selectReaction(emoji, index)
                },
                onDismiss = {
                    dismiss()
                }
            )
        }

        gestureHandler?.attachToView(this)
        gestureHandler?.setPanelVisible(true)
    }

    /**
     * Handle emoji selection with animation and feedback.
     */
    private fun selectReaction(emoji: String, index: Int) {
        if (selectedEmojiIndex == index) {
            confirmReaction()
            return
        }

        selectedEmojiIndex = index
        val selectedView = emojiViews.getOrNull(index) ?: return

        // Provide haptic feedback
        if (config.hapticFeedbackEnabled) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        // Play selection animation
        animationController.createSelectionAnimation(
            selectedView,
            selectedEmojiView
        ).start()

        selectedEmojiView = selectedView
    }

    /**
     * Confirm the selected reaction and dismiss the panel.
     */
    private fun confirmReaction() {
        if (selectedEmojiIndex < 0 || selectedEmojiIndex >= config.reactions.size) {
            dismiss()
            return
        }

        val selectedView = emojiViews[selectedEmojiIndex]
        val selectedEmoji = config.reactions[selectedEmojiIndex]

        // Provide haptic feedback
        if (config.hapticFeedbackEnabled) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        // Play confirmation animation
        animationController.createConfirmationAnimation(selectedView) {
            onReactionSelectedListener?.invoke(selectedEmoji, selectedEmojiIndex)
            dismiss()
        }.start()
    }

    /**
     * Dismiss the reaction panel with animation.
     */
    fun dismiss() {
        animationController.createPanelDisappearAnimation(this).apply {
            start()
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    visibility = View.GONE
                    selectedEmojiIndex = -1
                    selectedEmojiView = null
                    reset()
                    onPanelVisibleListener?.invoke(false)
                }
            })
        }
    }

    /**
     * Reset emoji views to initial state.
     */
    private fun reset() {
        emojiViews.forEach { view ->
            view.alpha = 0f
            view.scaleX = 0f
            view.scaleY = 0f
            view.translationY = 0f
        }
    }

    /**
     * Set listener for reaction selection events.
     */
    fun setOnReactionSelectedListener(listener: (emoji: String, index: Int) -> Unit) {
        this.onReactionSelectedListener = listener
    }

    /**
     * Set listener for panel visibility changes.
     */
    fun setOnPanelVisibilityListener(listener: (isVisible: Boolean) -> Unit) {
        this.onPanelVisibleListener = listener
    }

    /**
     * Cleanup resources.
     */
    fun cleanup() {
        animationController.cancelAllAnimations()
        gestureHandler?.cleanup()
        emojiViews.clear()
    }

    /**
     * Extension function to convert dp to pixels.
     */
    private fun Int.toPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    /**
     * Extension function to convert dp to pixels (Float).
     */
    private fun Float.toPx(context: Context): Float {
        return this * context.resources.displayMetrics.density
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanup()
    }
}
