/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val DECELERATION_RATE = 0.99f

/**
 * Spring animation spec that mimics the View system's ViscousFluidInterpolator.
 * Uses no bounce with medium-low stiffness for natural physics-based fling deceleration.
 */
private val FlingAnimationSpec = spring<IntOffset>(
  dampingRatio = Spring.DampingRatioNoBouncy,
  stiffness = Spring.StiffnessMediumLow
)

/**
 * Spring animation spec for position changes (corner to corner, or to/from center).
 * Uses a medium stiffness for responsive but smooth transitions.
 */
private val PositionAnimationSpec = spring<IntOffset>(
  dampingRatio = Spring.DampingRatioNoBouncy,
  stiffness = Spring.StiffnessMedium
)

/**
 * Spring animation spec for size changes.
 */
private val SizeAnimationSpec = spring<Dp>(
  dampingRatio = Spring.DampingRatioNoBouncy,
  stiffness = Spring.StiffnessMedium
)

/**
 * Displays moveable content in a bounding box and allows the user to drag it to
 * the four corners. Automatically adjusts itself as the bounding box and content
 * size changes. When [isFocused] is true, the content centers and dragging is disabled.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PictureInPicture(
  state: PictureInPictureState,
  modifier: Modifier = Modifier,
  isFocused: Boolean = false,
  content: @Composable BoxScope.() -> Unit
) {
  BoxWithConstraints(
    modifier = modifier
  ) {
    val density = LocalDensity.current
    val maxHeight = constraints.maxHeight
    val maxWidth = constraints.maxWidth
    val targetContentWidth = with(density) { state.targetSize.width.toPx().roundToInt() }
    val targetContentHeight = with(density) { state.targetSize.height.toPx().roundToInt() }
    val coroutineScope = rememberCoroutineScope()

    var isDragging by remember {
      mutableStateOf(false)
    }

    val topLeft = remember {
      IntOffset(0, 0)
    }

    val topRight = remember(maxWidth, targetContentWidth) {
      IntOffset(maxWidth - targetContentWidth, 0)
    }

    val bottomLeft = remember(maxHeight, targetContentHeight) {
      IntOffset(0, maxHeight - targetContentHeight)
    }

    val bottomRight = remember(maxWidth, maxHeight, targetContentWidth, targetContentHeight) {
      IntOffset(maxWidth - targetContentWidth, maxHeight - targetContentHeight)
    }

    val centerOffset = remember(maxWidth, maxHeight, targetContentWidth, targetContentHeight) {
      IntOffset(
        (maxWidth - targetContentWidth) / 2,
        (maxHeight - targetContentHeight) / 2
      )
    }

    val initialOffset = remember(maxWidth, maxHeight, targetContentWidth, targetContentHeight) {
      getDesiredCornerOffset(state.corner, topLeft, topRight, bottomLeft, bottomRight)
    }

    val offsetAnimatable = remember {
      Animatable(initialOffset, IntOffset.VectorConverter)
    }

    // Animate position when focused state changes or when constraints/corner changes
    LaunchedEffect(maxWidth, maxHeight, targetContentWidth, targetContentHeight, state.corner, isFocused) {
      if (!isDragging) {
        val targetOffset = if (isFocused) {
          centerOffset
        } else {
          getDesiredCornerOffset(state.corner, topLeft, topRight, bottomLeft, bottomRight)
        }

        // Animate to new position (don't snap)
        offsetAnimatable.animateTo(
          targetValue = targetOffset,
          animationSpec = PositionAnimationSpec
        )
      }
    }

    Box(
      modifier = Modifier
        .size(state.contentSize)
        .offset {
          offsetAnimatable.value
        }
        .draggable2D(
          enabled = !offsetAnimatable.isRunning && !isFocused,
          state = rememberDraggable2DState { offset ->
            coroutineScope.launch {
              offsetAnimatable.snapTo(
                IntOffset(
                  offsetAnimatable.value.x + offset.x.roundToInt(),
                  offsetAnimatable.value.y + offset.y.roundToInt()
                )
              )
            }
          },
          onDragStarted = {
            isDragging = true
          },
          onDragStopped = { velocity ->
            isDragging = false

            val currentOffset = offsetAnimatable.value
            val x = currentOffset.x + project(velocity.x)
            val y = currentOffset.y + project(velocity.y)

            val projectedCoordinate = IntOffset(x.roundToInt(), y.roundToInt())
            val (corner, targetOffset) = getClosestCorner(projectedCoordinate, topLeft, topRight, bottomLeft, bottomRight)
            state.corner = corner

            coroutineScope.launch {
              offsetAnimatable.animateTo(
                targetValue = targetOffset,
                initialVelocity = IntOffset(velocity.x.roundToInt(), velocity.y.roundToInt()),
                animationSpec = FlingAnimationSpec
              )
            }
          }
        )
    ) {
      content()
    }
  }
}

private fun project(velocity: Float): Float {
  return (velocity / 1000f) * DECELERATION_RATE / (1f - DECELERATION_RATE)
}

private fun getClosestCorner(coordinate: IntOffset, topLeft: IntOffset, topRight: IntOffset, bottomLeft: IntOffset, bottomRight: IntOffset): Pair<PictureInPictureState.Corner, IntOffset> {
  val distances = mapOf(
    (PictureInPictureState.Corner.TOP_LEFT to topLeft) to distance(coordinate, topLeft),
    (PictureInPictureState.Corner.TOP_RIGHT to topRight) to distance(coordinate, topRight),
    (PictureInPictureState.Corner.BOTTOM_LEFT to bottomLeft) to distance(coordinate, bottomLeft),
    (PictureInPictureState.Corner.BOTTOM_RIGHT to bottomRight) to distance(coordinate, bottomRight)
  )

  return distances.minBy { it.value }.key
}

private fun getDesiredCornerOffset(corner: PictureInPictureState.Corner, topLeft: IntOffset, topRight: IntOffset, bottomLeft: IntOffset, bottomRight: IntOffset): IntOffset {
  return when (corner) {
    PictureInPictureState.Corner.TOP_LEFT -> topLeft
    PictureInPictureState.Corner.TOP_RIGHT -> topRight
    PictureInPictureState.Corner.BOTTOM_RIGHT -> bottomRight
    PictureInPictureState.Corner.BOTTOM_LEFT -> bottomLeft
  }
}

class PictureInPictureState @RememberInComposition constructor(initialContentSize: DpSize, initialCorner: Corner = Corner.BOTTOM_RIGHT) {

  var contentSize: DpSize by mutableStateOf(initialContentSize)
    private set

  var targetSize: DpSize by mutableStateOf(initialContentSize)
    private set

  var corner: Corner by mutableStateOf(initialCorner)

  enum class Corner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT
  }

  @Composable
  fun animateTo(newTargetSize: DpSize) {
    targetSize = newTargetSize

    val targetWidth by animateDpAsState(label = "animate-pip-width", targetValue = newTargetSize.width, animationSpec = SizeAnimationSpec)
    val targetHeight by animateDpAsState(label = "animate-pip-height", targetValue = newTargetSize.height, animationSpec = SizeAnimationSpec)

    contentSize = DpSize(targetWidth, targetHeight)
  }
}

private fun distance(a: IntOffset, b: IntOffset): Float {
  return sqrt((b.x - a.x).toDouble().pow(2) + (b.y - a.y).toDouble().pow(2)).toFloat()
}

@NightPreview
@Composable
fun PictureInPicturePreview() {
  Previews.Preview {
    PictureInPicture(
      state = remember { PictureInPictureState(initialContentSize = DpSize(90.dp, 160.dp)) },
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(color = Color.Red)
          .clip(MaterialTheme.shapes.medium)
      )
    }
  }
}
