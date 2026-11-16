/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.gifsearch

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.core.ui.compose.CollectActions
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.data.GifKeyboardRepository
import org.signal.mediakeyboard.screens.gif.GifPageViewModel

/**
 * A full-screen gif search over [repository]'s gifs. A pick comes out through [onAction] as
 * [MediaKeyboardAction.GifSelected], as from the keyboard's gif page. [onClose] is the user leaving without one.
 *
 * @param searchHint Placeholder for the search field, so a host can name where its gifs come from.
 * @param footer Drawn below the results, for any attribution the host's gif source asks for. It sits on the bottom
 *   edge of the window, so it is responsible for the navigation bar and keyboard insets.
 */
@Composable
fun GifSearch(
  repository: GifKeyboardRepository,
  searchHint: String,
  onAction: (MediaKeyboardAction) -> Unit,
  onClose: () -> Unit,
  footer: @Composable () -> Unit
) {
  val viewModel: GifPageViewModel = viewModel(factory = GifPageViewModel.Factory(repository))
  val pageState by viewModel.state.collectAsStateWithLifecycle()
  val state = remember(pageState) { pageState.copy(showSearchField = false, showQuickSearchRail = false) }

  CollectActions(viewModel.actions, onAction)

  BackHandler(onBack = onClose)

  GifSearchScreen(
    state = state,
    searchFieldState = viewModel.searchFieldState,
    searchHint = searchHint,
    footer = footer,
    onEvent = { event ->
      when (event) {
        GifSearchEvents.NavigateBack -> onClose()
        is GifSearchEvents.GifPageScreenEvent -> viewModel.onEvent(event.event)
      }
    }
  )
}
