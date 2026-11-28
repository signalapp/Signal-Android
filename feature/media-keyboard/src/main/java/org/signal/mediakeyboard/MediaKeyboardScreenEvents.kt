/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard

/**
 * User interactions with a [MediaKeyboard], applied by [MediaKeyboardViewModel].
 */
sealed interface MediaKeyboardScreenEvents {
  data object Initialize : MediaKeyboardScreenEvents
  data class TabSelected(val tab: MediaKeyboardTab) : MediaKeyboardScreenEvents
  data class SearchQueryChanged(val query: String) : MediaKeyboardScreenEvents
}
