/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.gif

import org.signal.mediakeyboard.data.KeyboardGif

/**
 * @param searchQuery What the user typed into a host's search field. Takes the place of [selectedQuickSearch] while set.
 * @param showSearchField Whether the grid carries the keyboard's search field, which a host offering its own leaves out.
 * @param showQuickSearchRail Whether the quick search rail is pinned below the grid.
 */
data class GifPageState(
  val selectedQuickSearch: GifQuickSearchOption = GifQuickSearchOption.TRENDING,
  val searchQuery: String = "",
  val gifs: List<KeyboardGif> = emptyList(),
  val isLoading: Boolean = false,
  val isLoadingMore: Boolean = false,
  val hasMore: Boolean = true,
  val loadFailed: Boolean = false,
  val showSearchField: Boolean = true,
  val showQuickSearchRail: Boolean = true
) {
  val query: String
    get() = searchQuery.ifEmpty { selectedQuickSearch.query }
}
