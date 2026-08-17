/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.signal.core.models.media.Media
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.list.ReorderableItem
import org.signal.core.ui.compose.list.rememberReorderBuffer
import org.signal.core.ui.compose.list.rememberReorderableListState
import org.signal.core.ui.compose.list.reorderableList
import org.signal.core.util.ContentTypeUtil
import org.signal.glide.compose.GlideImage
import org.signal.mediasend.screens.MediaSendMetrics
import org.signal.mediasend.test.TestTags
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private val BASE_SPACING = 4.dp
private val MIN_PADDING = 0.dp
private val MAX_PADDING = 8.dp

/**
 * Horizontally scrollable thumbnail strip that syncs with [pagerState].
 * Features fish-eye padding effect where the centered item has more padding.
 *
 * Dragging the strip scrolls the pager, and which media the settled page focuses is reported by the caller from
 * [pagerState] rather than from here, since this row is only one of the chromes the pager can be swiped beneath.
 */
@Composable
internal fun ThumbnailRow(
  selectedMedia: List<Media>,
  pagerState: PagerState,
  onThumbnailClick: (Int) -> Unit = {},
  onReorder: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
  enabled: Boolean = true
) {
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()

  val itemWidthPx = with(density) { MediaSendMetrics.SelectedMediaPreviewSize.width.toPx() }
  val baseSpacingPx = with(density) { BASE_SPACING.toPx() }
  val itemStride = itemWidthPx + baseSpacingPx
  val pagerPageSize = pagerState.layoutInfo.pageSize.takeIf { it > 0 } ?: 1
  val listState = rememberLazyListState()

  val reorderBuffer = rememberReorderBuffer(selectedMedia, onReorder)
  val reorderableListState = rememberReorderableListState(
    lazyListState = listState,
    includeHeader = false,
    includeFooter = false,
    orientation = Orientation.Horizontal,
    autoScroll = false,
    onEvent = reorderBuffer::onReorderListEvent
  )
  val isReordering = reorderableListState.draggingItemIndex != null

  // A thumbnail's page has to be looked up by identity rather than taken from its slot in the row: for the length of a
  // drag the row renders the order that drag has built up while the pager is still on the pre-drag one, so the two
  // disagree about what any given index refers to. Everything keyed off the pager - the delete affordance and the
  // fish-eye - has to follow the media rather than the index, or it lands on whatever the drag has shuffled into the
  // pager's slot and only corrects itself once the new order arrives.
  val pageIndices = remember(selectedMedia) {
    selectedMedia.withIndex().associate { (index, media) -> media.uri to index }
  }

  val draggableState = rememberDraggableState { delta ->
    val scaledDelta = delta * (pagerPageSize.toFloat() / itemStride)
    pagerState.dispatchRawDelta(-scaledDelta)
  }

  // The rail's scroll position belongs to the drag rather than the pager from the moment an item is picked up until the
  // reorder it produced has landed in state. Resuming any earlier means syncing to the pre-drag order and then having to
  // correct once the new order arrives. The single catch-up afterwards is animated so the rail glides to the dropped
  // item's slot rather than snapping to it.
  LaunchedEffect(pagerState, itemStride, selectedMedia.size) {
    if (selectedMedia.isEmpty()) return@LaunchedEffect

    var isCatchingUp = false

    snapshotFlow {
      val isReordering = reorderableListState.draggingItemIndex != null || reorderBuffer.isReordering
      (pagerState.currentPage + pagerState.currentPageOffsetFraction) to isReordering
    }
      .distinctUntilChanged()
      .collectLatest { (position, isReordering) ->
        if (isReordering) {
          isCatchingUp = true
          return@collectLatest
        }

        val clampedPosition = position.coerceIn(0f, selectedMedia.lastIndex.toFloat())
        val baseIndex = floor(clampedPosition.toDouble()).toInt()
        val fraction = (clampedPosition - baseIndex).coerceIn(0f, 1f)
        val scrollOffsetPx = (fraction * itemStride).roundToInt()

        if (isCatchingUp) {
          // Left set until the animation actually finishes: if the pager retargets midway, collectLatest cancels this
          // and the next pass animates on from wherever the rail got to instead of snapping.
          listState.animateScrollToItem(baseIndex, scrollOffsetPx)
          isCatchingUp = false
        } else {
          listState.scrollToItem(baseIndex, scrollOffsetPx)
        }
      }
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .draggable(
        state = draggableState,
        orientation = Orientation.Horizontal,
        enabled = enabled && !isReordering,
        onDragStopped = { velocity ->
          scope.launch {
            val targetPage = when {
              velocity > 500f -> (pagerState.currentPage - 1).coerceAtLeast(0)
              velocity < -500f -> (pagerState.currentPage + 1).coerceAtMost(selectedMedia.lastIndex)
              else -> pagerState.currentPage
            }
            pagerState.animateScrollToPage(targetPage)
          }
        }
      )
  ) {
    val itemWidth = MediaSendMetrics.SelectedMediaPreviewSize.width

    val baseEdgePadding = ((maxWidth - itemWidth) / 2).coerceAtLeast(0.dp)
    val startPadding = (baseEdgePadding - MAX_PADDING).coerceAtLeast(0.dp)
    val endPadding = baseEdgePadding + MAX_PADDING

    LazyRow(
      horizontalArrangement = spacedBy(BASE_SPACING),
      contentPadding = PaddingValues(start = startPadding, end = endPadding),
      state = listState,
      userScrollEnabled = false,
      modifier = if (enabled) Modifier.reorderableList(reorderableListState) else Modifier
    ) {
      itemsIndexed(reorderBuffer.items, key = { _, media -> media.uri }) { index, media ->
        val pageIndex = pageIndices[media.uri] ?: index

        val padding by remember(pageIndex) {
          derivedStateOf {
            val currentPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val distanceFromCenter = abs(pageIndex - currentPosition).coerceIn(0f, 1f)
            lerp(MAX_PADDING, MIN_PADDING, distanceFromCenter)
          }
        }

        ReorderableItem(
          reorderableListState = reorderableListState,
          index = index,
          modifier = Modifier.clip(MediaSendMetrics.SelectedMediaPreviewShape)
        ) {
          DeleteBox(
            enabled = pagerState.currentPage == pageIndex,
            testTag = TestTags.thumbnailRowDeleteIcon(media.uri.toString())
          ) {
            Thumbnail(
              media = media,
              modifier = Modifier
                .padding(horizontal = padding)
                .clickable(enabled = enabled) { onThumbnailClick(pageIndex) }
            )
          }
        }
      }
    }
  }
}

private fun lerp(start: Dp, stop: Dp, fraction: Float): Dp {
  return start + (stop - start) * fraction
}

@Composable
private fun DeleteBox(
  enabled: Boolean,
  testTag: String? = null,
  content: @Composable () -> Unit
) {
  Box {
    content()

    if (enabled) {
      Icon(
        imageVector = SignalIcons.Trash.imageVector,
        tint = Color.White,
        contentDescription = null,
        modifier = Modifier
          .background(color = Color.Black.copy(alpha = 0.32f), shape = MediaSendMetrics.SelectedMediaPreviewShape)
          .size(MediaSendMetrics.SelectedMediaPreviewSize)
          .padding(10.dp)
          .align(Alignment.Center)
          .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
      )
    }
  }
}

@Composable
private fun Thumbnail(media: Media, modifier: Modifier = Modifier) {
  if (!LocalInspectionMode.current) {
    GlideImage(
      model = media.uri,
      imageSize = MediaSendMetrics.SelectedMediaPreviewSize,
      modifier = modifier
        .size(MediaSendMetrics.SelectedMediaPreviewSize)
        .clip(shape = MediaSendMetrics.SelectedMediaPreviewShape)
    )
  } else {
    Box(
      modifier = modifier
        .size(MediaSendMetrics.SelectedMediaPreviewSize)
        .background(color = Color.Gray, shape = MediaSendMetrics.SelectedMediaPreviewShape)
    )
  }
}

@DayNightPreviews
@Composable
private fun ThumbnailRowPreview() {
  val media = rememberPreviewMedia(10)
  val pagerState = rememberPagerState(pageCount = { media.size })

  Previews.Preview {
    ThumbnailRow(
      selectedMedia = media,
      pagerState = pagerState
    )
  }
}

@DayNightPreviews
@Composable
private fun ThumbnailPreview() {
  Previews.Preview {
    Thumbnail(
      media = rememberPreviewMedia(1).first()
    )
  }
}

@DayNightPreviews
@Composable
private fun DeleteBoxPreview() {
  Previews.Preview {
    DeleteBox(enabled = true) {
      Thumbnail(
        media = rememberPreviewMedia(1).first()
      )
    }
  }
}

@Composable
internal fun rememberPreviewMedia(count: Int, contentType: String = ContentTypeUtil.IMAGE_PNG): List<Media> {
  return remember(count) {
    (0 until count).map {
      Media(
        uri = "https://example.com/image$it.png".toUri(),
        contentType = contentType,
        width = 100,
        height = 100,
        duration = 0,
        date = 0,
        size = 0,
        isBorderless = false,
        isVideoGif = false,
        bucketId = null,
        caption = null,
        transformProperties = null,
        fileName = null
      )
    }
  }
}
