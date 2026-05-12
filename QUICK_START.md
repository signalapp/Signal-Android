# Animated Reactions - Quick Start Guide

## ⚡ 5-Minute Setup

### Step 1: Add to Layout XML
```xml
<org.thoughtcrime.securesms.reactions.animated.AnimatedReactionPanel
    android:id="@+id/animated_reaction_panel"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:visibility="gone"
    app:layout_constraintTop_toTopOf="parent"
    app:layout_constraintStart_toStartOf="parent" />
```

### Step 2: Initialize in Code
```kotlin
val panel = view.findViewById<AnimatedReactionPanel>(R.id.animated_reaction_panel)
panel.configure(ReactionPresets.default)

panel.setOnReactionSelectedListener { emoji, index ->
    // Handle reaction selection
    message.addReaction(emoji)
}
```

### Step 3: Setup Long-Press
```kotlin
var longPressTask: Runnable? = null

messageView.setOnTouchListener { _, event ->
    when (event.action) {
        MotionEvent.ACTION_DOWN -> {
            longPressTask = Runnable {
                panel.show(messageView, messageView)
            }
            handler.postDelayed(longPressTask!!, 500)
            true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            longPressTask?.let { handler.removeCallbacks(it) }
            true
        }
        else -> false
    }
}
```

Done! 🎉

## 📋 Common Tasks

### Change Reactions
```kotlin
val config = ReactionConfig(
    reactions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "😍")
)
panel.configure(config)
```

### Speed Up Animations
```kotlin
val config = ReactionConfig(
    animationDuration = 150,
    delayBetweenEmojiAnimations = 25
)
panel.configure(config)
```

### Customize Scale
```kotlin
val config = ReactionConfig(
    scaleOnSelection = 1.5f
)
panel.configure(config)
```

### Disable Haptics
```kotlin
val config = ReactionConfig(
    hapticFeedbackEnabled = false
)
panel.configure(config)
```

### Listen for Panel Opening
```kotlin
panel.setOnPanelVisibilityListener { isVisible ->
    if (isVisible) {
        println("Panel opened")
    } else {
        println("Panel closed")
    }
}
```

### Programmatically Close Panel
```kotlin
panel.dismiss()
```

### Cleanup
```kotlin
override fun onDestroyView() {
    super.onDestroyView()
    panel.cleanup()
}
```

## 🎨 Preset Configurations

```kotlin
// Minimal - 3 reactions, fast
panel.configure(ReactionPresets.minimal)

// Expressive - 10 reactions, nice animations
panel.configure(ReactionPresets.expressive)

// Fast - All quick animations
panel.configure(ReactionPresets.fastAnimations)

// Springy - Physics-based
panel.configure(ReactionPresets.springy)
```

## 🔧 Configuration Cheatsheet

```kotlin
ReactionConfig(
    // Emoji set
    reactions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥"),
    
    // Timing (ms)
    animationDuration = 300,
    delayBetweenEmojiAnimations = 50,
    selectionAnimationDuration = 200,
    confirmationAnimationDuration = 400,
    
    // Effects
    hapticFeedbackEnabled = true,
    showSelectionRing = true,
    
    // Scale (1.0 = normal)
    scaleOnSelection = 1.3f,
    springTension = 150f,
    damping = 10f,
    
    // Sizes (dp)
    emojiSize = 40,
    selectedEmojiSize = 52,
    panelPadding = 8,
    emojiSpacing = 4,
    
    // Behavior
    adjustPositionForEdges = true,
    dismissOnSwipeOut = true,
    dismissOnBackgroundTap = true
)
```

## 🎯 Gesture Reference

| Gesture | Duration | Action |
|---------|----------|--------|
| Long-press | 500ms | Show panel |
| Hover | - | Scale emoji |
| Release | - | Confirm reaction |
| Swipe out | 100dp+ | Dismiss panel |
| Tap background | - | Dismiss panel |

## 🚀 Performance Tips

**For Low-End Devices:**
```kotlin
panel.configure(ReactionConfig(
    animationDuration = 150,
    delayBetweenEmojiAnimations = 20,
    hapticFeedbackEnabled = false
))
```

**For High-End Devices:**
```kotlin
panel.configure(ReactionPresets.expressive)
```

**Enable Hardware Acceleration:**
```kotlin
messageView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
```

## 🐛 Troubleshooting

| Problem | Solution |
|---------|----------|
| Panel not appearing | Check `visibility = View.GONE` initially |
| Emoji not showing | Verify `reactions` list has valid emoji |
| Animation slow | Reduce `animationDuration` |
| Touch not responsive | Check long-press timeout is 500ms |
| Position wrong | Ensure layout constraints are set |
| Memory high | Call `cleanup()` in `onDestroyView()` |

## 📚 Documentation Map

```
QUICK_START.md (you are here)
  ↓
ANIMATED_REACTIONS_README.md (overview)
  ↓
ANIMATED_REACTIONS_INTEGRATION.md (detailed guide)
  ↓
ANIMATED_REACTIONS_DESIGN.md (full specification)
  ↓
ANIMATED_REACTIONS_EXAMPLE.kt (real code)
```

## 💡 Code Snippets

### Complete Example
```kotlin
class ConversationFragment : Fragment() {
    private lateinit var panel: AnimatedReactionPanel
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup panel
        panel = view.findViewById(R.id.animated_reaction_panel)
        panel.configure(ReactionPresets.default)
        
        // Handle selection
        panel.setOnReactionSelectedListener { emoji, _ ->
            addReactionToMessage(emoji)
        }
        
        // Setup long-press on message
        view.findViewById<View>(R.id.message).apply {
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        handler.postDelayed({
                            panel.show(this, this)
                        }, 500)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        true
                    }
                    else -> false
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        panel.cleanup()
    }
    
    private fun addReactionToMessage(emoji: String) {
        // Your reaction logic
    }
}
```

### Minimal Setup
```kotlin
panel = view.findViewById(R.id.animated_reaction_panel)
panel.configure(ReactionPresets.minimal)
panel.setOnReactionSelectedListener { emoji, _ ->
    onReactionSelected(emoji)
}
```

### Advanced Custom Config
```kotlin
panel.configure(ReactionConfig(
    reactions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "😍", "🎉"),
    animationDuration = 350,
    delayBetweenEmojiAnimations = 60,
    scaleOnSelection = 1.4f,
    springTension = 200f,
    damping = 8f,
    emojiSize = 44,
    selectedEmojiSize = 56
))
```

## 🎬 Animation Reference

| Animation | Duration | Effect |
|-----------|----------|--------|
| Emoji appear | 300ms | Staggered bounce-in |
| Selection | 200ms | Scale + highlight |
| Confirmation | 400ms | Celebratory bounce |
| Panel appear | 300ms | Scale + fade |
| Panel dismiss | 150ms | Scale + fade out |

## 📊 Default Values

```
animationDuration: 300ms
delayBetweenEmojiAnimations: 50ms
scaleOnSelection: 1.3x
emojiSize: 40dp
selectedEmojiSize: 52dp
longPressTimeout: 500ms
swipeDismissThreshold: 100dp
```

## ✨ Features Checklist

- ✓ Smooth animations (60fps)
- ✓ Long-press detection
- ✓ Touch scrubbing
- ✓ Swipe to dismiss
- ✓ Haptic feedback
- ✓ Edge detection
- ✓ Customizable reactions
- ✓ Preset configurations
- ✓ Responsive design
- ✓ Accessibility support

## 🔗 Integration Hooks

```kotlin
// Reaction selected
panel.setOnReactionSelectedListener { emoji, index -> }

// Panel visibility changed
panel.setOnPanelVisibilityListener { isVisible -> }

// Manual dismiss
panel.dismiss()

// Configure dynamically
panel.configure(customConfig)

// Cleanup on destroy
panel.cleanup()
```

## 🎓 Next Steps

1. Read **ANIMATED_REACTIONS_README.md** for overview
2. Review **ANIMATED_REACTIONS_INTEGRATION.md** for details
3. Study **ANIMATED_REACTIONS_EXAMPLE.kt** for real code
4. Implement in your activity/fragment
5. Test on multiple devices
6. Customize as needed

## 📞 Quick Help

**Q: How do I change the emoji?**  
A: Pass different list in `ReactionConfig(reactions = listOf(...))`

**Q: How do I make animations faster?**  
A: Use `ReactionPresets.fastAnimations` or reduce `animationDuration`

**Q: How do I disable haptics?**  
A: Set `hapticFeedbackEnabled = false` in config

**Q: How do I disable edge detection?**  
A: Set `adjustPositionForEdges = false` in config

**Q: Can I use with existing reactions?**  
A: Yes, both systems work together seamlessly

**Q: How do I optimize for low-end devices?**  
A: Use `ReactionPresets.minimal` or custom config with reduced timing

---

**Need more help?** Check the full documentation files!

**Ready to implement?** Follow the 5-minute setup above! 🚀
