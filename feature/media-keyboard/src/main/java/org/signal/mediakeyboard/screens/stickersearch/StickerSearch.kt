/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.stickersearch

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.core.ui.compose.CollectActions
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.data.StickerKeyboardRepository
import org.signal.mediakeyboard.screens.sticker.StickerPageScreenEvents
import org.signal.mediakeyboard.screens.sticker.StickerPageViewModel

/**
 * A full-screen sticker search over [repository]'s stickers. Tapping a sticker sends it, so a pick always comes out
 * through [onAction] as [MediaKeyboardAction.StickerSendClicked], alongside anything else only the host can carry out, as
 * from the keyboard's sticker page. [onClose] is the user leaving without one.
 */
@Composable
fun StickerSearch(
  repository: StickerKeyboardRepository,
  onAction: (MediaKeyboardAction) -> Unit,
  onClose: () -> Unit
) {
  val viewModel: StickerPageViewModel = viewModel(factory = StickerPageViewModel.Factory(repository))
  val pageState by viewModel.state.collectAsStateWithLifecycle()
  val state = remember(pageState) { pageState.copy(showSearchField = false) }

  CollectActions(viewModel.actions, onAction)

  BackHandler(onBack = onClose)

  StickerSearchScreen(
    state = state,
    searchFieldState = viewModel.searchFieldState,
    onEvent = { event ->
      when (event) {
        StickerSearchEvents.NavigateBack -> onClose()
        is StickerSearchEvents.StickerPageScreenEvent -> viewModel.onEvent(event.event.sendingTappedSticker())
      }
    }
  )
}

private fun StickerPageScreenEvents.sendingTappedSticker(): StickerPageScreenEvents {
  return if (this is StickerPageScreenEvents.StickerClicked) StickerPageScreenEvents.StickerSendClicked(sticker) else this
}
