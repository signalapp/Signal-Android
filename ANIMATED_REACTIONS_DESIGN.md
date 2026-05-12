# Animated Message Reactions Feature Design

## Overview
This document outlines the design and implementation plan for an animated message reactions feature that integrates seamlessly with Signal-Android's existing messaging interface. The feature enables users to long-press on messages to display an animated reaction panel with customizable emoji reactions, similar to Instagram's implementation.

## Architecture

### System Components

#### 1. **AnimatedReactionPanel** (Custom View)
A highly customizable, animated reaction panel component that displays reaction options in response to user interactions.

**Key Responsibilities:**
- Display a horizontal or vertical strip of animated reaction buttons
- Handle touch events and gesture recognition
- Manage animation lifecycle (appear, highlight, disappear)
- Display reaction confirmations with visual feedback

**Features:**
- Customizable reaction emoji set (default: 👍 ❤️ 😂 😮 😢 🔥)
- Spring/bounce animations on emoji appearance
- Scale animations on selection
- Smooth fade in/out transitions
- Haptic feedback on interaction
- Position optimization to avoid going off-screen

#### 2. **ReactionAnimationController** (Business Logic)
Manages animation sequences and timing for all reaction interactions.

**Key Responsibilities:**
- Coordinate appear/disappear animations
- Manage emoji stagger animations (each emoji appears with a delay)
- Handle selection animations (scale + highlight)
- Manage reaction confirmation animations

**Animation Types:**
- **Appear Animation**: Staggered pop-in with scale and alpha
- **Selection Animation**: Scale up, change color, bounce effect
- **Confirmation Animation**: Brief celebratory bounce before sending
- **Disappear Animation**: Fade out + scale down

#### 3. **ReactionGestureHandler** (Gesture Recognition)
Handles long-press and touch interaction detection for triggering the reaction panel.

**Key Responsibilities:**
- Detect long-press gestures on messages
- Track touch movement during scrubbing (Instagram-style)
- Detect lift/release to confirm selection
- Cancel on swipe out of bounds

#### 4. **ReactionConfig** (Configuration)
Centralized configuration for easy customization of reaction types and behavior.

```kotlin
data class ReactionConfig(
    val reactions: List<String> = listOf("👍", "❤️", "😂", "😮", "😢", "🔥"),
    val animationDuration: Long = 300,
    val delayBetweenEmojiAnimations: Long = 50,
    val hapticFeedbackEnabled: Boolean = true,
    val scaleOnSelection: Float = 1.3f,
    val springTension: Float = 150f,
    val damping: Float = 10f
)
```

## UI/UX Design

### Visual Design Specifications

**Panel Appearance:**
- Background: Semi-transparent blur effect matching Signal's design language
- Corner Radius: 12dp (matches Signal's design system)
- Elevation: High (above messages, below system UI)
- Padding: 8dp internal spacing
- Emoji Size: 40dp (default), scales to 52dp on selection

**Color Scheme:**
- Background: Signal's primary theme with 85% opacity
- Selection Highlight: Signal's accent color (semi-transparent ring)
- Reaction Icon: Match message bubble colors
- Haptic: System default haptic feedback

**Layout:**
- Horizontal layout for standard display
- Vertical fallback for narrow screens
- Position below message (default) or above if insufficient space
- Auto-adjustment to screen edges and notches

### Interaction Design

**Gesture Recognition:**
1. **Long-press (500ms)** on message → Panel appears with animation
2. **Hover/Touch** over emoji → Emoji scales up, highlight appears
3. **Release/Lift** → Reaction confirms and panel dismisses
4. **Swipe out of bounds** → Action cancels, panel dismisses

**Visual Feedback:**
- Haptic feedback on long-press detection
- Scale animation on emoji hover
- Brief bounce animation on confirmation
- Toast or brief notification showing reaction sent

## Implementation Strategy

### File Structure

```
app/src/main/java/org/thoughtcrime/securesms/reactions/animated/
├── AnimatedReactionPanel.kt           # Custom View component
├── ReactionAnimationController.kt     # Animation management
├── ReactionGestureHandler.kt          # Gesture recognition
├── ReactionConfig.kt                  # Configuration holder
├── ReactionPanelListener.kt           # Callback interface
└── views/
    └── ReactionEmojiView.kt           # Individual emoji view

app/src/main/res/layout/
├── animated_reaction_panel.xml        # Panel layout

app/src/main/res/animator/
├── reaction_emoji_appear.xml          # Emoji appearance animation
├── reaction_emoji_selected.xml        # Selection animation
├── reaction_panel_appear.xml          # Panel reveal animation
└── reaction_panel_disappear.xml       # Panel hide animation

app/src/main/res/values/
└── dimens_reactions.xml               # Dimension definitions
```

### Integration Points

#### 1. **ConversationReactionOverlay Integration**
- Extend existing `ConversationReactionOverlay` with AnimatedReactionPanel
- Maintain backward compatibility with current reaction flow
- Use AnimatedReactionPanel as an enhanced UI layer

#### 2. **V2ConversationItemLayout Integration**
- Add long-press listener to message items
- Pass message context to reaction panel
- Handle reaction confirmation callback

#### 3. **Message Database Integration**
- Store reaction type in existing message reaction table
- No schema changes required (uses existing reaction system)
- Timestamp for reaction animation reference

### Animation Details

#### Appear Animation (Panel)
```
Duration: 300ms
- Scale: 0.8 → 1.0
- Alpha: 0 → 1
- Interpolator: OvershootInterpolator
```

#### Emoji Stagger Appear
```
Each emoji:
- Delay: (index * 50ms)
- Duration: 300ms
- Scale: 0 → 1
- Alpha: 0 → 1
- TranslationY: +50dp → 0
- Interpolator: BounceInterpolator
```

#### Selection Animation
```
Duration: 200ms
- Scale: 1.0 → 1.3 → 1.1 (bounce effect)
- Color: Gray → Accent color
- Interpolator: OvershootInterpolator
```

#### Confirmation Animation
```
Duration: 400ms
- Scale: 1.0 → 1.5 → 0.95 (celebratory bounce)
- Alpha: 1 → 0
- Interpolator: BounceInterpolator
- Particle effect (optional): Small confetti burst
```

## Customization

### Easy Configuration Changes

Users can easily customize reactions by modifying `ReactionConfig`:

```kotlin
val customConfig = ReactionConfig(
    reactions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "😍"),
    animationDuration = 250,
    delayBetweenEmojiAnimations = 40,
    scaleOnSelection = 1.4f,
    springTension = 200f
)

animatedReactionPanel.configure(customConfig)
```

### Adding New Reaction Types

To add new reaction types:
1. Update `ReactionConfig.reactions` list with new emoji
2. No code changes needed - system automatically handles new emojis
3. Optional: Create preset configuration templates

## Responsive Design

### Mobile Considerations

**Phone (Portrait):**
- Horizontal panel below message
- Full width with side margins
- Single row of emoji

**Phone (Landscape):**
- Vertical panel (if space constrained)
- Side-positioned for better visibility
- Scrollable emoji list if many reactions

**Tablet:**
- Horizontal panel with generous spacing
- Larger emoji (44dp+)
- Grid layout for 2+ rows if many reactions

**Edge Cases:**
- Adjusts position when near screen edges
- Respects notches and system UI
- Avoids keyboard when visible

## Animation Performance

### Optimization Strategies

1. **Hardware Acceleration**: Enable for complex animations
2. **Lazy Loading**: Initialize animations only when panel is shown
3. **Caching**: Cache animation sets for reuse
4. **Memory Efficient**: Clean up animations after completion
5. **Battery Aware**: Reduce animation complexity on low battery

### Target Performance

- **Initial Show**: < 100ms
- **Frame Rate**: 60fps (no jank)
- **Memory**: < 2MB per reaction panel
- **CPU**: < 5% impact during animation

## Testing Strategy

### Unit Tests
- AnimationController timing verification
- GestureHandler event recognition
- Configuration validation

### Integration Tests
- Reaction confirmation flow
- Animation sequence execution
- Touch event handling

### UI Tests
- Visual appearance on different devices
- Animation smoothness
- Gesture responsiveness

## Future Enhancements

1. **Particle Effects**: Confetti or bubble animations on confirmation
2. **Sound Effects**: Optional reaction sounds (can be muted)
3. **Reaction Presets**: Save favorite reaction sets
4. **Reaction Grouping**: Categorize reactions (emoji, stickers, gifs)
5. **Skin Tone Variants**: Support emoji with skin tone modifiers
6. **Reaction Animations**: Animated emoji (sticker reactions)
7. **Analytics**: Track popular reactions for insights

## Accessibility

### Features
- Screen reader support with descriptive labels
- Haptic feedback for long-press detection
- High contrast reaction indicators
- Adjustable text size for reaction labels
- Color-independent selection indicators
- Keyboard navigation support (optional)

## Migration Plan

### Phase 1 (Initial Release)
- Implement core AnimatedReactionPanel
- Integrate with ConversationReactionOverlay
- Basic emoji set with default animations
- Testing on Pixel 4-6 and Huawei devices

### Phase 2 (Enhancement)
- Add particle effect animations
- Sound effect support
- Configuration presets
- Beta testing with users

### Phase 3 (Optimization)
- Performance optimization for low-end devices
- Animation refinement based on feedback
- Extended emoji support

## Technical Debt & Notes

- Current system uses `ConversationReactionOverlay` which is View-based; AnimatedReactionPanel will complement it
- No breaking changes to existing API
- Backward compatible with all Android API 21+ devices
- Uses standard Android animation APIs (no external dependencies required)

## References

- Signal's Design System: Light/Dark theme support
- Instagram's Reaction UI: Inspiration for animation sequences
- Android Animation Framework: ObjectAnimator, AnimatorSet
- Material Design: Motion principles and timing

---

**Document Version**: 1.0  
**Last Updated**: May 2026  
**Status**: Design Complete - Ready for Implementation
