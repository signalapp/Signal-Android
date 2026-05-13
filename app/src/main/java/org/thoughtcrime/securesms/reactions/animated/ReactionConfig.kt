/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.reactions.animated

/**
 * Configuration holder for animated reaction behavior and appearance.
 * Allows easy customization of reaction types, animation timings, and visual parameters.
 */
data class ReactionConfig(
    // Reaction emoji set - customize by providing different emojis
    val reactions: List<String> = listOf("👍", "❤️", "😂", "😮", "😢", "🔥"),

    // Animation timing parameters
    val animationDuration: Long = 300,                    // Duration of panel appearance
    val delayBetweenEmojiAnimations: Long = 50,          // Delay between each emoji animation
    val selectionAnimationDuration: Long = 200,          // Duration of selection animation
    val confirmationAnimationDuration: Long = 400,       // Duration of confirmation animation

    // Haptic and visual feedback
    val hapticFeedbackEnabled: Boolean = true,           // Enable vibration on interactions
    val showSelectionRing: Boolean = true,               // Show highlight ring on selection

    // Animation intensity parameters
    val scaleOnSelection: Float = 1.3f,                  // Scale factor when emoji is selected
    val springTension: Float = 150f,                     // Spring animation tension
    val damping: Float = 10f,                            // Spring animation damping

    // Layout parameters
    val emojiSize: Int = 40,                             // Default emoji size in dp
    val selectedEmojiSize: Int = 52,                     // Emoji size when selected in dp
    val panelPadding: Int = 8,                           // Internal padding in dp
    val emojiSpacing: Int = 4,                           // Space between emojis in dp

    // Position and behavior
    val positionAboveMessage: Boolean = false,           // Position panel above message (if true) or below
    val adjustPositionForEdges: Boolean = true,          // Auto-adjust when near screen edges
    val dismissOnSwipeOut: Boolean = true,               // Dismiss when user swipes out of bounds
    val dismissOnBackgroundTap: Boolean = true,          // Dismiss when user taps background
)

/**
 * Preset configurations for common use cases.
 */
object ReactionPresets {
    val default = ReactionConfig()

    val minimal = ReactionConfig(
        reactions = listOf("👍", "❤️", "😂"),
        animationDuration = 200,
        delayBetweenEmojiAnimations = 30
    )

    val expressive = ReactionConfig(
        reactions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "😍", "🎉", "😢", "💯"),
        animationDuration = 350,
        delayBetweenEmojiAnimations = 60,
        scaleOnSelection = 1.4f
    )

    val fastAnimations = ReactionConfig(
        animationDuration = 150,
        delayBetweenEmojiAnimations = 25,
        selectionAnimationDuration = 100,
        confirmationAnimationDuration = 200
    )

    val springy = ReactionConfig(
        springTension = 200f,
        damping = 8f,
        animationDuration = 400
    )
}
