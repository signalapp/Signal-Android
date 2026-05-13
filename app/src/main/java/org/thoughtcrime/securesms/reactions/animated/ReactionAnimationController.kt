/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.reactions.animated

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.abs

/**
 * Manages all animation sequences for the animated reaction panel.
 * Coordinates timing, interpolation, and animation callbacks for smooth visual feedback.
 */
class ReactionAnimationController(private val config: ReactionConfig) {

    private val animatorSets = mutableListOf<AnimatorSet>()

    /**
     * Creates a staggered appear animation for emoji in the panel.
     * Each emoji appears with a delay, creating a wave effect.
     */
    fun createEmojiAppearAnimations(emojiViews: List<View>): AnimatorSet {
        val animatorSet = AnimatorSet()
        val animators = mutableListOf<Animator>()

        emojiViews.forEachIndexed { index, view ->
            val delay = index * config.delayBetweenEmojiAnimations
            
            // Scale animation: 0 -> 1
            val scaleAnimatorX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0f, 1f).apply {
                duration = config.animationDuration
                startDelay = delay
                interpolator = OvershootInterpolator(1.5f)
            }

            val scaleAnimatorY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0f, 1f).apply {
                duration = config.animationDuration
                startDelay = delay
                interpolator = OvershootInterpolator(1.5f)
            }

            // Alpha animation: 0 -> 1
            val alphaAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
                duration = config.animationDuration
                startDelay = delay
                interpolator = LinearInterpolator()
            }

            // Translation animation: +50dp -> 0
            val translationAnimator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 50f, 0f).apply {
                duration = config.animationDuration
                startDelay = delay
                interpolator = BounceInterpolator()
            }

            animators.addAll(listOf(scaleAnimatorX, scaleAnimatorY, alphaAnimator, translationAnimator))
        }

        animatorSet.playTogether(animators)
        animatorSets.add(animatorSet)
        return animatorSet
    }

    /**
     * Creates a selection animation when user hovers over an emoji.
     * Includes scale and highlight color change with bounce effect.
     */
    fun createSelectionAnimation(
        emojiView: View,
        previousView: View?,
        onComplete: () -> Unit = {}
    ): AnimatorSet {
        val animatorSet = AnimatorSet()
        val animators = mutableListOf<Animator>()

        // Reset previous selection
        previousView?.let {
            val resetScaleX = ObjectAnimator.ofFloat(it, View.SCALE_X, 1f)
            val resetScaleY = ObjectAnimator.ofFloat(it, View.SCALE_Y, 1f)
            resetScaleX.duration = config.selectionAnimationDuration / 2
            resetScaleY.duration = config.selectionAnimationDuration / 2
            animators.addAll(listOf(resetScaleX, resetScaleY))
        }

        // Scale up with bounce
        val scaleXAnimator = ObjectAnimator.ofFloat(
            emojiView,
            View.SCALE_X,
            1f,
            config.scaleOnSelection,
            1.15f
        ).apply {
            duration = config.selectionAnimationDuration
            interpolator = OvershootInterpolator(1.2f)
        }

        val scaleYAnimator = ObjectAnimator.ofFloat(
            emojiView,
            View.SCALE_Y,
            1f,
            config.scaleOnSelection,
            1.15f
        ).apply {
            duration = config.selectionAnimationDuration
            interpolator = OvershootInterpolator(1.2f)
        }

        animators.addAll(listOf(scaleXAnimator, scaleYAnimator))

        animatorSet.apply {
            playTogether(animators)
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    onComplete()
                }
            })
        }

        animatorSets.add(animatorSet)
        return animatorSet
    }

    /**
     * Creates a confirmation animation shown when a reaction is selected.
     * Includes celebratory bounce and fade out.
     */
    fun createConfirmationAnimation(
        emojiView: View,
        onComplete: () -> Unit = {}
    ): AnimatorSet {
        val animatorSet = AnimatorSet()
        val animators = mutableListOf<Animator>()

        // Celebratory bounce: scale up then down
        val bounceX = ObjectAnimator.ofFloat(
            emojiView,
            View.SCALE_X,
            1f,
            1.5f,
            0.95f,
            0f
        ).apply {
            duration = config.confirmationAnimationDuration
            interpolator = BounceInterpolator()
        }

        val bounceY = ObjectAnimator.ofFloat(
            emojiView,
            View.SCALE_Y,
            1f,
            1.5f,
            0.95f,
            0f
        ).apply {
            duration = config.confirmationAnimationDuration
            interpolator = BounceInterpolator()
        }

        // Fade out
        val alphaAnimator = ObjectAnimator.ofFloat(emojiView, View.ALPHA, 1f, 0f).apply {
            duration = config.confirmationAnimationDuration
            interpolator = DecelerateInterpolator()
        }

        animators.addAll(listOf(bounceX, bounceY, alphaAnimator))

        animatorSet.apply {
            playTogether(animators)
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    onComplete()
                }
            })
        }

        animatorSets.add(animatorSet)
        return animatorSet
    }

    /**
     * Creates a panel appear animation.
     * Scale and fade in with slight overshoot for polish.
     */
    fun createPanelAppearAnimation(panelView: View): AnimatorSet {
        val animatorSet = AnimatorSet()
        val animators = mutableListOf<Animator>()

        // Initial state
        panelView.scaleX = 0.8f
        panelView.scaleY = 0.8f
        panelView.alpha = 0f

        // Scale animation
        val scaleXAnimator = ObjectAnimator.ofFloat(panelView, View.SCALE_X, 0.8f, 1f).apply {
            duration = config.animationDuration
            interpolator = OvershootInterpolator(1.2f)
        }

        val scaleYAnimator = ObjectAnimator.ofFloat(panelView, View.SCALE_Y, 0.8f, 1f).apply {
            duration = config.animationDuration
            interpolator = OvershootInterpolator(1.2f)
        }

        // Alpha animation
        val alphaAnimator = ObjectAnimator.ofFloat(panelView, View.ALPHA, 0f, 1f).apply {
            duration = config.animationDuration
            interpolator = LinearInterpolator()
        }

        animators.addAll(listOf(scaleXAnimator, scaleYAnimator, alphaAnimator))
        animatorSet.playTogether(animators)

        animatorSets.add(animatorSet)
        return animatorSet
    }

    /**
     * Creates a panel disappear animation.
     * Fade out and scale down.
     */
    fun createPanelDisappearAnimation(panelView: View): AnimatorSet {
        val animatorSet = AnimatorSet()
        val animators = mutableListOf<Animator>()

        // Scale animation
        val scaleXAnimator = ObjectAnimator.ofFloat(panelView, View.SCALE_X, 1f, 0.8f).apply {
            duration = config.animationDuration / 2
            interpolator = DecelerateInterpolator()
        }

        val scaleYAnimator = ObjectAnimator.ofFloat(panelView, View.SCALE_Y, 1f, 0.8f).apply {
            duration = config.animationDuration / 2
            interpolator = DecelerateInterpolator()
        }

        // Alpha animation
        val alphaAnimator = ObjectAnimator.ofFloat(panelView, View.ALPHA, 1f, 0f).apply {
            duration = config.animationDuration / 2
            interpolator = LinearInterpolator()
        }

        animators.addAll(listOf(scaleXAnimator, scaleYAnimator, alphaAnimator))
        animatorSet.playTogether(animators)

        animatorSets.add(animatorSet)
        return animatorSet
    }

    /**
     * Creates a spring physics-based animation for emphasis.
     * Useful for drawing user attention to specific elements.
     */
    fun createSpringAnimation(view: View, property: String = View.SCALE_X): ValueAnimator {
        val animator = ValueAnimator.ofFloat(1f, 1.2f, 1f).apply {
            duration = 600
            interpolator = OvershootInterpolator(2f)
            addUpdateListener { valueAnimator ->
                val value = valueAnimator.animatedValue as Float
                when (property) {
                    View.SCALE_X -> view.scaleX = value
                    View.SCALE_Y -> view.scaleY = value
                    View.ROTATION -> view.rotation = value * 360
                }
            }
        }

        animatorSets.add(AnimatorSet().apply { play(animator) })
        return animator
    }

    /**
     * Updates animation configuration and resets all animations.
     */
    fun updateConfig(newConfig: ReactionConfig) {
        cancelAllAnimations()
    }

    /**
     * Cancels all running animations and cleans up resources.
     */
    fun cancelAllAnimations() {
        animatorSets.forEach { it.cancel() }
        animatorSets.clear()
    }

    /**
     * Calculates appropriate panel position considering screen boundaries.
     */
    fun calculateOptimalPanelPosition(
        messageView: View,
        panelView: View,
        screenHeight: Int,
        screenWidth: Int
    ): Pair<Int, Int> {
        val messageLocation = IntArray(2)
        messageView.getLocationOnScreen(messageLocation)
        val messageX = messageLocation[0]
        val messageY = messageLocation[1]

        var panelX = messageX + (messageView.width - panelView.width) / 2
        var panelY = messageY - panelView.height - 16 // 16dp gap

        // Adjust X position if near edges
        if (config.adjustPositionForEdges) {
            val margin = 8 // 8dp margin from edge
            if (panelX < margin) {
                panelX = margin
            } else if (panelX + panelView.width > screenWidth - margin) {
                panelX = screenWidth - panelView.width - margin
            }
        }

        // Adjust Y position if not enough space above
        if (panelY < 0 && !config.positionAboveMessage) {
            panelY = messageY + messageView.height + 16 // Position below if not above
        }

        return Pair(panelX, panelY)
    }
}
