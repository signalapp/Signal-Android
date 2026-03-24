/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Default values for [AnimatedFlowRow].
 */
object AnimatedFlowRowDefaults {
  internal const val ANIMATION_DURATION_MS = 300L

  private val DefaultEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

  /**
   * Default animation spec for position animations.
   */
  @Stable
  val positionAnimationSpec: FiniteAnimationSpec<IntOffset> = tween(
    durationMillis = ANIMATION_DURATION_MS.toInt(),
    easing = DefaultEasing
  )

  /**
   * Default animation spec for size (height) animations.
   */
  @Stable
  val sizeAnimationSpec: FiniteAnimationSpec<IntSize> = tween(
    durationMillis = ANIMATION_DURATION_MS.toInt(),
    easing = DefaultEasing
  )

  internal val alphaAnimationSpec: FiniteAnimationSpec<Float> = tween(
    durationMillis = ANIMATION_DURATION_MS.toInt(),
    easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
  )
}

/**
 * Scope for [AnimatedFlowRow] content that provides [item] for adding keyed items.
 */
class AnimatedFlowRowScope {
  internal val items = mutableListOf<Pair<Any, @Composable () -> Unit>>()

  /**
   * Adds an item to the flow row with a stable key for animation tracking.
   *
   * @param key A stable, unique key for this item. Items with the same key will
   *            animate smoothly when their position changes.
   * @param content The composable content for this item.
   */
  fun item(key: Any, content: @Composable () -> Unit) {
    items.add(key to content)
  }
}

/**
 * A FlowRow that animates item position changes smoothly.
 * Items animate from their previous position to their new position when the layout changes.
 * New items fade in after existing items have finished animating to their new positions.
 *
 * Use the [AnimatedFlowRowScope.item] function to add items with stable keys:
 * ```
 * AnimatedFlowRow {
 *   item(key = "audio") { AudioChip() }
 *   item(key = "video") { VideoChip() }
 * }
 * ```
 *
 * @param modifier The modifier to apply to this layout.
 * @param sizeAnimationSpec Animation spec for container size changes, or null to disable size animation.
 *        Defaults to [AnimatedFlowRowDefaults.sizeAnimationSpec].
 * @param positionAnimationSpec The animation spec to use for item position animations.
 *        Defaults to [AnimatedFlowRowDefaults.positionAnimationSpec].
 * @param content The content builder using [AnimatedFlowRowScope].
 */
@Composable
fun AnimatedFlowRow(
  modifier: Modifier = Modifier,
  sizeAnimationSpec: FiniteAnimationSpec<IntSize>? = AnimatedFlowRowDefaults.sizeAnimationSpec,
  positionAnimationSpec: FiniteAnimationSpec<IntOffset> = AnimatedFlowRowDefaults.positionAnimationSpec,
  content: AnimatedFlowRowScope.() -> Unit
) {
  val scope = rememberCoroutineScope()
  val positionAnimatables: SnapshotStateMap<Any, Animatable<IntOffset, *>> = remember { mutableStateMapOf() }
  val alphaAnimatables: SnapshotStateMap<Any, Animatable<Float, *>> = remember { mutableStateMapOf() }
  val knownKeys = remember { mutableSetOf<Any>() }

  val flowRowScope = remember { AnimatedFlowRowScope() }
  flowRowScope.items.clear()
  flowRowScope.content()

  // Key operations run each recomposition to track additions/removals synchronously
  val itemKeys = flowRowScope.items.map { it.first }
  val currentKeysSet = itemKeys.toSet()

  // Determine which keys are new (not seen before) - check synchronously
  val newKeys = currentKeysSet - knownKeys
  val hasExistingItems = knownKeys.isNotEmpty()

  // Pre-initialize alpha for new items to 0 if there are existing items
  // This prevents flicker by ensuring they start invisible BEFORE first render
  newKeys.forEach { key ->
    if (hasExistingItems) {
      alphaAnimatables[key] = Animatable(0f)
    }
    knownKeys.add(key)
  }

  // Clean up animatables for removed items
  val removedKeys = knownKeys - currentKeysSet
  removedKeys.forEach { key ->
    positionAnimatables.remove(key)
    alphaAnimatables.remove(key)
    knownKeys.remove(key)
  }

  val layoutModifier = if (sizeAnimationSpec != null) {
    modifier.animateContentSize(animationSpec = sizeAnimationSpec)
  } else {
    modifier
  }

  Layout(
    content = {
      flowRowScope.items.forEach { (itemKey, itemContent) ->
        key(itemKey) {
          val alpha = alphaAnimatables[itemKey]?.value ?: 1f
          Box(
            modifier = Modifier
              .layoutId(itemKey)
              .alpha(alpha)
          ) {
            itemContent()
          }
        }
      }
    },
    modifier = layoutModifier
  ) { measurables, constraints ->
    val placeables = measurables.map { it.measure(Constraints()) }
    val keyToPlaceable = measurables.zip(placeables).associate { (measurable, placeable) ->
      measurable.layoutId to placeable
    }

    // Calculate flow row positions (centered, wrapping)
    val (totalHeight, positions) = calculateFlowRowPositions(measurables, placeables, constraints.maxWidth)

    // Initialize animatables for new items and trigger animations for existing items
    positions.forEach { (key, targetPosition) ->
      val existingPosition = positionAnimatables[key]
      if (existingPosition == null) {
        // New item - start at target position
        positionAnimatables[key] = Animatable(targetPosition, IntOffset.VectorConverter)
        if (hasExistingItems) {
          // Fade in after position animations complete
          scope.launch {
            delay(AnimatedFlowRowDefaults.ANIMATION_DURATION_MS)
            alphaAnimatables[key]?.animateTo(1f, AnimatedFlowRowDefaults.alphaAnimationSpec)
          }
        } else {
          // First layout, appear immediately
          if (alphaAnimatables[key] == null) {
            alphaAnimatables[key] = Animatable(1f)
          }
        }
      } else if (existingPosition.targetValue != targetPosition) {
        // Item is moving - animate to new position
        scope.launch {
          existingPosition.animateTo(targetPosition, positionAnimationSpec)
        }
      }
    }

    layout(constraints.maxWidth, totalHeight) {
      positions.forEach { (key, _) ->
        val placeable = keyToPlaceable[key]
        val animatable = positionAnimatables[key]
        if (placeable != null && animatable != null) {
          placeable.place(animatable.value.x, animatable.value.y)
        }
      }
    }
  }
}

/**
 * Calculates centered flow row positions for placeables.
 * Returns a pair of (totalHeight, list of (key, position) pairs).
 */
private fun calculateFlowRowPositions(
  measurables: List<Measurable>,
  placeables: List<Placeable>,
  maxWidth: Int
): Pair<Int, List<Pair<Any, IntOffset>>> {
  if (placeables.isEmpty()) return 0 to emptyList()

  val result = mutableListOf<Pair<Any, IntOffset>>()
  val rows = mutableListOf<MutableList<Triple<Any, Measurable, Placeable>>>()
  var currentRow = mutableListOf<Triple<Any, Measurable, Placeable>>()
  var currentRowWidth = 0

  // Group items into rows
  measurables.zip(placeables).forEach { (measurable, placeable) ->
    val key = measurable.layoutId ?: return@forEach
    if (currentRowWidth + placeable.width > maxWidth && currentRow.isNotEmpty()) {
      rows.add(currentRow)
      currentRow = mutableListOf()
      currentRowWidth = 0
    }
    currentRow.add(Triple(key, measurable, placeable))
    currentRowWidth += placeable.width
  }
  if (currentRow.isNotEmpty()) {
    rows.add(currentRow)
  }

  // Calculate total height first
  val totalHeight = rows.sumOf { row -> row.maxOf { it.third.height } }

  // Calculate positions (centered per row, from top to bottom)
  var y = 0
  rows.forEach { row ->
    val rowWidth = row.sumOf { it.third.width }
    val rowHeight = row.maxOf { it.third.height }
    var x = (maxWidth - rowWidth) / 2

    row.forEach { (key, _, placeable) ->
      result.add(key to IntOffset(x, y))
      x += placeable.width
    }
    y += rowHeight
  }

  return totalHeight to result
}
