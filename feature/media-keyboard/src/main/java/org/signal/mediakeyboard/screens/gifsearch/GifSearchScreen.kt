/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.gifsearch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.TextFields
import org.signal.mediakeyboard.R
import org.signal.mediakeyboard.data.KeyboardGif
import org.signal.mediakeyboard.screens.gif.GifPageScreen
import org.signal.mediakeyboard.screens.gif.GifPageState

/**
 * Search field over the results, with the host's [footer] below them. Scrolling the results dismisses the keyboard.
 */
@Composable
internal fun GifSearchScreen(
  state: GifPageState,
  searchFieldState: TextFieldState,
  searchHint: String,
  footer: @Composable () -> Unit,
  onEvent: (GifSearchEvents) -> Unit
) {
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  val dismissKeyboardOnScroll = remember(keyboardController, focusManager) {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y != 0f) {
          keyboardController?.hide()
          focusManager.clearFocus()
        }
        return Offset.Zero
      }
    }
  }

  Surface(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .windowInsetsPadding(WindowInsets.statusBars)
      ) {
        TextFields.SearchField(
          state = searchFieldState,
          placeholder = searchHint,
          leadingIcon = {
            IconButton(onClick = { onEvent(GifSearchEvents.NavigateBack) }) {
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

      GifPageScreen(
        state = state,
        onEvent = { onEvent(GifSearchEvents.GifPageScreenEvent(it)) },
        modifier = Modifier
          .weight(1f)
          .nestedScroll(dismissKeyboardOnScroll)
      )

      footer()
    }
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
@DayNightPreviews
private fun GifSearchScreenPreview() {
  GifSearchScreen(
    state = GifPageState(
      gifs = List(8) { index ->
        KeyboardGif(id = "gif-$index", still = null, mp4PreviewUri = null, width = 200, height = 120 + (index % 3) * 60)
      },
      showSearchField = false,
      showQuickSearchRail = false
    ),
    searchFieldState = rememberTextFieldState(),
    searchHint = stringResource(R.string.MediaKeyboard__search_gifs),
    footer = {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(40.dp)
          .background(MaterialTheme.colorScheme.inverseSurface)
      )
    },
    onEvent = {}
  )
}
