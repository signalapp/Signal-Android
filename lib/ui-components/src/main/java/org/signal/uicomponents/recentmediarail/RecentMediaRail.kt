/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.recentmediarail

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType
import org.signal.glide.decryptableuri.DecryptableUri
import org.signal.core.ui.R as CoreUiR

private val ITEM_SIZE = 80.dp
private val ITEM_SPACING = 8.dp
private val ITEM_CORNERS = RoundedCornerShape(12.dp)
private val ITEM_SCRIM = Color(0x14000000)

/**
 * A horizontally scrolling strip of media thumbnails, most recent first.
 *
 * Driven entirely by a [RecentMediaRailPresenter], which owns the state handed in here and decides what the events sent
 * back out of here actually do.
 */
@Composable
fun RecentMediaRail(
  state: RecentMediaRailState,
  onEvent: (RecentMediaRailEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val leftToRight = LocalLayoutDirection.current == LayoutDirection.Ltr

  LazyRow(
    horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING),
    contentPadding = PaddingValues(horizontal = dimensionResource(CoreUiR.dimen.gutter)),
    modifier = modifier
      .fillMaxWidth()
      .height(ITEM_SIZE)
  ) {
    itemsIndexed(state.media) { index, media ->
      RecentMediaRailItem(
        media = media,
        onClick = { bounds -> onEvent(RecentMediaRailEvents.ItemClicked(index, bounds, leftToRight)) }
      )
    }
  }
}

@Composable
private fun RecentMediaRailItem(
  media: RecentMedia,
  onClick: (IntRect) -> Unit
) {
  var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

  Box(
    modifier = Modifier
      .size(ITEM_SIZE)
      .clip(ITEM_CORNERS)
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .onGloballyPositioned { coordinates = it }
      .clickable { onClick(coordinates?.boundsInWindow()?.roundToIntRect() ?: IntRect.Zero) }
  ) {
    GlideImage(
      model = remember(media) { media.thumbnailUri?.let { DecryptableUri(it, media.thumbnailTimeUs) } },
      imageSize = DpSize(ITEM_SIZE, ITEM_SIZE),
      scaleType = GlideImageScaleType.CENTER_CROP,
      modifier = Modifier.fillMaxSize()
    )

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(ITEM_SCRIM)
    )
  }
}

@DayNightPreviews
@Composable
private fun RecentMediaRailPreview() {
  Previews.Preview {
    RecentMediaRail(
      state = RecentMediaRailState(
        media = List(5) { RecentMedia(thumbnailUri = Uri.EMPTY, availability = RecentMedia.Availability.AVAILABLE) },
        loaded = true
      ),
      onEvent = {}
    )
  }
}
