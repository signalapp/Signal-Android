/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.gif

import org.signal.mediakeyboard.data.KeyboardGif

sealed interface GifPageScreenEvents {
  data object Initialize : GifPageScreenEvents
  data class QuickSearchSelected(val option: GifQuickSearchOption) : GifPageScreenEvents
  data object LoadMoreRequested : GifPageScreenEvents
  data object RetryClicked : GifPageScreenEvents
  data class GifClicked(val gif: KeyboardGif) : GifPageScreenEvents
  data object SearchClicked : GifPageScreenEvents
}
