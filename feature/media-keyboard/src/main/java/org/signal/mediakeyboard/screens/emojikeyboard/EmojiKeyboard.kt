/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emojikeyboard

import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.core.ui.compose.CollectActions
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.data.EmojiKeyboardRepository
import org.signal.mediakeyboard.screens.emoji.EmojiPageScreenEvents
import org.signal.mediakeyboard.screens.emoji.EmojiPageViewModel

/**
 * An emoji keyboard over [repository]'s emoji, for a host that switches between it and the system keyboard itself.
 *
 * @param isSearching Whether search has taken the keyboard's place. The host owns this, since only it knows when the
 *   keyboard goes away, and is asked to change it through [EmojiKeyboardAction.SearchOpened] and
 *   [EmojiKeyboardAction.SearchClosed].
 * @param isShown Whether the host is showing the keyboard. Recents are refreshed each time it becomes true.
 */
@Composable
fun EmojiKeyboard(
  repository: EmojiKeyboardRepository,
  isSearching: Boolean,
  isShown: Boolean,
  onAction: (EmojiKeyboardAction) -> Unit,
  modifier: Modifier = Modifier
) {
  val viewModel: EmojiPageViewModel = viewModel { EmojiPageViewModel(repository) }
  val pageState by viewModel.state.collectAsStateWithLifecycle()

  // The view model outlives the keyboard being hidden, so pick up whatever was used since it was last shown.
  LaunchedEffect(viewModel, isShown) {
    if (isShown) {
      viewModel.onEvent(EmojiPageScreenEvents.RefreshRecents)
    }
  }

  LaunchedEffect(isSearching) {
    if (!isSearching) {
      viewModel.searchFieldState.clearText()
    }
  }

  CollectActions(viewModel.actions) { action ->
    if (action is MediaKeyboardAction.EmojiSelected) {
      onAction(EmojiKeyboardAction.EmojiSelected(action.emoji))
    }
  }

  EmojiKeyboardContent(
    state = EmojiKeyboardState(page = pageState, isSearching = isSearching),
    searchFieldState = viewModel.searchFieldState,
    onEvent = { event ->
      when (event) {
        is EmojiKeyboardEvents.EmojiPageScreenEvent -> viewModel.onEvent(event.event)
        EmojiKeyboardEvents.BackspaceClicked -> onAction(EmojiKeyboardAction.Backspace)
        EmojiKeyboardEvents.SearchClicked -> onAction(EmojiKeyboardAction.SearchOpened)
        EmojiKeyboardEvents.SearchCloseClicked -> onAction(EmojiKeyboardAction.SearchClosed)
      }
    },
    getEmojiDrawable = repository::getEmojiDrawable,
    modifier = modifier
  )
}
