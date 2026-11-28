/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emojisheet

import android.graphics.drawable.Drawable
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.ExpandAtTop
import org.signal.core.ui.compose.LocalBottomSheetHostState
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.TextFields
import org.signal.core.ui.compose.collapsingTopBar
import org.signal.core.ui.compose.rememberCollapsingBarsState
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.compose.topBarPadding
import org.signal.mediakeyboard.R
import org.signal.mediakeyboard.screens.emoji.EmojiPageScreen
import org.signal.mediakeyboard.screens.emoji.EmojiPageState

@VisibleForTesting
object EmojiSheetTestTags {
  const val SEARCH_FIELD = "emoji-sheet-search-field"
  const val CUSTOMIZE_REACTIONS = "emoji-sheet-customize-reactions"
}

/**
 * An emoji picker that fills a bottom sheet beneath a pinned drag handle. The search field and category rail collapse
 * as the grid scrolls. Focusing search expands the sheet, and collapsing the sheet closes the keyboard.
 */
@Composable
fun EmojiSheetContent(
  state: EmojiSheetState,
  searchFieldState: TextFieldState,
  onEvent: (EmojiSheetEvents) -> Unit,
  modifier: Modifier = Modifier,
  getEmojiDrawable: (String) -> Drawable? = { null }
) {
  val barsState = rememberCollapsingBarsState()
  val gridState = rememberLazyGridState()
  val searchResultsGridState = rememberLazyGridState()

  val sheet = LocalBottomSheetHostState.current
  val focusManager = LocalFocusManager.current

  // Typing only fits at full height.
  LaunchedEffect(sheet.isExpanded) {
    if (!sheet.isExpanded) {
      focusManager.clearFocus()
    }
  }

  barsState.ExpandAtTop(
    scrollableState = if (state.page.searchResults != null) searchResultsGridState else gridState,
    key = state.page.searchQuery
  )

  Column(modifier = modifier.fillMaxSize()) {
    BottomSheets.Handle(modifier = Modifier.align(Alignment.CenterHorizontally))

    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .clipToBounds()
        .nestedScroll(barsState.nestedScrollConnection)
    ) {
      EmojiPageScreen(
        state = state.page,
        onEvent = { onEvent(EmojiSheetEvents.EmojiPageScreenEvent(it)) },
        getEmojiDrawable = getEmojiDrawable,
        barsState = barsState,
        contentPadding = barsState.topBarPadding,
        gridState = gridState,
        searchResultsGridState = searchResultsGridState,
        railLeadingContent = if (state.showCustomizeReactions) {
          { CustomizeReactionsButton(onClick = { onEvent(EmojiSheetEvents.CustomizeReactionsClicked) }) }
        } else {
          null
        }
      )

      Box(
        modifier = Modifier
          .collapsingTopBar(barsState)
          .fillMaxWidth()
          .background(SignalTheme.colors.colorSurface1)
      ) {
        SearchField(
          searchFieldState = searchFieldState,
          onFocused = sheet::expand
        )
      }
    }
  }
}

@Composable
private fun SearchField(
  searchFieldState: TextFieldState,
  onFocused: () -> Unit
) {
  TextFields.SearchField(
    state = searchFieldState,
    placeholder = stringResource(R.string.MediaKeyboard__search_emoji),
    leadingIcon = {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(48.dp)
      ) {
        Icon(
          imageVector = SignalIcons.Search.imageVector,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
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
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .fillMaxWidth()
      .onFocusChanged { if (it.isFocused) onFocused() }
      .testTag(EmojiSheetTestTags.SEARCH_FIELD)
  )
}

@Composable
private fun CustomizeReactionsButton(onClick: () -> Unit) {
  IconButton(
    onClick = onClick,
    modifier = Modifier.testTag(EmojiSheetTestTags.CUSTOMIZE_REACTIONS)
  ) {
    Icon(
      imageVector = SignalIcons.Settings.imageVector,
      contentDescription = stringResource(R.string.MediaKeyboard__customize_reactions),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier
        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        .padding(6.dp)
    )
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EmojiSheetContentPreview() {
  EmojiSheetContent(
    state = previewEmojiSheetState(),
    searchFieldState = rememberTextFieldState(),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EmojiSheetContentNoResultsPreview() {
  EmojiSheetContent(
    state = EmojiSheetState(page = EmojiPageState(searchResults = emptyList(), searchQuery = "hamburger")),
    searchFieldState = rememberTextFieldState("hamburger"),
    onEvent = {}
  )
}
