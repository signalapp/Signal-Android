/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.stickersearch

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.TextFields
import org.signal.mediakeyboard.R
import org.signal.mediakeyboard.screens.sticker.StickerPageScreen
import org.signal.mediakeyboard.screens.sticker.StickerPageState
import org.signal.mediakeyboard.screens.sticker.rememberPreviewStickerPageState

private val SEARCH_BAR_HEIGHT = 64.dp

/**
 * The results run edge to edge, beneath the status bar, the search bar and the pack rail, which rides above the keyboard,
 * starting clear of them but scrolling behind them. Each of those slides over results that are already there rather than
 * resizing them.
 * Scrolling dismisses the keyboard and slides the search bar away, and it comes back as soon as the user scrolls up.
 */
@Composable
internal fun StickerSearchScreen(
  state: StickerPageState,
  searchFieldState: TextFieldState,
  onEvent: (StickerSearchEvents) -> Unit
) {
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val density = LocalDensity.current
  val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val searchBarHeightPx = with(density) { (statusBarHeight + SEARCH_BAR_HEIGHT).toPx() }
  var searchBarOffsetPx by remember { mutableFloatStateOf(0f) }

  val scrollConnection = remember(focusManager, searchBarHeightPx) {
    object : NestedScrollConnection {
      // Follows the scroll the results actually took, so a drag over results that fit on screen moves nothing.
      override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (consumed.y == 0f) {
          return Offset.Zero
        }

        if (source == NestedScrollSource.UserInput) {
          focusManager.clearFocus()
        }

        searchBarOffsetPx = (searchBarOffsetPx + consumed.y).coerceIn(-searchBarHeightPx, 0f)
        return Offset.Zero
      }

      override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val target = if (searchBarOffsetPx < -searchBarHeightPx / 2) -searchBarHeightPx else 0f
        animate(initialValue = searchBarOffsetPx, targetValue = target) { value, _ -> searchBarOffsetPx = value }
        return Velocity.Zero
      }
    }
  }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  // Results at the top always show the bar. Otherwise it could be left out of reach by results that stop scrolling, e.g.
  // a new search, or the room the keyboard took being handed back.
  val gridState = rememberLazyGridState()
  val resultsAtTop by remember { derivedStateOf { !gridState.canScrollBackward } }
  LaunchedEffect(resultsAtTop, state.searchQuery) {
    if (resultsAtTop) {
      animate(initialValue = searchBarOffsetPx, targetValue = 0f) { value, _ -> searchBarOffsetPx = value }
    }
  }

  Surface(modifier = Modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .nestedScroll(scrollConnection)
    ) {
      StickerPageScreen(
        state = state,
        onEvent = { onEvent(StickerSearchEvents.StickerPageScreenEvent(it)) },
        contentPadding = PaddingValues(top = statusBarHeight + SEARCH_BAR_HEIGHT),
        fullBleed = true,
        gridState = gridState,
        longPressMenu = true
      )

      Box(
        modifier = Modifier
          .graphicsLayer { translationY = searchBarOffsetPx }
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .windowInsetsPadding(WindowInsets.statusBars)
          .height(SEARCH_BAR_HEIGHT)
      ) {
        TextFields.SearchField(
          state = searchFieldState,
          placeholder = stringResource(R.string.MediaKeyboard__search_stickers),
          leadingIcon = {
            IconButton(onClick = { onEvent(StickerSearchEvents.NavigateBack) }) {
              Icon(
                imageVector = SignalIcons.ArrowStart.imageVector,
                contentDescription = stringResource(R.string.MediaKeyboard__close_search)
              )
            }
          },
          trailingIcon = if (searchFieldState.text.isNotEmpty()) {
            {
              IconButton(onClick = { searchFieldState.clearText() }) {
                Icon(
                  imageVector = SignalIcons.X.imageVector,
                  contentDescription = stringResource(R.string.MediaKeyboard__clear_search)
                )
              }
            }
          } else {
            null
          },
          modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .focusRequester(focusRequester)
        )
      }
    }
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
@DayNightPreviews
private fun StickerSearchScreenPreview() {
  StickerSearchScreen(
    state = rememberPreviewStickerPageState(),
    searchFieldState = rememberTextFieldState(),
    onEvent = {}
  )
}
