/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.gif

import org.signal.mediakeyboard.data.KeyboardGif

data class GifPageState(
  val selectedQuickSearch: GifQuickSearchOption = GifQuickSearchOption.TRENDING,
  val gifs: List<KeyboardGif> = emptyList(),
  val isLoading: Boolean = false,
  val isLoadingMore: Boolean = false,
  val hasMore: Boolean = true,
  val loadFailed: Boolean = false
)
