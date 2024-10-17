/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.signal.core.ui.DarkPreview
import org.signal.core.ui.Previews
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val DECELERATION_RATE = 0.99f

/**
 * Displays moveable content in a bounding box and allows the user to drag it to
 * the four corners. Automatically adjusts itself as the bounding box and content
 * size changes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PictureInPicture(
  modifier: Modifier = Modifier,
  contentSize: DpSize,
  content: @Composable () -> Unit
) {
  BoxWithConstraints(
    modifier = modifier
  ) {
    val density = LocalDensity.current
    val maxHeight = constraints.maxHeight
    val maxWidth = constraints.maxWidth
    val contentWidth = with(density) { contentSize.width.toPx().roundToInt() }
    val contentHeight = with(density) { contentSize.height.toPx().roundToInt() }
    val coroutineScope = rememberCoroutineScope()

    var isDragging by remember {
      mutableStateOf(false)
    }

    var isAnimating by remember {
      mutableStateOf(false)
    }

    var offsetX by remember {
      mutableIntStateOf(maxWidth - contentWidth)
    }
    var offsetY by remember {
      mutableIntStateOf(maxHeight - contentHeight)
    }

    val topLeft = remember {
      IntOffset(0, 0)
    }

    val topRight = remember(maxWidth, contentWidth) {
      IntOffset(maxWidth - contentWidth, 0)
    }

    val bottomLeft = remember(maxHeight, contentHeight) {
      IntOffset(0, maxHeight - contentHeight)
    }

    val bottomRight = remember(maxWidth, maxHeight, contentWidth, contentHeight) {
      IntOffset(maxWidth - contentWidth, maxHeight - contentHeight)
    }

    DisposableEffect(maxWidth, maxHeight, isAnimating, isDragging, contentWidth, contentHeight) {
      if (!isAnimating && !isDragging) {
        val projectedCoordinate = IntOffset(offsetX, offsetY)
        val closestCorner = getClosestCorner(projectedCoordinate, topLeft, topRight, bottomLeft, bottomRight)

        offsetX = closestCorner.x
        offsetY = closestCorner.y
      }

      onDispose { }
    }

    Box(
      modifier = Modifier
        .size(contentSize)
        .offset {
          IntOffset(offsetX, offsetY)
        }
        .draggable2D(
          enabled = !isAnimating,
          state = rememberDraggable2DState { offset ->
            offsetX += offset.x.roundToInt()
            offsetY += offset.y.roundToInt()
          },
          onDragStarted = {
            isDragging = true
          },
          onDragStopped = { velocity ->
            isDragging = false
            isAnimating = true

            val x = offsetX + project(velocity.x)
            val y = offsetY + project(velocity.y)

            val projectedCoordinate = IntOffset(x.roundToInt(), y.roundToInt())
            val cornerCoordinate = getClosestCorner(projectedCoordinate, topLeft, topRight, bottomLeft, bottomRight)

            coroutineScope.launch {
              animate(
                typeConverter = IntOffsetConverter,
                initialValue = IntOffset(offsetX, offsetY),
                targetValue = cornerCoordinate,
                initialVelocity = IntOffset(velocity.x.roundToInt(), velocity.y.roundToInt()),
                animationSpec = tween()
              ) { value, _ ->
                offsetX = value.x
                offsetY = value.y
              }

              isAnimating = false
            }
          }
        )
    ) {
      content()
    }
  }
}

private object IntOffsetConverter : TwoWayConverter<IntOffset, AnimationVector2D> {
  override val convertFromVector: (AnimationVector2D) -> IntOffset = { animationVector ->
    IntOffset(animationVector.v1.roundToInt(), animationVector.v2.roundToInt())
  }
  override val convertToVector: (IntOffset) -> AnimationVector2D = { intOffset ->
    AnimationVector(intOffset.x.toFloat(), intOffset.y.toFloat())
  }
}

private fun project(velocity: Float): Float {
  return (velocity / 1000f) * DECELERATION_RATE / (1f - DECELERATION_RATE)
}

private fun getClosestCorner(coordinate: IntOffset, topLeft: IntOffset, topRight: IntOffset, bottomLeft: IntOffset, bottomRight: IntOffset): IntOffset {
  val distances = mapOf(
    topLeft to distance(coordinate, topLeft),
    topRight to distance(coordinate, topRight),
    bottomLeft to distance(coordinate, bottomLeft),
    bottomRight to distance(coordinate, bottomRight)
  )

  return distances.minBy { it.value }.key
}

private fun distance(a: IntOffset, b: IntOffset): Float {
  return sqrt((b.x - a.x).toDouble().pow(2) + (b.y - a.y).toDouble().pow(2)).toFloat()
}

@DarkPreview
@Composable
fun PictureInPicturePreview() {
  Previews.Preview {
    PictureInPicture(
      contentSize = DpSize(90.dp, 160.dp),
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
