/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.gif

import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType
import org.signal.mediakeyboard.R
import org.signal.mediakeyboard.data.KeyboardGif
import org.signal.mediakeyboard.screens.GRID_CONTENT_PADDING
import org.signal.mediakeyboard.screens.MediaKeyboardSearchField
import org.signal.mediakeyboard.screens.PinnedRailLayout
import org.signal.mediakeyboard.screens.SearchFieldReveal

/** The search field ahead of the gifs, which item indices have to be shifted back past. */
private const val SEARCH_FIELD_ITEMS = 1

/**
 * @param onSearchFieldRevealedChange Reports whether the grid is scrolled far enough up to show the
 *   search field, so the top bar can drop its now-redundant search icon.
 */
@Composable
fun GifPageScreen(
  state: GifPageState,
  onEvent: (GifPageScreenEvents) -> Unit,
  modifier: Modifier = Modifier,
  onSearchFieldRevealedChange: (Boolean) -> Unit = {}
) {
  // Only the grid carries the field, so nothing is revealed while loading, retrying or empty.
  val showingGrid = !state.isLoading && !state.loadFailed && state.gifs.isNotEmpty()

  LaunchedEffect(showingGrid) {
    if (!showingGrid) {
      onSearchFieldRevealedChange(false)
    }
  }

  PinnedRailLayout(
    rail = { GifQuickSearchRail(state = state, onEvent = onEvent) },
    modifier = modifier
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      when {
        state.isLoading -> {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        state.loadFailed -> {
          GifLoadError(onEvent = onEvent)
        }

        state.gifs.isEmpty() -> {
          Text(
            text = stringResource(R.string.MediaKeyboard__no_results_found),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center)
          )
        }

        else -> {
          GifGrid(
            state = state,
            onEvent = onEvent,
            onSearchFieldRevealedChange = onSearchFieldRevealedChange
          )
        }
      }
    }
  }
}

@Composable
private fun GifQuickSearchRail(
  state: GifPageState,
  onEvent: (GifPageScreenEvents) -> Unit
) {
  Surface(color = SignalTheme.colors.colorSurface5) {
    LazyRow(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
      items(GifQuickSearchOption.entries, key = { it.name }) { option ->
        FilterChip(
          selected = option == state.selectedQuickSearch,
          onClick = { onEvent(GifPageScreenEvents.QuickSearchSelected(option)) },
          label = { Text(text = stringResource(option.label)) },
          modifier = Modifier.padding(horizontal = 4.dp)
        )
      }
    }
  }
}

@Composable
private fun GifLoadError(onEvent: (GifPageScreenEvents) -> Unit) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp)
  ) {
    Text(
      text = stringResource(R.string.MediaKeyboard__couldnt_load_gifs),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 24.dp)
    )

    TextButton(onClick = { onEvent(GifPageScreenEvents.RetryClicked) }) {
      Text(text = stringResource(R.string.MediaKeyboard__retry))
    }
  }
}

@Composable
private fun GifGrid(
  state: GifPageState,
  onEvent: (GifPageScreenEvents) -> Unit,
  onSearchFieldRevealedChange: (Boolean) -> Unit
) {
  val gridState = rememberLazyStaggeredGridState()
  val playerPool = rememberGifPlayerPool()

  // Keyed on the quick search, since switching one empties the grid and takes the scroll back to
  // the top with it.
  SearchFieldReveal(
    hasContent = state.gifs.isNotEmpty(),
    firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
    scrollPastField = gridState::scrollToItem,
    onRevealedChange = onSearchFieldRevealedChange,
    resetKey = state.selectedQuickSearch
  )

  val shouldLoadMore by remember(state.gifs.size) {
    derivedStateOf {
      val lastVisibleGif = (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) - SEARCH_FIELD_ITEMS
      lastVisibleGif >= state.gifs.size - 8
    }
  }

  LaunchedEffect(shouldLoadMore, state.gifs.size) {
    if (shouldLoadMore) {
      onEvent(GifPageScreenEvents.LoadMoreRequested)
    }
  }

  LazyVerticalStaggeredGrid(
    columns = StaggeredGridCells.Fixed(2),
    state = gridState,
    verticalItemSpacing = 4.dp,
    contentPadding = GRID_CONTENT_PADDING,
    modifier = Modifier.fillMaxSize()
  ) {
    item(key = "search", span = StaggeredGridItemSpan.FullLine) {
      MediaKeyboardSearchField(
        hint = stringResource(R.string.MediaKeyboard__search_gifs),
        onClick = { onEvent(GifPageScreenEvents.SearchClicked) }
      )
    }

    items(
      count = state.gifs.size,
      key = { index -> "${state.gifs[index].id}:$index" }
    ) { index ->
      val gif = state.gifs[index]
      GifCell(
        gif = gif,
        playerPool = playerPool,
        onClick = { onEvent(GifPageScreenEvents.GifClicked(gif)) },
        modifier = Modifier.padding(horizontal = 4.dp)
      )
    }

    if (state.isLoadingMore) {
      item(key = "loading-more", span = StaggeredGridItemSpan.FullLine) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          CircularProgressIndicator()
        }
      }
    }
  }
}

@OptIn(UnstableApi::class)
@Composable
private fun GifCell(
  gif: KeyboardGif,
  playerPool: GifPlayerPool,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(gif.aspectRatio)
      .clip(MaterialTheme.shapes.small)
      .clickable(onClick = onClick)
  ) {
    GlideImage(
      model = gif.still,
      scaleType = GlideImageScaleType.CENTER_CROP,
      modifier = Modifier.fillMaxSize()
    )

    val mp4PreviewUri = gif.mp4PreviewUri
    if (mp4PreviewUri != null) {
      var player by remember(gif.id) { mutableStateOf<Player?>(null) }

      DisposableEffect(gif.id) {
        player = playerPool.acquire(gif.id, mp4PreviewUri)
        onDispose {
          playerPool.release(gif.id)
          player = null
        }
      }

      player?.let {
        val presentationState = rememberPresentationState(it)

        PlayerSurface(
          player = it,
          surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
          modifier = Modifier.resizeWithContentScale(ContentScale.Crop, presentationState.videoSizeDp)
        )
      }
    }
  }
}
