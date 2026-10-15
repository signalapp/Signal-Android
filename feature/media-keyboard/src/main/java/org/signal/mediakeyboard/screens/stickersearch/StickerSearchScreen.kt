/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.stickersearch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.ExpandAtTop
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.TextFields
import org.signal.core.ui.compose.collapsingTopBar
import org.signal.core.ui.compose.rememberCollapsingBarsState
import org.signal.core.ui.compose.topBarPadding
import org.signal.mediakeyboard.R
import org.signal.mediakeyboard.screens.sticker.StickerPageScreen
import org.signal.mediakeyboard.screens.sticker.StickerPageState
import org.signal.mediakeyboard.screens.sticker.rememberPreviewStickerPageState

/**
 * Results run edge to edge, scrolling behind the status bar, search bar and pack rail. Scrolling dismisses the keyboard
 * and collapses the search bar and rail.
 */
@Composable
internal fun StickerSearchScreen(
  state: StickerPageState,
  searchFieldState: TextFieldState,
  onEvent: (StickerSearchEvents) -> Unit
) {
  val focusRequester = remember { FocusRequester() }
  val barsState = rememberCollapsingBarsState()

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  val gridState = rememberLazyGridState()
  barsState.ExpandAtTop(gridState, key = state.searchQuery)

  Surface(modifier = Modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .nestedScroll(barsState.nestedScrollConnection)
    ) {
      StickerPageScreen(
        state = state,
        onEvent = { onEvent(StickerSearchEvents.StickerPageScreenEvent(it)) },
        contentPadding = barsState.topBarPadding,
        barsState = barsState,
        gridState = gridState,
        longPressMenu = true
      )

      Box(
        modifier = Modifier
          .collapsingTopBar(barsState)
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .windowInsetsPadding(WindowInsets.statusBars)
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
