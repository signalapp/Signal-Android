/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Animation constants for CallGrid
 */
internal object CallGridDefaults {
  const val ANIMATION_DURATION_MS = 350L

  private val DefaultEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

  private inline fun <reified T> defaultTween(): FiniteAnimationSpec<T> = tween(
    durationMillis = ANIMATION_DURATION_MS.toInt(),
    easing = DefaultEasing
  )

  val positionAnimationSpec: FiniteAnimationSpec<IntOffset> = defaultTween()
  val alphaAnimationSpec: FiniteAnimationSpec<Float> = defaultTween()
  val sizeAnimationSpec: FiniteAnimationSpec<IntSize> = defaultTween()
  val dpAnimationSpec: FiniteAnimationSpec<Dp> = defaultTween()
  val scaleAnimationSpec: FiniteAnimationSpec<Float> = defaultTween()

  const val ENTER_SCALE_START = 0.9f
  const val ENTER_SCALE_END = 1f
  const val EXIT_SCALE_END = 0.9f
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
        3 -> GridLayoutParams(2, 2, 1, lastColSpans = true)
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

private const val WIDTH_DP_LARGE_LOWER_BOUND = 1200

@Composable
fun rememberCallGridStrategy(): CallGridStrategy {
  val windowSizeClass = currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true).windowSizeClass

  return remember(windowSizeClass) {
    val isWidthLarge = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_LARGE_LOWER_BOUND)
    val isWidthMedium = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val isHeightMedium = windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

    when {
      isWidthLarge && isHeightMedium -> CallGridStrategy.Large()
      isWidthMedium && isHeightMedium -> CallGridStrategy.Medium()
      !isHeightMedium -> CallGridStrategy.SmallLandscape()
      else -> CallGridStrategy.SmallPortrait()
    }
  }
}

/**
 * Observes a participant's video sink and returns their video's aspect ratio.
 *
 * This attaches a lightweight VideoSink to capture frame dimensions from the
 * participant's video stream. The sink is automatically removed when the
 * videoSink changes or the composable leaves composition.
 *
 * @param videoSink The participant's BroadcastVideoSink to observe, or null
 * @return The aspect ratio (width/height) of the video, or null if not yet known
 */
@Composable
fun rememberParticipantAspectRatio(videoSink: BroadcastVideoSink?): Float? {
  var aspectRatio by remember { mutableStateOf<Float?>(null) }

  DisposableEffect(videoSink) {
    if (videoSink == null) {
      aspectRatio = null
      return@DisposableEffect onDispose { }
    }

    val dimensionSink = object : VideoSink {
      override fun onFrame(frame: VideoFrame) {
        val width = frame.rotatedWidth
        val height = frame.rotatedHeight
        if (width > 0 && height > 0) {
          val newAspectRatio = width.toFloat() / height.toFloat()
          if (aspectRatio != newAspectRatio) {
            aspectRatio = newAspectRatio
          }
        }
      }
    }

    videoSink.addSink(dimensionSink)

    onDispose {
      videoSink.removeSink(dimensionSink)
    }
  }

  return aspectRatio
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
 * Holds an item being tracked by [CallGrid], along with whether it should animate in on entry.
 */
private data class ManagedItem<T>(val item: T, val animateEnter: Boolean)

/**
 * An animated grid layout for call participants.
 *
 * Features:
 * - Smooth position animations when items move
 * - Fade-in/scale-in animation for new items (0% to 100% opacity, 90% to 100% scale)
 * - Fade-out/scale-out animation for removed items (100% to 0% opacity, 100% to 90% scale)
 * - Device-aware grid configurations
 *
 * @param items List of items to display, each with a stable key
 * @param modifier Modifier for the grid container
 * @param singleParticipantAspectRatio Optional aspect ratio override for single-participant display.
 *   When provided and there's only one participant, this replaces the default 9:16 aspect ratio,
 *   allowing the video to display at its native dimensions (e.g., 16:9 for landscape video).
 * @param itemKey Function to extract a stable key from each item
 * @param content Composable content for each item
 */
@Composable
fun <T> CallGrid(
  items: List<T>,
  modifier: Modifier = Modifier,
  singleParticipantAspectRatio: Float? = null,
  itemKey: (T) -> Any,
  content: @Composable (item: T, modifier: Modifier) -> Unit
) {
  val strategy = rememberCallGridStrategy()
  val displayCount = min(items.size, strategy.maxTiles)
  val displayItems = items.take(displayCount)
  val baseConfig = remember(strategy, displayCount) { strategy.getConfig(displayCount) }
  val config = if (displayCount == 1 && singleParticipantAspectRatio != null && baseConfig.aspectRatio != null) {
    baseConfig.copy(aspectRatio = singleParticipantAspectRatio)
  } else {
    baseConfig
  }

  val animatedCornerRadius by animateDpAsState(
    targetValue = config.cornerRadius,
    animationSpec = CallGridDefaults.dpAnimationSpec,
    label = "cornerRadius"
  )

  var containerSize by remember { mutableStateOf(IntSize.Zero) }
  val density = LocalDensity.current

  val cells = remember(config, containerSize, displayCount) {
    if (containerSize == IntSize.Zero) emptyList()
    else calculateGridCells(
      config = config,
      containerWidth = containerSize.width.toFloat(),
      containerHeight = containerSize.height.toFloat(),
      itemCount = displayCount
    )
  }

  // Holds all items currently in the grid, including those still animating out.
  val managedItems: SnapshotStateMap<Any, ManagedItem<T>> = remember { mutableStateMapOf() }

  // lastKnownCells freezes the last grid position for items that are animating out so they
  // stay in place (rather than jumping to zero) while their exit animation plays.
  val lastKnownCells = remember { mutableMapOf<Any, GridCell>() }

  val currentKeys = displayItems.map { itemKey(it) }.toSet()
  val hasExistingItems = managedItems.isNotEmpty()

  SideEffect {
    displayItems.forEach { item ->
      val key = itemKey(item)
      if (key !in managedItems) {
        managedItems[key] = ManagedItem(item, animateEnter = hasExistingItems)
      } else {
        managedItems[key] = managedItems[key]!!.copy(item = item)
      }
    }
  }

  Box(modifier = modifier.onSizeChanged { containerSize = it }) {
    managedItems.entries.toList().forEach { (key, managed) ->
      val index = displayItems.indexOfFirst { itemKey(it) == key }
      val targetCell = cells.getOrNull(index)
      if (targetCell != null) lastKnownCells[key] = targetCell
      val effectiveCell = targetCell ?: lastKnownCells[key] ?: return@forEach

      key(key) {
        var isVisible by remember { mutableStateOf(!managed.animateEnter) }
        LaunchedEffect(Unit) { isVisible = true }

        AnimatedVisibility(
          visible = isVisible && key in currentKeys,
          enter = scaleIn(
            initialScale = CallGridDefaults.ENTER_SCALE_START,
            animationSpec = CallGridDefaults.scaleAnimationSpec
          ) + fadeIn(animationSpec = CallGridDefaults.alphaAnimationSpec),
          exit = scaleOut(
            targetScale = CallGridDefaults.EXIT_SCALE_END,
            animationSpec = CallGridDefaults.scaleAnimationSpec
          ) + fadeOut(animationSpec = CallGridDefaults.alphaAnimationSpec)
        ) {
          DisposableEffect(Unit) {
            onDispose { managedItems.remove(key) }
          }

          val targetPosition = IntOffset(effectiveCell.x.roundToInt(), effectiveCell.y.roundToInt())
          val targetSize = IntSize(effectiveCell.width.roundToInt(), effectiveCell.height.roundToInt())

          val positionAnim = remember { Animatable(targetPosition, IntOffset.VectorConverter) }
          val sizeAnim = remember { Animatable(targetSize, IntSize.VectorConverter) }

          // LaunchedEffect is tied to this composable's lifecycle and cancels automatically
          // when the item leaves composition, preventing any deactivated-node interaction.
          LaunchedEffect(targetPosition) {
            positionAnim.animateTo(targetPosition, CallGridDefaults.positionAnimationSpec)
          }
          LaunchedEffect(targetSize) {
            sizeAnim.animateTo(targetSize, CallGridDefaults.sizeAnimationSpec)
          }

          Box(modifier = Modifier.absoluteOffset { positionAnim.value }) {
            content(
              managed.item,
              Modifier
                .size(
                  width = with(density) { sizeAnim.value.width.toDp() },
                  height = with(density) { sizeAnim.value.height.toDp() }
                )
                .clip(RoundedCornerShape(animatedCornerRadius))
            )
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
    var nextId by remember { mutableStateOf(2) }
    val items = remember { mutableStateListOf(1) }

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

    val windowSizeClass = currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true).windowSizeClass
    val strategy = rememberCallGridStrategy()

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

      Text(
        text = "WSC: ${windowSizeClass.minWidthDp}x${windowSizeClass.minHeightDp}\n" +
          "Strategy: ${strategy::class.simpleName}",
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.align(Alignment.TopEnd)
      )

      Row(
        modifier = Modifier.align(Alignment.TopStart)
      ) {
        Button(onClick = { if (items.size > 1) items.removeAt(items.size - 1) }) {
          Text("-")
        }
        Button(onClick = { if (items.size < 12) { items.add(nextId); nextId++ } }) {
          Text("+")
        }
        Button(onClick = {
          if (items.size > 1) {
            val index = (0 until items.size).random()
            items.removeAt(index)
          }
        }) {
          Text("-R")
        }
        Button(onClick = {
          if (items.size < 12) {
            val index = (0..items.size).random()
            items.add(index, nextId)
            nextId++
          }
        }) {
          Text("+R")
        }
      }
    }
  }
}
