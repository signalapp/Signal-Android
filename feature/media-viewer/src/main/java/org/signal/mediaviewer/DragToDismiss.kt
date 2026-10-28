/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

/** Fraction of the screen height the media must be dragged from center before a release dismisses. */
private const val DISMISS_FRACTION = 0.15f

/** Fraction of the screen height the media must be dragged from center for the backdrop to fully fade and the media to fully scale down. */
private const val FULL_FADE_FRACTION = 0.5f

internal const val MIN_SCALE = 0.7f

private const val FLY_AWAY_DURATION_MS = 200

private val FLING_VELOCITY_THRESHOLD = 1000.dp

/**
 * Drag-to-dismiss state for [MediaViewerScreen].
 */
@Stable
class DragToDismissState {
  internal val offset = Animatable(Offset.Zero, Offset.VectorConverter)

  internal var width by mutableIntStateOf(0)
  internal var height by mutableIntStateOf(0)

  /** Set once a drag has committed to dismissing the viewer. No further drags are accepted. */
  var isDismissing by mutableStateOf(false)
    internal set

  /** Horizontal offset of the media in px. */
  val offsetX: Float get() = offset.value.x

  /** Vertical offset of the media in px. Negative when dragged up. */
  val offsetY: Float get() = offset.value.y

  /** 0 at rest, rising to 1 as the media is dragged far enough from center to fully reveal what is beneath the viewer. */
  val progress: Float
    get() = fractionOfFullFade(offset.value.getDistance())

  /** 1 at rest, shrinking to [MIN_SCALE] as the media is dragged away from center. */
  val scale: Float
    get() = 1f - (1f - MIN_SCALE) * fractionOfFullFade(offset.value.getDistance())

  private fun fractionOfFullFade(distance: Float): Float {
    return if (height == 0) 0f else (distance / (height * FULL_FADE_FRACTION)).coerceIn(0f, 1f)
  }
}

@Composable
fun rememberDragToDismissState(): DragToDismissState {
  return remember { DragToDismissState() }
}

/**
 * Drag-to-dismiss. Claims only vertical drags, so horizontal swipes still page, and only starts
 * when [canStart] at touch down (to allow conditional dismissal). Once claimed, the media follows
 * the finger in any direction, and a release far enough from center dismisses.
 */
internal fun Modifier.dragToDismiss(
  state: DragToDismissState,
  canStart: () -> Boolean,
  canDismissToSharedElement: () -> Boolean,
  onDismissed: (toSharedElement: Boolean) -> Unit
): Modifier {
  return this
    .onSizeChanged {
      state.width = it.width
      state.height = it.height
    }
    .pointerInput(state) {
      val flingThreshold = FLING_VELOCITY_THRESHOLD.toPx()
      coroutineScope {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          if (state.isDismissing || !canStart()) {
            return@awaitEachGesture
          }

          val velocityTracker = VelocityTracker()
          velocityTracker.addPointerInputChange(down)

          var overSlop = 0f
          val dragStart = awaitVerticalDragStart(down.id) { change, over ->
            change.consume()
            overSlop = over
          } ?: return@awaitEachGesture

          velocityTracker.addPointerInputChange(dragStart)
          launch { state.dragBy(Offset(0f, overSlop)) }

          val completed = drag(dragStart.id) { change ->
            velocityTracker.addPointerInputChange(change)
            val delta = change.positionChange()
            change.consume()
            launch { state.dragBy(delta) }
          }

          if (completed) {
            val velocity = velocityTracker.calculateVelocity()
            launch { state.release(velocity, flingThreshold, canDismissToSharedElement(), onDismissed) }
          } else {
            launch { state.springBack(velocity = Velocity.Zero) }
          }
        }
      }
    }
}

/**
 * Like [awaitVerticalTouchSlopOrCancellation], but a move consumed by someone else restarts slop
 * detection instead of cancelling. The zooming views under the pager claim every gesture at touch
 * down and only hand it back once they see a drag they cannot use, so the dismiss has to be picked
 * up mid-gesture, the same way the pager's own scrollable does.
 */
private suspend fun AwaitPointerEventScope.awaitVerticalDragStart(
  pointerId: PointerId,
  onSlopReached: (change: PointerInputChange, overSlop: Float) -> Unit
): PointerInputChange? {
  while (true) {
    val dragStart = awaitVerticalTouchSlopOrCancellation(pointerId, onSlopReached)
    if (dragStart != null) {
      return dragStart
    }
    if (currentEvent.changes.none { it.id == pointerId && it.pressed }) {
      return null
    }
  }
}

private suspend fun DragToDismissState.dragBy(delta: Offset) {
  offset.snapTo(offset.value + delta)
}

/**
 * Ends a drag. Past the dismiss threshold (or flung away from rest) the viewer is dismissed:
 * toward the shared element when [canDismissToSharedElement], otherwise by first flying the
 * media off screen in the direction it was dragged. Short of that, the media springs back.
 */
private suspend fun DragToDismissState.release(velocity: Velocity, flingThreshold: Float, canDismissToSharedElement: Boolean, onDismissed: (toSharedElement: Boolean) -> Unit) {
  val current = offset.value
  val distance = current.getDistance()
  val velocityOffset = velocity.toOffset()
  val movingAway = velocityOffset.x * current.x + velocityOffset.y * current.y > 0f
  val flungAway = velocityOffset.getDistance() > flingThreshold && movingAway
  if (distance > 0f && (distance > height * DISMISS_FRACTION || flungAway)) {
    isDismissing = true
    if (!canDismissToSharedElement) {
      offset.animateTo(
        targetValue = current / distance * max(width, height).toFloat(),
        animationSpec = tween(durationMillis = FLY_AWAY_DURATION_MS, easing = FastOutLinearInEasing)
      )
    }
    onDismissed(canDismissToSharedElement)
  } else {
    springBack(velocity)
  }
}

private suspend fun DragToDismissState.springBack(velocity: Velocity) {
  offset.animateTo(
    targetValue = Offset.Zero,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    initialVelocity = velocity.toOffset()
  )
}

private fun Velocity.toOffset(): Offset = Offset(x, y)
