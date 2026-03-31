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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.flow.MutableStateFlow
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
  // Plain map (not snapshot state) so initialization inside the Layout measurement lambda is safe.
  val positionAnimatables: MutableMap<Any, Animatable<IntOffset, *>> = remember { mutableMapOf() }
  val alphaAnimatables: SnapshotStateMap<Any, Animatable<Float, *>> = remember { mutableStateMapOf() }
  val knownKeys = remember { mutableSetOf<Any>() }
  val firstSeenInLayout = remember { mutableSetOf<Any>() }

  // MutableStateFlow (not snapshot state) bridges Layout measurement to composition-phase
  // animation launches, so position writes inside Layout measurement are safe.
  val pendingPositions = remember { MutableStateFlow<Map<Any, IntOffset>>(emptyMap()) }

  val flowRowScope = remember { AnimatedFlowRowScope() }
  flowRowScope.items.clear()
  flowRowScope.content()

  val itemKeys = flowRowScope.items.map { it.first }
  val currentKeysSet = itemKeys.toSet()
  val newKeys = currentKeysSet - knownKeys
  val hasExistingItems = knownKeys.isNotEmpty()

  newKeys.forEach { key ->
    alphaAnimatables[key] = if (hasExistingItems) Animatable(0f) else Animatable(1f)
    knownKeys.add(key)
  }

  val removedKeys = knownKeys - currentKeysSet
  removedKeys.forEach { key ->
    positionAnimatables.remove(key)
    alphaAnimatables.remove(key)
    firstSeenInLayout.remove(key)
    knownKeys.remove(key)
  }

  // Animation launches live here, not inside the Layout measurement lambda, so the Layout
  // remains free of side effects that can interact with Compose's node deactivation.
  LaunchedEffect(Unit) {
    pendingPositions.collect { positions ->
      positions.forEach { (key, targetPos) ->
        val posAnim = positionAnimatables[key] ?: return@forEach
        val isFirstSeen = firstSeenInLayout.add(key)
        if (isFirstSeen) {
          if (alphaAnimatables[key]?.value == 0f) {
            launch {
              kotlinx.coroutines.delay(AnimatedFlowRowDefaults.ANIMATION_DURATION_MS)
              alphaAnimatables[key]?.animateTo(1f, AnimatedFlowRowDefaults.alphaAnimationSpec)
            }
          }
        } else if (posAnim.targetValue != targetPos) {
          launch { posAnim.animateTo(targetPos, positionAnimationSpec) }
        }
      }
    }
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

    val (totalHeight, positions) = calculateFlowRowPositions(measurables, placeables, constraints.maxWidth)

    // Plain map mutation is safe inside Layout measurement; snapshot state mutation is not.
    positions.forEach { (key, targetPosition) ->
      if (positionAnimatables[key] == null) {
        positionAnimatables[key] = Animatable(targetPosition, IntOffset.VectorConverter)
      }
    }

    pendingPositions.value = positions.toMap()

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

  val totalHeight = rows.sumOf { row -> row.maxOf { it.third.height } }

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
