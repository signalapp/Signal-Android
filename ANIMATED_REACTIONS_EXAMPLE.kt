/*
 * Example implementation showing how to integrate AnimatedReactionPanel
 * into a conversation activity with real message handling.
 */

package org.thoughtcrime.securesms.conversation.animated_reactions_example

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.reactions.ReactionDetails
import org.thoughtcrime.securesms.reactions.ReactionsRepository
import org.thoughtcrime.securesms.reactions.animated.AnimatedReactionPanel
import org.thoughtcrime.securesms.reactions.animated.ReactionConfig
import org.thoughtcrime.securesms.reactions.animated.ReactionPresets
import org.thoughtcrime.securesms.util.LongClickMovementMethod

/**
 * Example Fragment showing integrated animated reactions in a conversation.
 * 
 * This demonstrates:
 * - Setting up the animated reaction panel
 * - Handling long-press on messages
 * - Storing reactions in the database
 * - Displaying existing reactions
 */
class ConversationWithAnimatedReactionsExample : Fragment() {

    companion object {
        private const val TAG = "ConversationReactions"
        private const val LONG_PRESS_DURATION = 500L
    }

    private lateinit var animatedReactionPanel: AnimatedReactionPanel
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Repository for handling reactions
    private lateinit var reactionsRepository: ReactionsRepository
    
    // Long-press detection per message
    private val longPressRunnables = mutableMapOf<String, Runnable>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize animated reaction panel
        animatedReactionPanel = view.findViewById(R.id.animated_reaction_panel)
        reactionsRepository = ReactionsRepository(requireContext())

        // Option 1: Use preset configuration (easiest)
        setupWithPreset()

        // Option 2: Use custom configuration (uncomment to try)
        // setupWithCustomConfig()

        // Setup reaction selection handling
        animatedReactionPanel.setOnReactionSelectedListener { emoji, index ->
            onReactionSelected(emoji, index)
        }

        // Setup panel visibility feedback
        animatedReactionPanel.setOnPanelVisibilityListener { isVisible ->
            if (isVisible) {
                Log.d(TAG, "Reaction panel opened")
            } else {
                Log.d(TAG, "Reaction panel closed")
            }
        }

        // Setup long-press handling for messages
        setupMessageLongPressHandlers()
    }

    /**
     * Setup with a preset configuration for quick implementation.
     */
    private fun setupWithPreset() {
        // Choose from available presets
        val config = ReactionPresets.expressive // Many reactions with nice animations

        animatedReactionPanel.configure(config)

        Log.d(TAG, "Reaction panel configured with preset: ${config.reactions.size} reactions")
    }

    /**
     * Setup with a completely custom configuration.
     */
    private fun setupWithCustomConfig() {
        val customConfig = ReactionConfig(
            // Custom emoji reactions
            reactions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "😍", "🎉"),

            // Animation timing
            animationDuration = 300,
            delayBetweenEmojiAnimations = 50,
            selectionAnimationDuration = 200,
            confirmationAnimationDuration = 400,

            // Visual feedback
            hapticFeedbackEnabled = true,
            showSelectionRing = true,

            // Animation intensity
            scaleOnSelection = 1.35f,
            springTension = 160f,
            damping = 10f,

            // Layout
            emojiSize = 42,
            selectedEmojiSize = 54,
            panelPadding = 10,
            emojiSpacing = 6,

            // Behavior
            adjustPositionForEdges = true,
            dismissOnSwipeOut = true,
            dismissOnBackgroundTap = true
        )

        animatedReactionPanel.configure(customConfig)

        Log.d(TAG, "Reaction panel configured with custom config")
    }

    /**
     * Setup long-press detection for each message in the conversation.
     * In a real implementation, this would be called for each message view in the adapter.
     */
    private fun setupMessageLongPressHandlers() {
        // This is an example - in real implementation, setup would happen in the adapter
        // when creating/binding ViewHolder items

        // Example: Attach to a specific message view
        // val messageView = view.findViewById<View>(R.id.message_item)
        // setupMessageLongPress(messageView, messageRecord)
    }

    /**
     * Setup long-press handling for a specific message.
     * Call this for each message item in the RecyclerView adapter.
     */
    fun setupMessageLongPress(
        messageView: View,
        messageRecord: MessageRecord
    ) {
        val messageId = messageRecord.id.toString()

        // Create long-press runnable
        val longPressRunnable = Runnable {
            Log.d(TAG, "Long-press detected on message: $messageId")
            showReactionPanel(messageView, messageRecord)
        }

        longPressRunnables[messageId] = longPressRunnable

        // Setup touch listener for long-press detection
        messageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Start long-press timer
                    mainHandler.postDelayed(longPressRunnable, LONG_PRESS_DURATION)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Cancel long-press if user releases too early
                    mainHandler.removeCallbacks(longPressRunnable)
                    false
                }
                else -> false
            }
        }
    }

    /**
     * Show the reaction panel for a specific message.
     */
    private fun showReactionPanel(
        messageView: View,
        messageRecord: MessageRecord
    ) {
        animatedReactionPanel.show(
            anchorView = messageView,
            messageView = messageView,
            onReactionSelected = { emoji, _ ->
                onReactionSelected(emoji, messageRecord)
            }
        )
    }

    /**
     * Handle reaction selection - store in database and update UI.
     */
    private fun onReactionSelected(emoji: String, messageRecord: MessageRecord) {
        Log.d(TAG, "Reaction selected: $emoji for message: ${messageRecord.id}")

        // Store reaction in database using Signal's API
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Add reaction using Signal's ReactionManager
                // Example (actual implementation depends on Signal's API)
                reactionsRepository.addReaction(
                    messageRecord.id,
                    emoji
                )

                Log.d(TAG, "Reaction stored successfully")

                // Update UI to show reaction
                updateReactionDisplay(messageRecord.id, emoji)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to add reaction", e)
            }
        }
    }

    /**
     * Overload for backward compatibility with emoji string only.
     */
    private fun onReactionSelected(emoji: String, index: Int) {
        // This is called from the panel's listener
        // You may need to track the current message separately
        Log.d(TAG, "Reaction selected: $emoji at index: $index")
    }

    /**
     * Update UI to display the reaction pill/badge.
     */
    private fun updateReactionDisplay(messageId: Long, emoji: String) {
        // Update the message item to show the reaction
        // This would typically be done by notifying the adapter
        // Example: adapter.notifyItemChanged(messagePosition)
    }

    /**
     * Example: Customize panel appearance based on message properties.
     */
    fun customizeReactionPanelForMessage(messageRecord: MessageRecord) {
        val config = when {
            messageRecord.isSent -> {
                // More reactions for sent messages
                ReactionPresets.expressive
            }
            messageRecord.isIncoming -> {
                // Minimal reactions for received messages
                ReactionPresets.minimal
            }
            else -> ReactionPresets.default
        }

        animatedReactionPanel.configure(config)
    }

    /**
     * Example: Device-specific optimization.
     */
    fun optimizeForDevice() {
        val config = if (isLowEndDevice()) {
            // Optimized for low-end devices
            ReactionConfig(
                animationDuration = 150,
                delayBetweenEmojiAnimations = 20,
                hapticFeedbackEnabled = false,
                scaleOnSelection = 1.1f
            )
        } else {
            // Full animations for capable devices
            ReactionPresets.expressive
        }

        animatedReactionPanel.configure(config)
    }

    /**
     * Check if device has limited resources.
     */
    private fun isLowEndDevice(): Boolean {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
        return maxMemory < 512 // Less than 512MB RAM
    }

    /**
     * Example: Track analytics for reaction usage.
     */
    private fun trackReactionAnalytics(emoji: String, messageRecord: MessageRecord) {
        // Log reaction usage for analytics
        val eventData = mapOf(
            "emoji" to emoji,
            "message_id" to messageRecord.id,
            "is_group" to messageRecord.isOutgoing,
            "timestamp" to System.currentTimeMillis()
        )

        // Send to analytics service
        // Analytics.logEvent("message_reaction", eventData)

        Log.d(TAG, "Tracked reaction: $emoji")
    }

    /**
     * Example: Show existing reactions on message.
     */
    fun displayExistingReactions(messageRecord: MessageRecord) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Get existing reactions from database
                val reactions = reactionsRepository.getReactions(messageRecord.id)

                // Group by emoji
                reactions.groupBy { it.emoji }
                    .forEach { (emoji, reactions) ->
                        Log.d(TAG, "Existing reaction: $emoji (count: ${reactions.size})")
                    }

                // Update UI with reaction pills/badges
                // updateReactionPills(reactions)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to get reactions", e)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // Cleanup
        longPressRunnables.forEach { (_, runnable) ->
            mainHandler.removeCallbacks(runnable)
        }
        longPressRunnables.clear()

        animatedReactionPanel.cleanup()
    }

    // ===== Helper methods for integration =====

    /**
     * Call this when setting up each message item in RecyclerView adapter.
     */
    fun setupMessageItemReactions(messageView: View, messageRecord: MessageRecord) {
        setupMessageLongPress(messageView, messageRecord)
        displayExistingReactions(messageRecord)
    }

    /**
     * Call this to update reaction configuration based on user preferences.
     */
    fun updateReactionPreferences(preference: String) {
        val config = when (preference) {
            "minimal" -> ReactionPresets.minimal
            "expressive" -> ReactionPresets.expressive
            "fast" -> ReactionPresets.fastAnimations
            "springy" -> ReactionPresets.springy
            else -> ReactionPresets.default
        }

        animatedReactionPanel.configure(config)
    }

    /**
     * Call this to handle dismissing the panel programmatically.
     */
    fun dismissReactionPanel() {
        animatedReactionPanel.dismiss()
    }
}

/**
 * Extension example showing how to add this to existing ConversationFragment.
 */
fun ConversationFragmentExample() {
    // In your actual ConversationFragment, add:
    
    // In onViewCreated():
    val animatedReactionPanel: AnimatedReactionPanel? = view?.findViewById(R.id.animated_reaction_panel)
    if (animatedReactionPanel != null) {
        animatedReactionPanel.configure(ReactionPresets.default)
    }

    // In the RecyclerView adapter's onBindViewHolder():
    val messageView = holder.itemView.findViewById<View>(R.id.message_content)
    val messageRecord = items[position]
    
    // Setup long-press handling
    setupMessageLongPress(messageView, messageRecord)
}
