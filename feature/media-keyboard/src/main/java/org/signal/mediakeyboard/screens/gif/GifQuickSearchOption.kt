/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.gif

import androidx.annotation.StringRes
import org.signal.mediakeyboard.R

enum class GifQuickSearchOption(val query: String, @field:StringRes val label: Int) {
  TRENDING("", R.string.MediaKeyboard__trending),
  CELEBRATE("celebrate", R.string.MediaKeyboard__celebrate),
  LOVE("love", R.string.MediaKeyboard__love),
  THUMBS_UP("thumbs up", R.string.MediaKeyboard__thumbs_up),
  SURPRISED("surprised", R.string.MediaKeyboard__surprised),
  EXCITED("excited", R.string.MediaKeyboard__excited),
  SAD("sad", R.string.MediaKeyboard__sad),
  ANGRY("angry", R.string.MediaKeyboard__angry)
}
