/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.signal.core.models.media.Media
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.ContentTypeUtil
import org.signal.glide.compose.GlideImage
import org.signal.mediasend.MediaSendMetrics
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private val BASE_SPACING = 4.dp
private val MIN_PADDING = 0.dp
private val MAX_PADDING = 8.dp

/**
 * Horizontally scrollable thumbnail strip that syncs with [pagerState].
 * Features fish-eye padding effect where the centered item has more padding.
 */
@Composable
internal fun ThumbnailRow(
  selectedMedia: List<Media>,
  pagerState: PagerState,
  onFocusedMediaChange: (Media) -> Unit,
  onThumbnailClick: (Int) -> Unit = {}
) {
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()

  val itemWidthPx = with(density) { MediaSendMetrics.SelectedMediaPreviewSize.width.toPx() }
  val baseSpacingPx = with(density) { BASE_SPACING.toPx() }
  val itemStride = itemWidthPx + baseSpacingPx
  val pagerPageSize = pagerState.layoutInfo.pageSize.takeIf { it > 0 } ?: 1
  val listState = rememberLazyListState()

  val draggableState = rememberDraggableState { delta ->
    val scaledDelta = delta * (pagerPageSize.toFloat() / itemStride)
    pagerState.dispatchRawDelta(-scaledDelta)
  }

  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.isScrollInProgress }
      .filter { !it }
      .drop(1)
      .collectLatest {
        val settledPage = pagerState.currentPage
        if (settledPage in selectedMedia.indices) {
          onFocusedMediaChange(selectedMedia[settledPage])
        }
      }
  }

  LaunchedEffect(pagerState, itemStride, selectedMedia.size) {
    if (selectedMedia.isEmpty()) return@LaunchedEffect

    snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }
      .distinctUntilChanged()
      .collectLatest { position ->
        val clampedPosition = position.coerceIn(0f, selectedMedia.lastIndex.toFloat())
        val baseIndex = floor(clampedPosition.toDouble()).toInt()
        val fraction = (clampedPosition - baseIndex).coerceIn(0f, 1f)
        val scrollOffsetPx = (fraction * itemStride).roundToInt()

        listState.scrollToItem(baseIndex, scrollOffsetPx)
      }
  }

  BoxWithConstraints(
    modifier = Modifier.fillMaxWidth().draggable(
      state = draggableState,
      orientation = Orientation.Horizontal,
      onDragStopped = { velocity ->
        scope.launch {
          val currentOffset = pagerState.currentPageOffsetFraction
          val targetPage = when {
            velocity > 500f -> (pagerState.currentPage - 1).coerceAtLeast(0)
            velocity < -500f -> (pagerState.currentPage + 1).coerceAtMost(selectedMedia.lastIndex)
            currentOffset > 0.5f -> (pagerState.currentPage + 1).coerceAtMost(selectedMedia.lastIndex)
            currentOffset < -0.5f -> (pagerState.currentPage - 1).coerceAtLeast(0)
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
      userScrollEnabled = false
    ) {
      itemsIndexed(selectedMedia, key = { _, media -> media.uri }) { index, media ->
        val padding by remember(index) {
          derivedStateOf {
            val currentPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val distanceFromCenter = abs(index - currentPosition).coerceIn(0f, 1f)
            lerp(MAX_PADDING, MIN_PADDING, distanceFromCenter)
          }
        }

        Thumbnail(
          media = media,
          modifier = Modifier
            .padding(horizontal = padding)
            .clickable { onThumbnailClick(index) }
        )
      }
    }
  }
}

private fun lerp(start: Dp, stop: Dp, fraction: Float): Dp {
  return start + (stop - start) * fraction
}

@Composable
private fun Thumbnail(media: Media, modifier: Modifier = Modifier) {
  if (!LocalInspectionMode.current) {
    GlideImage(
      model = media.uri,
      imageSize = MediaSendMetrics.SelectedMediaPreviewSize,
      modifier = modifier
        .size(MediaSendMetrics.SelectedMediaPreviewSize)
        .clip(shape = RoundedCornerShape(8.dp))
    )
  } else {
    Box(
      modifier = modifier
        .size(MediaSendMetrics.SelectedMediaPreviewSize)
        .background(color = Color.Gray, shape = RoundedCornerShape(8.dp))
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
      pagerState = pagerState,
      onFocusedMediaChange = { }
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

@Composable
internal fun rememberPreviewMedia(count: Int): List<Media> {
  return remember(count) {
    (0 until count).map {
      Media(
        uri = "https://example.com/image$it.png".toUri(),
        contentType = ContentTypeUtil.IMAGE_PNG,
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
