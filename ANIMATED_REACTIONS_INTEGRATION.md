# Animated Message Reactions - Integration Guide

## Quick Start

### 1. Basic Integration

Add the animated reaction panel to your conversation layout:

```xml
<!-- In your conversation activity layout -->
<org.thoughtcrime.securesms.reactions.animated.AnimatedReactionPanel
    android:id="@+id/animated_reaction_panel"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:visibility="gone"
    app:layout_constraintTop_toTopOf="parent"
    app:layout_constraintStart_toStartOf="parent" />
```

### 2. Setup in Activity/Fragment

```kotlin
class ConversationFragment : Fragment() {
    
    private lateinit var animatedReactionPanel: AnimatedReactionPanel
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        animatedReactionPanel = view.findViewById(R.id.animated_reaction_panel)
        
        // Configure with default settings
        animatedReactionPanel.configure(ReactionConfig())
        
        // Handle reaction selection
        animatedReactionPanel.setOnReactionSelectedListener { emoji, index ->
            onReactionSelected(emoji, index)
        }
        
        // Setup long-press on message views
        setupMessageLongPressHandling()
    }
}
```

### 3. Handle Message Long-Press

```kotlin
private fun setupMessageLongPressHandling() {
    val longPressRunnable = Runnable {
        // Show reaction panel when long-press is detected
        animatedReactionPanel.show(
            anchorView = messageView,
            messageView = messageView,
            onReactionSelected = { emoji, index ->
                addReactionToMessage(emoji)
            }
        )
    }
    
    messageView.setOnTouchListener { view, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Start long-press timer
                mainHandler.postDelayed(longPressRunnable, 500) // 500ms
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Cancel long-press if user releases too early
                mainHandler.removeCallbacks(longPressRunnable)
            }
        }
        false
    }
}
```

## Configuration Options

### Using Preset Configurations

```kotlin
// Minimal setup with just essential reactions
animatedReactionPanel.configure(ReactionPresets.minimal)

// Expressive setup with many reactions
animatedReactionPanel.configure(ReactionPresets.expressive)

// Fast animations for snappy feel
animatedReactionPanel.configure(ReactionPresets.fastAnimations)

// Springy physics-based animations
animatedReactionPanel.configure(ReactionPresets.springy)
```

### Custom Configuration

```kotlin
val customConfig = ReactionConfig(
    reactions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "😍"),
    animationDuration = 250,
    delayBetweenEmojiAnimations = 40,
    scaleOnSelection = 1.4f,
    hapticFeedbackEnabled = true,
    emojiSize = 44,
    selectedEmojiSize = 56
)

animatedReactionPanel.configure(customConfig)
```

## Integration with Existing Reactions

The animated reaction panel works alongside Signal's existing `ConversationReactionOverlay`:

```kotlin
// Use both systems together
// - AnimatedReactionPanel: Shows custom animated panel on long-press
// - ConversationReactionOverlay: Shows default scrubber for comparison

// Or replace entirely by hiding ConversationReactionOverlay
conversationReactionOverlay.visibility = View.GONE
animatedReactionPanel.visibility = View.VISIBLE
```

## Handling Reaction Storage

Store the selected emoji in the existing message database:

```kotlin
private fun addReactionToMessage(emoji: String) {
    val messageRecord = getCurrentMessage() ?: return
    
    // Use Signal's existing reaction API
    ReactionManager.addReaction(
        context = context,
        messageId = messageRecord.id,
        reaction = emoji
    )
}
```

## Customizing Animations

### Create Custom Animation Sequences

```kotlin
class CustomAnimationController(config: ReactionConfig) : ReactionAnimationController(config) {
    
    // Override methods to customize animations
    override fun createEmojiAppearAnimations(emojiViews: List<View>): AnimatorSet {
        // Create custom animation sequence
        val animatorSet = AnimatorSet()
        
        emojiViews.forEachIndexed { index, view ->
            val rotate = ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 360f)
            val scale = ObjectAnimator.ofFloat(view, View.SCALE_X, 0f, 1f)
            // ... more animations
            animatorSet.playTogether(rotate, scale)
        }
        
        return animatorSet
    }
}
```

## Event Callbacks

### Reaction Selected

```kotlin
animatedReactionPanel.setOnReactionSelectedListener { emoji, index ->
    println("Selected reaction: $emoji at index $index")
    // Update UI, save to database, send to other users, etc.
}
```

### Panel Visibility Changed

```kotlin
animatedReactionPanel.setOnPanelVisibilityListener { isVisible ->
    if (isVisible) {
        println("Reaction panel opened")
    } else {
        println("Reaction panel closed")
    }
}
```

## Gesture Handling

The system automatically handles:

- **Long-press detection**: Shows panel after 500ms press
- **Touch scrubbing**: Selecting emoji by moving over them
- **Swipe to dismiss**: Swiping outside bounds dismisses panel
- **Single tap selection**: Tapping emoji confirms reaction

### Customize Gesture Behavior

```kotlin
val gestureHandler = ReactionGestureHandler(
    context = context,
    config = config,
    onLongPress = { /* Panel appears */ },
    onReactionSelected = { emoji, index -> /* Handle selection */ },
    onDismiss = { /* Handle dismissal */ }
)

messageView.setOnTouchListener { _, event ->
    gestureHandler.onTouchEvent(event)
}
```

## Responsive Design

The panel automatically adapts to different screen sizes:

```kotlin
// For phones (portrait)
// - Horizontal layout below message
// - Single row of emoji

// For tablets/landscape
// - Adjusted positioning
// - Larger emoji (configurable)

// Automatic edge detection
// - Adjusts X position if near left/right edges
// - Adjusts Y position if near top/bottom edges
```

## Performance Optimization

### For Low-End Devices

```kotlin
// Reduce animation complexity
val lowEndConfig = ReactionConfig(
    animationDuration = 150,        // Shorter animations
    delayBetweenEmojiAnimations = 20,
    scaleOnSelection = 1.1f,        // Smaller scale change
    hapticFeedbackEnabled = false   // Disable haptics
)

animatedReactionPanel.configure(lowEndConfig)
```

### For High-End Devices

```kotlin
// Enable rich animations
val premiumConfig = ReactionConfig(
    animationDuration = 400,
    delayBetweenEmojiAnimations = 60,
    scaleOnSelection = 1.5f,
    springTension = 200f,
    damping = 8f
)

animatedReactionPanel.configure(premiumConfig)
```

## Accessibility

The system includes accessibility features:

```kotlin
// Content descriptions for screen readers
animatedReactionPanel.contentDescription = "Reaction selection panel"

// High contrast for visibility
animatedReactionPanel.setBackground(
    ContextCompat.getDrawable(context, R.drawable.high_contrast_bg)
)

// Haptic feedback for interaction confirmation
// (Already enabled by default in config)
```

## Testing

### Unit Tests

```kotlin
class AnimatedReactionPanelTest {
    
    @Test
    fun testReactionSelection() {
        val panel = AnimatedReactionPanel(context)
        panel.configure(ReactionConfig())
        
        var selectedEmoji = ""
        panel.setOnReactionSelectedListener { emoji, _ ->
            selectedEmoji = emoji
        }
        
        // Simulate selection
        // Assert selectedEmoji equals expected value
    }
}
```

### Integration Tests

```kotlin
class ConversationReactionIntegrationTest {
    
    @Test
    fun testReactionPanelAppears() {
        // Start conversation activity
        // Long-press on message
        // Verify panel appears with animation
        // Verify emoji are visible
    }
}
```

## Troubleshooting

### Panel Not Appearing

```kotlin
// Check panel visibility
Log.d("ReactionPanel", "Panel visibility: ${animatedReactionPanel.visibility}")

// Ensure long-press detection is working
// Check that onLongPress callback is called

// Verify position calculation
val position = animationController.calculateOptimalPanelPosition(
    messageView, animatedReactionPanel, screenHeight, screenWidth
)
Log.d("ReactionPanel", "Panel position: $position")
```

### Animation Jank

```kotlin
// Check hardware acceleration
messageView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

// Reduce animation duration for low-end devices
config.animationDuration = 150

// Monitor frame drops
android.os.Debug.startMethodTracing("reactions")
// ... perform actions
android.os.Debug.stopMethodTracing()
```

### Emoji Not Displaying

```kotlin
// Ensure EmojiImageView is properly imported
import org.thoughtcrime.securesms.components.emoji.EmojiImageView

// Check emoji string is valid Unicode
val emoji = "\u1F44D" // 👍

// Verify emoji rendering support
EmojiUtil.getCanonicalRepresentation(emoji)
```

## Advanced Usage

### Custom Reaction Types

```kotlin
// Add custom emoji reactions
val customReactions = listOf(
    "👍",   // Standard emoji
    "💯",   // Hundred points
    "🎉",   // Party
    "🚀",   // Rocket
    "💬"    // Speech bubble
)

val config = ReactionConfig(reactions = customReactions)
animatedReactionPanel.configure(config)
```

### Analytics Integration

```kotlin
animatedReactionPanel.setOnReactionSelectedListener { emoji, index ->
    // Track reaction selection
    Analytics.trackEvent("reaction_selected", mapOf(
        "emoji" to emoji,
        "index" to index,
        "timestamp" to System.currentTimeMillis()
    ))
}
```

### Custom Styling

```kotlin
// Customize background
animatedReactionPanel.setBackgroundColor(
    ContextCompat.getColor(context, R.color.custom_reaction_bg)
)

// Customize elevation
animatedReactionPanel.elevation = 16f

// Customize corner radius (in drawable)
// R.drawable.custom_reaction_background
```

## Migration from Old System

If upgrading from `ConversationReactionOverlay`:

```kotlin
// Step 1: Add AnimatedReactionPanel to layout
// Step 2: Configure it with your preferred settings
// Step 3: Setup long-press handling
// Step 4: Test thoroughly
// Step 5: Remove old ConversationReactionOverlay if not needed

// Both systems can coexist during transition
// Gradually phase out old system
```

---

For more details, see **ANIMATED_REACTIONS_DESIGN.md**
