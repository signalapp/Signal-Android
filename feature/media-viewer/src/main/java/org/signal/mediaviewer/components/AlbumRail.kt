/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.distinctUntilChanged
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType
import org.signal.glide.decryptableuri.DecryptableUri
import org.signal.mediaviewer.MediaViewerEvent
import org.signal.mediaviewer.MediaViewerItem
import org.signal.mediaviewer.MediaViewerPreviewFixtures
import org.signal.mediaviewer.MediaViewerScreenTestTags
import org.signal.mediaviewer.R

/**
 * Horizontal rail of thumbnails for the current album (or the whole gallery). Hidden when there
 * is only one item to show.
 */
@Composable
internal fun AlbumRail(
  items: List<MediaViewerItem?>,
  selectedItem: MediaViewerItem?,
  onEvent: (MediaViewerEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  if (items.size <= 1) {
    return
  }

  val listState = rememberLazyListState()
  val selectedIndex = items.indexOfFirst { it != null && it.attachmentId == selectedItem?.attachmentId }

  val currentOnEvent by rememberUpdatedState(onEvent)
  LaunchedEffect(listState) {
    snapshotFlow { listState.layoutInfo.visibleItemsInfo.let { it.firstOrNull()?.index to it.lastOrNull()?.index } }
      .distinctUntilChanged()
      .collect { (first, last) ->
        if (first != null && last != null) {
          currentOnEvent(MediaViewerEvent.RailVisibleRange(first, last))
        }
      }
  }

  // The half-viewport content padding means a scroll offset of zero puts an item's leading edge
  // exactly at the center. The first composition runs before the row has been measured, so wait
  // for a real viewport rather than scrolling against a zero-width one.
  val viewportWidth by remember {
    derivedStateOf { listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset }
  }

  LaunchedEffect(selectedIndex, viewportWidth) {
    if (selectedIndex >= 0 && viewportWidth > 0) {
      listState.animateScrollToItem(selectedIndex)
    }
  }

  BoxWithConstraints(modifier = modifier) {
    val sidePadding = ((this.maxWidth - ITEM_SIZE) / 2).coerceAtLeast(0.dp)

    LazyRow(
      state = listState,
      contentPadding = PaddingValues(horizontal = sidePadding),
      horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING),
      modifier = Modifier
        .fillMaxWidth()
        .testTag(MediaViewerScreenTestTags.ALBUM_RAIL)
    ) {
      itemsIndexed(
        items = items,
        key = { index, item -> item?.attachmentId?.id ?: -(index + 1L) }
      ) { _, item ->
        RailThumbnail(
          item = item,
          isSelected = item != null && item.attachmentId == selectedItem?.attachmentId,
          onClick = { item?.let { onEvent(MediaViewerEvent.RailItemClicked(it.attachmentId)) } }
        )
      }
    }
  }
}

@Composable
private fun RailThumbnail(
  item: MediaViewerItem?,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val shape = RoundedCornerShape(CORNER_RADIUS)
  val thumbnailContentDescription = stringResource(R.string.MediaViewerScreen__album_rail_thumbnail)
  val selection by animateFloatAsState(targetValue = if (isSelected) 1f else 0f)

  val inset = (BORDER_WIDTH + BORDER_GAP) * selection

  Box(
    modifier = modifier
      .size(ITEM_SIZE)
      .clip(shape)
      .border(width = BORDER_WIDTH, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = selection), shape = shape)
      .clickable(onClick = onClick)
      .semantics { selected = isSelected }
      .testTag(MediaViewerScreenTestTags.railItemTag(item))
  ) {
    if (item != null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(inset)
          .clip(RoundedCornerShape((CORNER_RADIUS - inset).coerceAtLeast(0.dp)))
      ) {
        GlideImage(
          model = remember(item.uri) { DecryptableUri(item.uri, thumbnailTimeUs = if (item.isVideo) RAIL_VIDEO_THUMBNAIL_TIME_US else 0L) },
          scaleType = GlideImageScaleType.CENTER_CROP,
          modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = thumbnailContentDescription }
        )

        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = lerp(UNSELECTED_SCRIM_ALPHA, SELECTED_SCRIM_ALPHA, selection)))
        )

        if (item.caption != null) {
          Icon(
            painter = painterResource(R.drawable.symbol_caption_24),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(start = 2.dp)
          )
        }
      }
    }
  }
}

private val ITEM_SIZE = 44.dp
private val ITEM_SPACING = 8.dp
private val CORNER_RADIUS = 6.dp
private val BORDER_WIDTH = 2.5.dp
private val BORDER_GAP = 2.dp
private const val SELECTED_SCRIM_ALPHA = 0.12f
private const val UNSELECTED_SCRIM_ALPHA = 0.24f
private const val RAIL_VIDEO_THUMBNAIL_TIME_US = 1000L

@DayNightPreviews
@Composable
private fun AlbumRailPreview() {
  val items = MediaViewerPreviewFixtures.previewItems(5)

  Previews.Preview {
    AlbumRail(
      items = items,
      selectedItem = items[2],
      onEvent = {}
    )
  }
}
