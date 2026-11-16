/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.about

import org.signal.core.util.StringUtil

data class EditAboutState(
  /** Already capped to the about limits by whoever fed it in. */
  val about: String = "",
  /** The emoji shown beside the about text, or empty for none. */
  val emoji: String = "",
  /** Whether the emoji picker is open. */
  val showEmojiPicker: Boolean = false,
  val saving: Boolean = false,
  val snackbar: Snackbar = Snackbar.None
) {

  companion object {
    const val ABOUT_MAX_GRAPHEMES = 140

    /** The count only shows once the about is getting close to [ABOUT_MAX_GRAPHEMES]. */
    const val ABOUT_COUNT_DISPLAY_THRESHOLD = 120
  }

  /** How long [about] is, counting an emoji or accented letter as one. */
  val aboutLength: Int
    get() = StringUtil.getGraphemeCount(about)

  override fun toString(): String = "EditAboutState(aboutLength=${about.length}, hasEmoji=${emoji.isNotEmpty()}, showEmojiPicker=$showEmojiPicker, saving=$saving, snackbar=$snackbar)"

  sealed interface Snackbar {
    data object None : Snackbar

    /** The about couldn't be uploaded, so nothing was saved. */
    data object NetworkFailure : Snackbar
  }
}
