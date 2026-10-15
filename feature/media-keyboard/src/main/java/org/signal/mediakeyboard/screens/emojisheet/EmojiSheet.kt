/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emojisheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.core.ui.compose.CollectActions
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.data.EmojiKeyboardRepository
import org.signal.mediakeyboard.screens.emoji.EmojiPageViewModel

/**
 * An emoji picker for a bottom sheet. Picks and other host work come out through [onAction].
 *
 * @param showCustomizeReactions Whether to offer a way to change the user's quick reactions.
 */
@Composable
fun EmojiSheet(
  repository: EmojiKeyboardRepository,
  onAction: (EmojiSheetAction) -> Unit,
  modifier: Modifier = Modifier,
  showCustomizeReactions: Boolean = false
) {
  val viewModel: EmojiPageViewModel = viewModel { EmojiPageViewModel(repository) }
  val pageState by viewModel.state.collectAsStateWithLifecycle()

  CollectActions(viewModel.actions) { action ->
    if (action is MediaKeyboardAction.EmojiSelected) {
      onAction(EmojiSheetAction.EmojiSelected(action.emoji))
    }
  }

  EmojiSheetContent(
    state = EmojiSheetState(page = pageState, showCustomizeReactions = showCustomizeReactions),
    searchFieldState = viewModel.searchFieldState,
    onEvent = { event ->
      when (event) {
        is EmojiSheetEvents.EmojiPageScreenEvent -> viewModel.onEvent(event.event)
        EmojiSheetEvents.CustomizeReactionsClicked -> onAction(EmojiSheetAction.CustomizeReactionsClicked)
      }
    },
    getEmojiDrawable = repository::getEmojiDrawable,
    modifier = modifier
  )
}
