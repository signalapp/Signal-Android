/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.Previews
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Animation constants for CallGrid
 */
private object CallGridDefaults {
  const val ANIMATION_DURATION_MS = 300L

  private val DefaultEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

  val positionAnimationSpec: FiniteAnimationSpec<IntOffset> = tween(
    durationMillis = ANIMATION_DURATION_MS.toInt(),
    easing = DefaultEasing
  )

  val alphaAnimationSpec: FiniteAnimationSpec<Float> = tween(
    durationMillis = ANIMATION_DURATION_MS.toInt(),
    easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
  )

  val sizeAnimationSpec: FiniteAnimationSpec<IntSize> = tween(
    durationMillis = ANIMATION_DURATION_MS.toInt(),
    easing = DefaultEasing
  )

  val dpAnimationSpec: FiniteAnimationSpec<Dp> = tween(
    durationMillis = ANIMATION_DURATION_MS.toInt(),
    easing = DefaultEasing
  )
}

/**
 * Configuration for a specific grid layout
 */
@Immutable
data class GridConfig(
  val rows: Int,
  val columns: Int,
  val itemsInLastRow: Int,
  val outerPadding: Dp,
  val innerSpacing: Dp,
  val cornerRadius: Dp,
  val aspectRatio: Float?,
  val lastColumnSpansFullHeight: Boolean = false
)

/**
 * Represents a calculated cell position and size in the grid
 */
@Immutable
data class GridCell(
  val index: Int,
  val x: Float,
  val y: Float,
  val width: Float,
  val height: Float
)

/**
 * Internal helper for grid layout parameters
 */
private data class GridLayoutParams(
  val rows: Int,
  val cols: Int,
  val lastRowItems: Int,
  val lastColSpans: Boolean = false
)

/**
 * Strategy for determining grid configuration based on device size
 */
sealed class CallGridStrategy(val maxTiles: Int) {
  abstract fun getConfig(count: Int): GridConfig

  class SmallPortrait : CallGridStrategy(6) {
    override fun getConfig(count: Int): GridConfig {
      val (rows, cols, lastRowItems) = when (count) {
        1 -> GridLayoutParams(1, 1, 1)
        2 -> GridLayoutParams(2, 1, 1)
        3 -> GridLayoutParams(2, 2, 1)
        4 -> GridLayoutParams(2, 2, 2)
        5 -> GridLayoutParams(3, 2, 1)
        else -> GridLayoutParams(3, 2, 2)
      }
      return GridConfig(
        rows = rows,
        columns = cols,
        itemsInLastRow = lastRowItems,
        outerPadding = if (count == 1) 0.dp else 16.dp,
        innerSpacing = if (count == 1) 0.dp else 12.dp,
        cornerRadius = if (count == 1) 0.dp else 32.dp,
        aspectRatio = null
      )
    }
  }

  class SmallLandscape : CallGridStrategy(6) {
    override fun getConfig(count: Int): GridConfig {
      // For 5 items: 2 rows, 3 columns, with the last column (5th item) spanning full height
      val (rows, cols, lastRowItems, lastColSpans) = when (count) {
        1 -> GridLayoutParams(1, 1, 1)
        2 -> GridLayoutParams(1, 2, 2)
        3 -> GridLayoutParams(2, 2, 1)
        4 -> GridLayoutParams(2, 2, 2)
        5 -> GridLayoutParams(2, 3, 1, lastColSpans = true)
        else -> GridLayoutParams(2, 3, 3)
      }
      return GridConfig(
        rows = rows,
        columns = cols,
        itemsInLastRow = lastRowItems,
        outerPadding = if (count == 1) 0.dp else 16.dp,
        innerSpacing = if (count == 1) 0.dp else 12.dp,
        cornerRadius = if (count == 1) 0.dp else 32.dp,
        aspectRatio = null,
        lastColumnSpansFullHeight = lastColSpans
      )
    }
  }

  class Medium : CallGridStrategy(9) {
    override fun getConfig(count: Int): GridConfig {
      val (rows, cols, lastRowItems) = when (count) {
        1 -> GridLayoutParams(1, 1, 1)
        2 -> GridLayoutParams(2, 1, 1)
        3 -> GridLayoutParams(2, 2, 1)
        4 -> GridLayoutParams(2, 2, 2)
        5 -> GridLayoutParams(3, 2, 1)
        6 -> GridLayoutParams(3, 2, 2)
        7 -> GridLayoutParams(3, 3, 1)
        8 -> GridLayoutParams(3, 3, 2)
        else -> GridLayoutParams(3, 3, 3)
      }
      return GridConfig(
        rows = rows,
        columns = cols,
        itemsInLastRow = lastRowItems,
        outerPadding = 24.dp,
        innerSpacing = 12.dp,
        cornerRadius = 32.dp,
        aspectRatio = if (count == 1) 9f / 16f else 5f / 4f
      )
    }
  }

  class Large : CallGridStrategy(12) {
    override fun getConfig(count: Int): GridConfig {
      val (rows, cols, lastRowItems) = when (count) {
        1 -> GridLayoutParams(1, 1, 1)
        2 -> GridLayoutParams(1, 2, 2)
        3 -> GridLayoutParams(1, 3, 3)
        4 -> GridLayoutParams(2, 2, 2)
        5 -> GridLayoutParams(2, 3, 2)
        6 -> GridLayoutParams(2, 3, 3)
        7 -> GridLayoutParams(2, 4, 3)
        8 -> GridLayoutParams(2, 4, 4)
        9 -> GridLayoutParams(3, 4, 1)
        10 -> GridLayoutParams(3, 4, 2)
        11 -> GridLayoutParams(3, 4, 3)
        else -> GridLayoutParams(3, 4, 4)
      }
      return GridConfig(
        rows = rows,
        columns = cols,
        itemsInLastRow = lastRowItems,
        outerPadding = 24.dp,
        innerSpacing = 12.dp,
        cornerRadius = 32.dp,
        aspectRatio = if (count == 1) 9f / 16f else 5f / 4f
      )
    }
  }
}

/**
 * Remembers the appropriate CallGridStrategy based on current window size
 */
@Composable
fun rememberCallGridStrategy(): CallGridStrategy {
  val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

  return remember(windowSizeClass) {
    val isWidthExpanded = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val isWidthMedium = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val isHeightMedium = windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

    when {
      isWidthExpanded && isHeightMedium -> CallGridStrategy.Large()
      isWidthMedium && isHeightMedium -> CallGridStrategy.Medium()
      !isHeightMedium -> CallGridStrategy.SmallLandscape()
      else -> CallGridStrategy.SmallPortrait()
    }
  }
}

/**
 * Calculate grid cell positions and sizes.
 *
 * When aspectRatio is specified: Items are packed together with only inner spacing
 * between them, and the entire group is centered in the container.
 *
 * When aspectRatio is null (compact mode): Items fill the available space, with
 * partial rows stretching to fill the full width.
 *
 * When lastColumnSpansFullHeight is true: The last item occupies the rightmost column
 * and spans the full height (all rows).
 */
private fun calculateGridCells(
  config: GridConfig,
  containerWidth: Float,
  containerHeight: Float,
  itemCount: Int
): List<GridCell> {
  if (itemCount == 0) return emptyList()

  val padding = config.outerPadding.value
  val spacing = config.innerSpacing.value
  val availableWidth = containerWidth - (padding * 2)
  val availableHeight = containerHeight - (padding * 2)

  if (config.lastColumnSpansFullHeight && itemCount > 1) {
    return calculateGridCellsWithSpanningColumn(
      config = config,
      availableWidth = availableWidth,
      availableHeight = availableHeight,
      padding = padding,
      spacing = spacing,
      itemCount = itemCount
    )
  }

  val maxCellWidth = (availableWidth - (spacing * (config.columns - 1))) / config.columns
  val maxCellHeight = (availableHeight - (spacing * (config.rows - 1))) / config.rows

  val (itemWidth, itemHeight) = if (config.aspectRatio != null) {
    val targetAspectRatio = config.aspectRatio
    val cellAspectRatio = maxCellWidth / maxCellHeight

    if (cellAspectRatio > targetAspectRatio) {
      val constrainedWidth = maxCellHeight * targetAspectRatio
      constrainedWidth to maxCellHeight
    } else {
      val constrainedHeight = maxCellWidth / targetAspectRatio
      maxCellWidth to constrainedHeight
    }
  } else {
    maxCellWidth to maxCellHeight
  }

  val totalGridWidth = (config.columns * itemWidth) + ((config.columns - 1) * spacing)
  val totalGridHeight = (config.rows * itemHeight) + ((config.rows - 1) * spacing)

  val gridStartX = padding + (availableWidth - totalGridWidth) / 2
  val gridStartY = padding + (availableHeight - totalGridHeight) / 2

  val cells = mutableListOf<GridCell>()

  var index = 0
  for (row in 0 until config.rows) {
    val isLastRow = row == config.rows - 1
    val itemsInThisRow = if (isLastRow) config.itemsInLastRow else config.columns
    val remainingItems = itemCount - index

    if (remainingItems <= 0) break

    val actualItemsInRow = min(itemsInThisRow, remainingItems)
    val isPartialRow = actualItemsInRow < config.columns

    // Stretch items in partial rows to fill width (compact mode only)
    val cellWidthForRow = if (config.aspectRatio == null && isPartialRow) {
      (totalGridWidth - (spacing * (actualItemsInRow - 1))) / actualItemsInRow
    } else {
      itemWidth
    }

    val rowWidth = (actualItemsInRow * cellWidthForRow) + ((actualItemsInRow - 1) * spacing)
    val rowXOffset = (totalGridWidth - rowWidth) / 2

    for (col in 0 until actualItemsInRow) {
      val x = gridStartX + rowXOffset + col * (cellWidthForRow + spacing)
      val y = gridStartY + row * (itemHeight + spacing)

      cells.add(
        GridCell(
          index = index,
          x = x,
          y = y,
          width = cellWidthForRow,
          height = itemHeight
        )
      )
      index++
    }
  }

  return cells
}

/**
 * Calculate grid cells when the last column spans full height.
 * Layout: Regular grid on the left, with the last item taking the rightmost column
 * and spanning all rows.
 */
private fun calculateGridCellsWithSpanningColumn(
  config: GridConfig,
  availableWidth: Float,
  availableHeight: Float,
  padding: Float,
  spacing: Float,
  itemCount: Int
): List<GridCell> {
  val cells = mutableListOf<GridCell>()

  val columnsForRegularItems = config.columns - 1
  val regularItemCount = itemCount - 1

  val cellWidth = (availableWidth - (spacing * (config.columns - 1))) / config.columns
  val cellHeight = (availableHeight - (spacing * (config.rows - 1))) / config.rows

  val totalGridWidth = (config.columns * cellWidth) + ((config.columns - 1) * spacing)
  val totalGridHeight = (config.rows * cellHeight) + ((config.rows - 1) * spacing)

  val gridStartX = padding + (availableWidth - totalGridWidth) / 2
  val gridStartY = padding + (availableHeight - totalGridHeight) / 2

  // Place regular items in column-major order (fills columns top-to-bottom, left-to-right)
  var index = 0
  for (col in 0 until columnsForRegularItems) {
    for (row in 0 until config.rows) {
      if (index >= regularItemCount) break

      val x = gridStartX + col * (cellWidth + spacing)
      val y = gridStartY + row * (cellHeight + spacing)

      cells.add(
        GridCell(
          index = index,
          x = x,
          y = y,
          width = cellWidth,
          height = cellHeight
        )
      )
      index++
    }
  }

  // Spanning item takes full height
  val spanningX = gridStartX + columnsForRegularItems * (cellWidth + spacing)
  val spanningY = gridStartY
  val spanningHeight = totalGridHeight

  cells.add(
    GridCell(
      index = regularItemCount,
      x = spanningX,
      y = spanningY,
      width = cellWidth,
      height = spanningHeight
    )
  )

  return cells
}

/**
 * An animated grid layout for call participants.
 *
 * Features:
 * - Smooth position animations when items move
 * - Fade-in animation for new items (after position animations complete)
 * - Crossfade for swapped items (same position, different participant)
 * - Device-aware grid configurations
 *
 * @param items List of items to display, each with a stable key
 * @param modifier Modifier for the grid container
 * @param itemKey Function to extract a stable key from each item
 * @param content Composable content for each item
 */
@Composable
fun <T> CallGrid(
  items: List<T>,
  modifier: Modifier = Modifier,
  itemKey: (T) -> Any,
  content: @Composable (item: T, modifier: Modifier) -> Unit
) {
  val strategy = rememberCallGridStrategy()
  val scope = rememberCoroutineScope()

  val positionAnimatables: SnapshotStateMap<Any, Animatable<IntOffset, *>> = remember { mutableStateMapOf() }
  val sizeAnimatables: SnapshotStateMap<Any, Animatable<IntSize, *>> = remember { mutableStateMapOf() }
  val alphaAnimatables: SnapshotStateMap<Any, Animatable<Float, *>> = remember { mutableStateMapOf() }
  val knownKeys = remember { mutableSetOf<Any>() }

  val displayCount = min(items.size, strategy.maxTiles)
  val displayItems = items.take(displayCount)
  val config = remember(strategy, displayCount) { strategy.getConfig(displayCount) }

  val animatedCornerRadius by animateDpAsState(
    targetValue = config.cornerRadius,
    animationSpec = CallGridDefaults.dpAnimationSpec,
    label = "cornerRadius"
  )

  val currentKeys = displayItems.map { itemKey(it) }.toSet()
  val newKeys = currentKeys - knownKeys
  val hasExistingItems = knownKeys.isNotEmpty()

  newKeys.forEach { key ->
    if (hasExistingItems) {
      alphaAnimatables[key] = Animatable(0f)
    }
    knownKeys.add(key)
  }

  val removedKeys = knownKeys - currentKeys
  removedKeys.forEach { key ->
    positionAnimatables.remove(key)
    sizeAnimatables.remove(key)
    alphaAnimatables.remove(key)
    knownKeys.remove(key)
  }

  BoxWithConstraints(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    val containerWidthPx = constraints.maxWidth.toFloat()
    val containerHeightPx = constraints.maxHeight.toFloat()

    val cells = remember(config, containerWidthPx, containerHeightPx, displayCount) {
      calculateGridCells(
        config = config,
        containerWidth = containerWidthPx,
        containerHeight = containerHeightPx,
        itemCount = displayCount
      )
    }

    val density = LocalDensity.current

    Layout(
      content = {
        displayItems.forEachIndexed { index, item ->
          val itemKeyValue = itemKey(item)
          key(itemKeyValue) {
            val alpha = alphaAnimatables[itemKeyValue]?.value ?: 1f
            val animatedSize = sizeAnimatables[itemKeyValue]?.value
            val cell = cells.getOrNull(index)

            if (cell != null) {
              val widthPx = animatedSize?.width ?: cell.width.roundToInt()
              val heightPx = animatedSize?.height ?: cell.height.roundToInt()

              Box(
                modifier = Modifier
                  .layoutId(itemKeyValue)
                  .alpha(alpha)
              ) {
                content(
                  item,
                  Modifier
                    .size(
                      width = with(density) { widthPx.toDp() },
                      height = with(density) { heightPx.toDp() }
                    )
                    .clip(RoundedCornerShape(animatedCornerRadius))
                )
              }
            }
          }
        }
      }
    ) { measurables, constraints ->
      displayItems.forEachIndexed { index, item ->
        val itemKeyValue = itemKey(item)
        val cell = cells.getOrNull(index) ?: return@forEachIndexed
        val targetPosition = IntOffset(cell.x.roundToInt(), cell.y.roundToInt())
        val targetSize = IntSize(cell.width.roundToInt(), cell.height.roundToInt())

        val existingPosition = positionAnimatables[itemKeyValue]
        if (existingPosition == null) {
          positionAnimatables[itemKeyValue] = Animatable(targetPosition, IntOffset.VectorConverter)
          if (hasExistingItems && itemKeyValue in newKeys) {
            scope.launch {
              delay(CallGridDefaults.ANIMATION_DURATION_MS)
              alphaAnimatables[itemKeyValue]?.animateTo(1f, CallGridDefaults.alphaAnimationSpec)
            }
          } else {
            if (alphaAnimatables[itemKeyValue] == null) {
              alphaAnimatables[itemKeyValue] = Animatable(1f)
            }
          }
        } else if (existingPosition.targetValue != targetPosition) {
          scope.launch {
            existingPosition.animateTo(targetPosition, CallGridDefaults.positionAnimationSpec)
          }
        }

        val existingSize = sizeAnimatables[itemKeyValue]
        if (existingSize == null) {
          sizeAnimatables[itemKeyValue] = Animatable(targetSize, IntSize.VectorConverter)
        } else if (existingSize.targetValue != targetSize) {
          scope.launch {
            existingSize.animateTo(targetSize, CallGridDefaults.sizeAnimationSpec)
          }
        }
      }

      val placeables = measurables.mapIndexed { index, measurable ->
        val itemKeyValue = displayItems.getOrNull(index)?.let { itemKey(it) }
        val animatedSize = itemKeyValue?.let { sizeAnimatables[it]?.value }
        val cell = cells.getOrNull(index)

        if (animatedSize != null) {
          measurable.measure(
            Constraints.fixed(animatedSize.width, animatedSize.height)
          )
        } else if (cell != null) {
          measurable.measure(
            Constraints.fixed(
              cell.width.roundToInt(),
              cell.height.roundToInt()
            )
          )
        } else {
          measurable.measure(Constraints())
        }
      }

      val keyToPlaceable = measurables.zip(placeables).associate { (measurable, placeable) ->
        measurable.layoutId to placeable
      }

      layout(constraints.maxWidth, constraints.maxHeight) {
        displayItems.forEach { item ->
          val itemKeyValue = itemKey(item)
          val placeable = keyToPlaceable[itemKeyValue]
          val position = positionAnimatables[itemKeyValue]?.value

          if (placeable != null && position != null) {
            placeable.place(position.x, position.y)
          }
        }
      }
    }
  }
}

// Preview

@AllNightPreviews
@Composable
private fun CallGridPreview() {
  Previews.Preview {
    var count by remember { mutableStateOf(1) }
    val items = remember(count) { (1..count).toList() }

    val colors = listOf(
      Color(0xFF5E97F6),
      Color(0xFF9CCC65),
      Color(0xFFFFB74D),
      Color(0xFFEF5350),
      Color(0xFFAB47BC),
      Color(0xFF26A69A),
      Color(0xFF78909C),
      Color(0xFFEC407A),
      Color(0xFF7E57C2),
      Color(0xFF29B6F6),
      Color(0xFFD4E157),
      Color(0xFFFF7043)
    )

    Box(modifier = Modifier.fillMaxSize()) {
      CallGrid(
        items = items,
        modifier = Modifier.fillMaxSize(),
        itemKey = { it }
      ) { item, itemModifier ->
        Box(
          modifier = itemModifier.background(colors[(item - 1) % colors.size]),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = item.toString(),
            color = Color.White
          )
        }
      }

      Row(
        modifier = Modifier.align(Alignment.TopStart)
      ) {
        Button(onClick = { count = max(1, count - 1) }) {
          Text("-")
        }
        Button(onClick = { count = min(12, count + 1) }) {
          Text("+")
        }
        Text(
          text = "Count: $count",
          modifier = Modifier.padding(16.dp),
          color = Color.White
        )
      }
    }
  }
}
