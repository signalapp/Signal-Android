/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emojikeyboard

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.TextFields
import org.signal.mediakeyboard.R
import org.signal.mediakeyboard.data.KeyboardEmoji
import org.signal.mediakeyboard.screens.emoji.EmojiPageScreen
import org.signal.mediakeyboard.screens.emoji.EmojiSearchResultsRow
import org.signal.mediakeyboard.screens.emoji.previewEmojiPageState

@VisibleForTesting
object EmojiKeyboardTestTags {
  const val SEARCH = "emoji-keyboard-search"
  const val BACKSPACE = "emoji-keyboard-backspace"
  const val SEARCH_FIELD = "emoji-keyboard-search-field"
  const val CLOSE_SEARCH = "emoji-keyboard-close-search"
}

/**
 * An emoji keyboard that stands in for the system keyboard. While [EmojiKeyboardState.isSearching], it shrinks to a
 * search field and a row of results so the system keyboard fits beneath it.
 */
@Composable
fun EmojiKeyboardContent(
  state: EmojiKeyboardState,
  searchFieldState: TextFieldState,
  onEvent: (EmojiKeyboardEvents) -> Unit,
  modifier: Modifier = Modifier,
  getEmojiDrawable: (String) -> Drawable? = { null }
) {
  if (state.isSearching) {
    EmojiSearch(
      state = state,
      searchFieldState = searchFieldState,
      onEvent = onEvent,
      getEmojiDrawable = getEmojiDrawable,
      modifier = modifier
    )
  } else {
    EmojiGrid(
      state = state,
      onEvent = onEvent,
      getEmojiDrawable = getEmojiDrawable,
      modifier = modifier
    )
  }
}

@Composable
private fun EmojiGrid(
  state: EmojiKeyboardState,
  onEvent: (EmojiKeyboardEvents) -> Unit,
  getEmojiDrawable: (String) -> Drawable?,
  modifier: Modifier = Modifier
) {
  // Two search affordances at once is one too many, so the icon goes while the grid's own field shows.
  var searchFieldRevealed by remember { mutableStateOf(false) }

  Column(modifier = modifier.fillMaxSize()) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp)
    ) {
      val searchAlpha by animateFloatAsState(
        targetValue = if (searchFieldRevealed) 0f else 1f,
        label = "searchAlpha"
      )

      IconButton(
        onClick = { onEvent(EmojiKeyboardEvents.SearchClicked) },
        enabled = searchAlpha > 0f,
        modifier = Modifier
          .graphicsLayer { alpha = searchAlpha }
          .testTag(EmojiKeyboardTestTags.SEARCH)
      ) {
        Icon(
          imageVector = SignalIcons.Search.imageVector,
          contentDescription = stringResource(R.string.MediaKeyboard__search),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.weight(1f))

      IconButton(
        onClick = { onEvent(EmojiKeyboardEvents.BackspaceClicked) },
        modifier = Modifier.testTag(EmojiKeyboardTestTags.BACKSPACE)
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.Backspace,
          contentDescription = stringResource(R.string.MediaKeyboard__backspace),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    EmojiPageScreen(
      state = state.page,
      onEvent = { onEvent(EmojiKeyboardEvents.EmojiPageScreenEvent(it)) },
      getEmojiDrawable = getEmojiDrawable,
      onSearchClicked = { onEvent(EmojiKeyboardEvents.SearchClicked) },
      onSearchFieldRevealedChange = { searchFieldRevealed = it },
      modifier = Modifier.weight(1f)
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmojiSearch(
  state: EmojiKeyboardState,
  searchFieldState: TextFieldState,
  onEvent: (EmojiKeyboardEvents) -> Unit,
  getEmojiDrawable: (String) -> Drawable?,
  modifier: Modifier = Modifier
) {
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  // Dismissing the system keyboard, as back does, leaves search too.
  val isImeVisible = WindowInsets.isImeVisible
  var imeWasVisible by remember { mutableStateOf(false) }
  LaunchedEffect(isImeVisible) {
    if (isImeVisible) {
      imeWasVisible = true
    } else if (imeWasVisible) {
      onEvent(EmojiKeyboardEvents.SearchCloseClicked)
    }
  }

  BackHandler {
    onEvent(EmojiKeyboardEvents.SearchCloseClicked)
  }

  Column(modifier = modifier.fillMaxWidth()) {
    TextFields.SearchField(
      state = searchFieldState,
      placeholder = stringResource(R.string.MediaKeyboard__search_emoji),
      leadingIcon = {
        IconButton(
          onClick = { onEvent(EmojiKeyboardEvents.SearchCloseClicked) },
          modifier = Modifier.testTag(EmojiKeyboardTestTags.CLOSE_SEARCH)
        ) {
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
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .fillMaxWidth()
        .focusRequester(focusRequester)
        .testTag(EmojiKeyboardTestTags.SEARCH_FIELD)
    )

    EmojiSearchResultsRow(
      state = state.page,
      onEvent = { onEvent(EmojiKeyboardEvents.EmojiPageScreenEvent(it)) },
      getEmojiDrawable = getEmojiDrawable
    )
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EmojiKeyboardContentPreview() {
  EmojiKeyboardContent(
    state = EmojiKeyboardState(page = previewEmojiPageState()),
    searchFieldState = rememberTextFieldState(),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EmojiKeyboardContentSearchingPreview() {
  val page = previewEmojiPageState()
  EmojiKeyboardContent(
    state = EmojiKeyboardState(
      page = page.copy(searchResults = listOf("😀", "😃", "😄", "😁").map { KeyboardEmoji(it) }, searchQuery = "smile"),
      isSearching = true
    ),
    searchFieldState = rememberTextFieldState("smile"),
    onEvent = {}
  )
}
