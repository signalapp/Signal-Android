/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val THUMB_PADDING = 16.dp
private val THUMB_HEIGHT = 48.dp
private val THUMB_WIDTH = 12.dp
private val TEARDROP_SIZE = 72.dp
private val TEARDROP_PADDING = 16.dp

/**
 * Wraps a LazyColumn with a fast-scroller that runs along the 'end' of the content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LazyColumnFastScroller(
  fastScrollerState: FastScrollerState,
  lazyListState: LazyListState = rememberLazyListState(),
  modifier: Modifier = Modifier,
  letterContent: @Composable (CharSequence) -> Unit = {
    Text(text = it.toString(), style = MaterialTheme.typography.headlineLarge)
  },
  enabled: Boolean = true,
  userScrollEnabled: Boolean = true,
  content: @Composable BoxScope.(LazyListState) -> Unit
) {
  val context = LocalContext.current

  BoxWithConstraints(
    modifier = modifier
  ) {
    val maxHeight = maxHeight
    val progress by rememberScrollProgress(lazyListState, totalCount = fastScrollerState.totalCount)

    var isDragInProgress by remember { mutableStateOf(false) }

    val dragState = rememberDragState(
      maxHeight = maxHeight,
      thumbHeight = THUMB_HEIGHT,
      totalCount = fastScrollerState.totalCount,
      isDragInProgress = isDragInProgress,
      lazyListState = lazyListState
    )

    val character = remember(progress, fastScrollerState.totalCount) {
      val targetIndex = (fastScrollerState.totalCount * progress).roundToInt()
      val clampedIndex = targetIndex.coerceIn(0, (fastScrollerState.totalCount - 1).coerceAtLeast(0))
      fastScrollerState.getFastScrollCharacter(context, clampedIndex)
    }

    content(lazyListState)

    if (!enabled) {
      return@BoxWithConstraints
    }

    Box(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .width(TEARDROP_SIZE + TEARDROP_PADDING * 2)
        .fillMaxHeight()
        .padding(vertical = THUMB_PADDING)
    ) {
      AnimatedVisibility(
        visible = isDragInProgress,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        LetterTeardrop(
          letter = character,
          hardCorner = if (progress > 0.5f) HardCorner.BOTTOM else HardCorner.TOP,
          content = letterContent,
          modifier = Modifier.offset {
            val maxTravelPx = (maxHeight - (THUMB_PADDING * 2) - TEARDROP_SIZE).toPx()
            val yOffset = (maxTravelPx * progress).roundToInt()

            IntOffset(0, yOffset)
          }
        )
      }

      val view = LocalView.current
      Box(
        modifier = Modifier
          .offset {
            val maxTravelPx = (maxHeight - (THUMB_PADDING * 2) - THUMB_HEIGHT).toPx()
            val yOffset = (maxTravelPx * progress).roundToInt()

            IntOffset(0, yOffset)
          }
          .width(48.dp)
          .height(THUMB_HEIGHT)
          .pointerInput(dragState, userScrollEnabled) {
            if (!userScrollEnabled) return@pointerInput
            awaitPointerEventScope {
              while (true) {
                val down = awaitFirstDown(requireUnconsumed = false)

                view.parent?.requestDisallowInterceptTouchEvent(true)
                isDragInProgress = true

                val dragPointerId = down.id
                while (true) {
                  val event = awaitPointerEvent()
                  val dragEvent = event.changes.firstOrNull { it.id == dragPointerId }

                  if (dragEvent == null || dragEvent.isConsumed || !dragEvent.pressed) {
                    break
                  }

                  view.parent?.requestDisallowInterceptTouchEvent(true)

                  val delta = dragEvent.positionChange().y
                  dragState.dispatchRawDelta(delta)
                  dragEvent.consume()
                }

                isDragInProgress = false
                view.parent?.requestDisallowInterceptTouchEvent(false)
              }
            }
          }
          .align(Alignment.TopEnd)
          .padding(start = 24.dp, end = THUMB_PADDING)
          .background(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(50)
          )
      )
    }
  }
}

@Composable
private fun LetterTeardrop(
  letter: CharSequence,
  hardCorner: HardCorner,
  modifier: Modifier = Modifier,
  content: @Composable (CharSequence) -> Unit = {
    Text(text = letter.toString(), style = MaterialTheme.typography.headlineLarge)
  }
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .size(72.dp)
      .background(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(
          topStartPercent = 50,
          topEndPercent = if (hardCorner == HardCorner.TOP) 0 else 50,
          bottomStartPercent = 50,
          bottomEndPercent = if (hardCorner == HardCorner.BOTTOM) 0 else 50
        )
      )
  ) {
    content(letter)
  }
}

@Immutable
data class FastScrollerState(
  val totalCount: Int,
  private val items: List<Any>
) {
  fun getFastScrollCharacter(context: Context, index: Int): CharSequence {
    val model = items.getOrNull(index)
    return if (model is FastScrollCharacterProvider) {
      model.getFastScrollCharacter(context)
    } else {
      " "
    }
  }
}

interface FastScrollCharacterProvider {
  fun getFastScrollCharacter(context: Context): CharSequence
}

private enum class HardCorner {
  TOP, BOTTOM
}

@DayNightPreviews
@Composable
private fun FastScrollerPreview() {
  val charPool = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toList()
  val items = remember {
    (1 until 100).map {
      FastScrollTestContainer(charPool.shuffled().take(5).joinToString(""))
    }.sortedBy { it.word }
  }

  val fastScrollerState = remember {
    FastScrollerState(100, items)
  }

  Previews.Preview {
    LazyColumnFastScroller(
      fastScrollerState = fastScrollerState,
      modifier = Modifier.fillMaxSize()
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = it
      ) {
        items(items) { item ->
          Rows.TextRow(text = item.word)
        }
      }
    }
  }
}

private data class FastScrollTestContainer(
  val word: String
) : FastScrollCharacterProvider {
  override fun getFastScrollCharacter(context: Context): CharSequence {
    return word.first().toString()
  }
}

@DayNightPreviews
@Composable
private fun LetterTeardropTopPreview() {
  Previews.Preview {
    LetterTeardrop("A", hardCorner = HardCorner.TOP, modifier = Modifier.padding(16.dp))
  }
}

@DayNightPreviews
@Composable
private fun LetterTeardropBottomPreview() {
  Previews.Preview {
    LetterTeardrop("A", hardCorner = HardCorner.BOTTOM, modifier = Modifier.padding(16.dp))
  }
}

@Composable
private fun rememberScrollProgress(listState: LazyListState, totalCount: Int): State<Float> {
  return remember(listState, totalCount) {
    derivedStateOf {
      val layoutInfo = listState.layoutInfo
      val visibleItemsInfo = layoutInfo.visibleItemsInfo

      if (totalCount <= 0 || visibleItemsInfo.isEmpty()) {
        0f
      } else {
        val firstVisibleIndex = listState.firstVisibleItemIndex

        val firstItem = visibleItemsInfo.first()
        val firstItemOffsetFraction = if (firstItem.size > 0) {
          listState.firstVisibleItemScrollOffset.toFloat() / firstItem.size
        } else {
          0f
        }

        val currentSmoothIndex = firstVisibleIndex + firstItemOffsetFraction
        val maxPossibleTopIndex = (totalCount - visibleItemsInfo.size).coerceAtLeast(1)

        (currentSmoothIndex / maxPossibleTopIndex).coerceIn(0f, 1f)
      }
    }
  }
}

@Composable
private fun rememberDragState(
  maxHeight: Dp,
  thumbHeight: Dp,
  totalCount: Int,
  isDragInProgress: Boolean,
  lazyListState: LazyListState
): DraggableState {
  val density = LocalDensity.current
  val coroutineScope = rememberCoroutineScope()
  var accumulatedProgress by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(lazyListState.isScrollInProgress, isDragInProgress) {
    if (!lazyListState.isScrollInProgress && !isDragInProgress && totalCount > 0) {
      val visibleCount = lazyListState.layoutInfo.visibleItemsInfo.size
      val maxIndex = (totalCount - visibleCount).coerceAtLeast(1)
      accumulatedProgress = lazyListState.firstVisibleItemIndex.toFloat() / maxIndex
    }
  }

  return rememberDraggableState { deltaPixels ->
    if (totalCount <= 0) return@rememberDraggableState

    val maxThumbTravelPx = with(density) { (maxHeight - thumbHeight).toPx() }
    if (maxThumbTravelPx <= 0f) return@rememberDraggableState

    val deltaProgress = deltaPixels / maxThumbTravelPx
    accumulatedProgress = (accumulatedProgress + deltaProgress).coerceIn(0f, 1f)

    val visibleCount = lazyListState.layoutInfo.visibleItemsInfo.size
    val maxTargetIndex = (totalCount - visibleCount).coerceAtLeast(0)
    val targetIndex = (accumulatedProgress * maxTargetIndex).toInt()

    coroutineScope.launch {
      lazyListState.scrollToItem(targetIndex)
    }
  }
}
