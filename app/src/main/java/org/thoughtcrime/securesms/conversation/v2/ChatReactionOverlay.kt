/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.signal.core.ui.compose.navigationBarsCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationItem
import org.thoughtcrime.securesms.conversation.ReactionOverlayPlacement
import org.thoughtcrime.securesms.conversation.ReactionScrubber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import org.signal.core.ui.R as CoreUiR

const val REACTION_EMOJI_COUNT = 7

private const val GROW_DURATION_MS = 200
private val KEYBOARD_RETURN_TIMEOUT = 1.seconds
private const val GROW_SCALE = 1.5f
private val EMOJI_WIDTH = 32.dp
private val EMOJI_HEIGHT = 48.dp
private val SELECTION_INDICATOR_SIZE = 52.dp
private val STRIP_CORNER_RADIUS = 30.dp

/** DecelerateInterpolator, which an interpolator on an AnimatorSet applied to the whole reveal and hide. */
private val Decelerate = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) }

/** ViewPropertyAnimator's default, which carried the snapshot to its place. */
private val AccelerateDecelerate = Easing { fraction -> (cos((fraction + 1f) * PI).toFloat() / 2f) + 0.5f }

private class StripBounds {
  var barTop: Float = 0f
  var barBottom: Float = 0f
  var firstEmojiEdge: Float = 0f
  var lastEmojiEdge: Float = 0f
}

/**
 * Everything one long press put on screen, in overlay coordinates. [lastSeenDownY] is the exception
 * and is in window coordinates, as it arrives from the activity's touch relay.
 *
 * @param snapshot A picture of the row that was pressed, standing in for the real one.
 * @param emojiLabels What an accessibility service announces for each slot in [emoji].
 * @param appliedEmojiIndex Slot holding the reaction already on this message, or -1 for none.
 * @param canReact False for a message that takes no reactions, which hides the strip.
 * @param returnPosition Where the row is *now*, for the hide to fly back to. Null once it is gone.
 */
data class ReactionOverlaySelection(
  val snapshot: ImageBitmap,
  val bubbleX: Float,
  val bubbleY: Float,
  val bubbleWidth: Int,
  val contextMenuX: Float,
  val isMessageOnLeft: Boolean,
  val lastSeenDownY: Float,
  val emoji: List<Drawable?>,
  val emojiLabels: List<String?>,
  val appliedEmojiIndex: Int,
  val canReact: Boolean,
  val returnPosition: () -> Offset?
)

/**
 * The long press reaction overlay, drawn above the keyboard scaffold.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatReactionOverlay(
  controller: ChatReactionOverlayController,
  modifier: Modifier = Modifier
) {
  val selection = controller.selection
  val isRevealed = controller.isRevealed
  val scrubber = controller.scrubber
  val menuWidth = controller.menu?.getMaxWidth() ?: 0
  val menuHeight = controller.menu?.getMaxHeight() ?: 0

  val density = LocalDensity.current
  val context = LocalContext.current
  val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

  val revealDuration = integerResource(R.integer.reaction_scrubber_reveal_duration)
  val revealOffset = integerResource(R.integer.reaction_scrubber_reveal_offset)
  val hideDuration = integerResource(R.integer.reaction_scrubber_hide_duration)
  val emojiStagger = integerResource(R.integer.reaction_scrubber_emoji_reveal_duration_start_delay_factor)
  val indicatorDuration = integerResource(android.R.integer.config_mediumAnimTime)

  val scrubberWidth = dimensionResource(R.dimen.reaction_scrubber_width)
  val scrubberHeight = dimensionResource(R.dimen.conversation_reaction_scrubber_height)
  val horizontalMargin = dimensionResource(R.dimen.conversation_reaction_scrub_horizontal_margin)
  val scrubDistanceBelowTouch = dimensionResource(R.dimen.conversation_reaction_scrub_deadzone_distance_from_touch_bottom)

  val barHeightPx = remember(context) {
    val attributes = context.theme.obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.actionBarSize))
    val height = attributes.getDimensionPixelSize(0, 0)
    attributes.recycle()

    height
  }

  val overlayTop = remember { mutableFloatStateOf(0f) }

  val imeInsets = WindowInsets.ime
  val imeTargetInsets = WindowInsets.imeAnimationTarget

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .onGloballyPositioned { coordinates ->
        val origin = coordinates.positionInWindow()

        overlayTop.floatValue = origin.y
        controller.onOriginChanged(origin)
      },
    contentAlignment = AbsoluteAlignment.TopLeft
  ) {
    if (selection == null) {
      return@BoxWithConstraints
    }

    val overlayWidthPx = with(density) { maxWidth.roundToPx() }
    val overlayHeightPx = with(density) { maxHeight.roundToPx() }
    val statusBarPx = overlayTop.floatValue.roundToInt()
    val navigationBarPx = WindowInsets.navigationBarsCompat.getBottom(density)
    val scrubBelowTouchPx = with(density) { scrubDistanceBelowTouch.toPx() }

    val placement = remember(
      selection,
      overlayWidthPx,
      overlayHeightPx,
      statusBarPx,
      navigationBarPx,
      barHeightPx,
      menuWidth,
      menuHeight
    ) {
      ReactionOverlayPlacement.of(
        metrics = ReactionOverlayPlacement.Metrics(
          overlayWidth = overlayWidthPx,
          overlayHeight = overlayHeightPx,
          statusBarHeight = statusBarPx,
          navigationBarHeight = navigationBarPx,
          bubbleX = selection.bubbleX,
          bubbleY = selection.bubbleY,
          bubbleWidth = selection.bubbleWidth,
          contextMenuX = selection.contextMenuX,
          snapshotWidth = selection.snapshot.width,
          snapshotHeight = selection.snapshot.height,
          reactionBarHeight = barHeightPx,
          scrubberForegroundHeight = with(density) { scrubberHeight.roundToPx() },
          scrubberWidth = with(density) { scrubberWidth.roundToPx() },
          scrubberHorizontalMargin = with(density) { horizontalMargin.roundToPx() },
          menuMaxWidth = menuWidth,
          menuMaxHeight = menuHeight,
          isMessageOnLeft = selection.isMessageOnLeft,
          lastSeenDownY = selection.lastSeenDownY
        ),
        dp = ReactionOverlayPlacement.DpConverter { with(density) { it.dp.toPx() } }
      )
    }

    val bounds = remember(selection) { StripBounds() }

    val pushGeometry = {
      scrubber.geometry = ReactionScrubber.Geometry(
        stripStart = bounds.firstEmojiEdge,
        stripEnd = bounds.lastEmojiEdge,
        stripTop = bounds.barTop,
        stripBottom = bounds.barBottom,
        scrubTop = bounds.barTop,
        scrubBottom = selection.lastSeenDownY + scrubBelowTouchPx,
        isStripVisible = selection.canReact
      )
    }

    val shade = remember(selection) { Animatable(1f) }
    val emojiReveal = remember(selection) { List(REACTION_EMOJI_COUNT) { Animatable(0f) } }
    val indicatorReveal = remember(selection) { Animatable(0f) }
    val stripReveal = remember(selection) { Animatable(0f) }
    val snapshotX = remember(selection) { Animatable(selection.bubbleX) }
    val snapshotY = remember(selection) { Animatable(selection.bubbleY) }
    val snapshotScale = remember(selection) { Animatable(ConversationItem.LONG_PRESS_SCALE_FACTOR) }
    val returnProgress = remember(selection) { Animatable(0f) }
    var returnFrom by remember(selection) { mutableStateOf<Offset?>(null) }
    var returnFromScale by remember(selection) { mutableFloatStateOf(1f) }
    var returnTo by remember(selection) { mutableStateOf(Offset(selection.bubbleX, selection.bubbleY)) }

    DisposableEffect(Unit) {
      onDispose { controller.onHideFinished() }
    }

    LaunchedEffect(selection, isRevealed) {
      if (isRevealed) {
        coroutineScope {
          launch { snapshotX.animateTo(placement.snapshotX, tween(revealDuration, easing = AccelerateDecelerate)) }
          launch { snapshotY.animateTo(placement.snapshotY, tween(revealDuration, easing = AccelerateDecelerate)) }
          launch { snapshotScale.animateTo(placement.snapshotScale, tween(revealDuration, easing = AccelerateDecelerate)) }
          launch { indicatorReveal.animateTo(1f, tween(indicatorDuration, easing = Decelerate)) }
          launch { stripReveal.animateTo(1f, tween(revealDuration, delayMillis = revealOffset, easing = Decelerate)) }

          emojiReveal.forEachIndexed { index, animatable ->
            launch {
              animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                  durationMillis = revealDuration,
                  delayMillis = revealOffset + index * emojiStagger,
                  easing = Decelerate
                )
              )
            }
          }
        }
      } else {
        returnFrom = Offset(snapshotX.value, snapshotY.value)
        returnFromScale = snapshotScale.value
        returnTo = selection.returnPosition() ?: returnTo

        val updateReturnTo = { returnTo = selection.returnPosition() ?: returnTo }

        coroutineScope {
          launch { shade.animateTo(0f, tween(hideDuration, easing = Decelerate)) }
          launch {
            if (controller.isKeyboardReturning && Build.VERSION.SDK_INT >= 30) {
              withTimeoutOrNull(KEYBOARD_RETURN_TIMEOUT) {
                followKeyboard(imeInsets, imeTargetInsets, density, returnProgress, updateReturnTo)
              }
            }

            returnProgress.animateTo(1f, tween(hideDuration, easing = Decelerate)) { updateReturnTo() }
          }
          launch { indicatorReveal.animateTo(0f, tween(hideDuration, easing = Decelerate)) }
          launch { stripReveal.animateTo(0f, tween(hideDuration, easing = Decelerate)) }

          emojiReveal.forEach { animatable ->
            launch { animatable.animateTo(0f, tween(hideDuration, easing = Decelerate)) }
          }
        }

        controller.onHideFinished()
      }
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .onGloballyPositioned { controller.onPlaced(placement) }
        .graphicsLayer { alpha = shade.value }
        .background(colorResource(R.color.reactions_screen_light_shade_color))
        .background(colorResource(R.color.reactions_screen_dark_shade_color))
    )

    Image(
      bitmap = selection.snapshot,
      contentDescription = null,
      modifier = Modifier
        .absoluteOffset {
          val from = returnFrom
          val position = if (from != null) lerp(from, returnTo, returnProgress.value) else Offset(snapshotX.value, snapshotY.value)

          IntOffset(position.x.roundToInt(), position.y.roundToInt())
        }
        .size(
          width = with(density) { selection.snapshot.width.toDp() },
          height = with(density) { selection.snapshot.height.toDp() }
        )
        .graphicsLayer {
          val scale = if (returnFrom != null) lerp(returnFromScale, 1f, returnProgress.value) else snapshotScale.value

          scaleX = scale
          scaleY = scale
        }
    )

    if (!selection.canReact) {
      return@BoxWithConstraints
    }

    Box(
      modifier = Modifier
        .absoluteOffset { IntOffset(placement.scrubberX.roundToInt(), placement.reactionBarY.roundToInt()) }
        .width(scrubberWidth)
        .height(with(density) { barHeightPx.toDp() })
        .graphicsLayer { alpha = stripReveal.value }
        .background(
          color = colorResource(CoreUiR.color.signal_colorSurface2),
          shape = RoundedCornerShape(STRIP_CORNER_RADIUS)
        )
        .onGloballyPositioned { coordinates ->
          bounds.barTop = coordinates.positionInWindow().y
          bounds.barBottom = bounds.barTop + coordinates.size.height
          pushGeometry()
        }
    )

    Row(
      modifier = Modifier
        .absoluteOffset { IntOffset(placement.scrubberX.roundToInt(), placement.scrubberForegroundY.roundToInt()) }
        .width(scrubberWidth)
        .height(scrubberHeight),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      for (index in 0 until REACTION_EMOJI_COUNT) {
        ReactionEmoji(
          index = index,
          emoji = selection.emoji.getOrNull(index),
          label = selection.emojiLabels.getOrNull(index),
          isApplied = index == selection.appliedEmojiIndex,
          reveal = emojiReveal[index].value,
          indicatorAlpha = indicatorReveal.value,
          selectedIndex = { controller.selectedIndex },
          onActivate = { controller.onEmojiClicked(index) },
          onEdgeMeasured = { left, right ->
            // Layout direction order, so under RTL start is the greater edge.
            if (index == 0) {
              bounds.firstEmojiEdge = if (isLtr) left else right
            }

            if (index == REACTION_EMOJI_COUNT - 1) {
              bounds.lastEmojiEdge = if (isLtr) right else left
            }

            pushGeometry()
          }
        )
      }
    }
  }
}

/**
 * Drives [progress] by how far the system keyboard has come in, so the return lands as the keyboard
 * does. Returns early, leaving the rest to the caller, if the keyboard goes away again.
 */
private suspend fun followKeyboard(
  imeInsets: WindowInsets,
  imeTargetInsets: WindowInsets,
  density: Density,
  progress: Animatable<Float, *>,
  onFrame: () -> Unit
) {
  snapshotFlow { imeTargetInsets.getBottom(density) }.first { it > 0 }

  while (true) {
    withFrameNanos { }
    onFrame()

    val target = imeTargetInsets.getBottom(density)
    if (target <= 0) {
      return
    }

    val fraction = (imeInsets.getBottom(density).toFloat() / target).coerceIn(0f, 1f)
    progress.snapTo(fraction)

    if (fraction >= 1f) {
      // The row is laid out for the final inset a frame after the inset reports it.
      withFrameNanos { }
      onFrame()
      return
    }
  }
}

/**
 * One slot in the strip.
 *
 * Its own composable so that [selectedIndex], which changes as fast as a thumb moves, invalidates
 * seven small scopes rather than the whole overlay.
 *
 * @param onActivate Activation through semantics only, for accessibility services. Touch still goes
 *   through the scrubber.
 * @param onEdgeMeasured The slot's untransformed leading and trailing edges, in window coordinates.
 */
@Composable
private fun ReactionEmoji(
  index: Int,
  emoji: Drawable?,
  label: String?,
  isApplied: Boolean,
  reveal: Float,
  indicatorAlpha: Float,
  selectedIndex: () -> Int,
  onActivate: () -> Unit,
  onEdgeMeasured: (Float, Float) -> Unit
) {
  val density = LocalDensity.current
  val startTranslation = dimensionResource(R.dimen.reaction_scrubber_anim_start_translation_y)
  val growTranslation = dimensionResource(R.dimen.conversation_reaction_scrub_vertical_translation)

  val isUnderThumb = selectedIndex() == index
  val revealLift = (1f - reveal) * with(density) { startTranslation.toPx() }

  val grow by animateFloatAsState(
    targetValue = if (isUnderThumb) GROW_SCALE else 1f,
    animationSpec = tween(GROW_DURATION_MS, easing = Decelerate),
    label = "emojiGrow$index"
  )
  val growLift by animateFloatAsState(
    targetValue = if (isUnderThumb) -with(density) { growTranslation.toPx() } else 0f,
    animationSpec = tween(GROW_DURATION_MS, easing = Decelerate),
    label = "emojiGrowLift$index"
  )

  Box(
    modifier = Modifier
      .size(width = EMOJI_WIDTH, height = EMOJI_HEIGHT)
      // Measured here rather than on the emoji, whose graphicsLayer scale would otherwise move the
      // reported edges as it grows and shift the segment boundaries under a stationary thumb.
      .onGloballyPositioned { coordinates ->
        val left = coordinates.positionInWindow().x

        onEdgeMeasured(left, left + coordinates.size.width)
      }
      // Not clickable: touch belongs to the scrubber, and a click would commit again on lift.
      .clearAndSetSemantics {
        if (label != null) {
          contentDescription = label
          role = Role.Button
          onClick {
            onActivate()
            true
          }
        }
      },
    contentAlignment = Alignment.Center
  ) {
    if (isApplied) {
      Box(
        modifier = Modifier
          // Required so the indicator keeps its full circle rather than being squeezed into the slot.
          .requiredSize(SELECTION_INDICATOR_SIZE)
          .graphicsLayer { alpha = indicatorAlpha }
          .background(
            color = colorResource(CoreUiR.color.signal_colorSurfaceVariant_16_no_alpha),
            shape = CircleShape
          )
      )
    }

    Image(
      painter = rememberDrawablePainter(emoji),
      contentDescription = null,
      modifier = Modifier
        .size(width = EMOJI_WIDTH, height = EMOJI_HEIGHT)
        .graphicsLayer {
          alpha = reveal
          translationY = revealLift + growLift
          scaleX = grow
          scaleY = grow
        }
    )
  }
}
