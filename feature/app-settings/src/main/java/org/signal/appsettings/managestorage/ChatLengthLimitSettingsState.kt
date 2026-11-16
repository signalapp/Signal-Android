/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

/**
 * Everything [ChatLengthLimitSettingsScreen] needs to render.
 */
data class ChatLengthLimitSettingsState(
  val lengthLimit: Int = NO_LIMIT,
  val dialog: Dialog = Dialog.None
) {

  companion object {
    const val NO_LIMIT = 0
    val PRESETS = listOf(NO_LIMIT, 5000, 1000, 500, 100)
  }

  /** Whether the user entered a limit of their own rather than picking a preset. */
  val isCustom: Boolean
    get() = lengthLimit !in PRESETS

  /** Whichever dialog the screen is showing, if any. Only one is ever up at a time. */
  sealed interface Dialog {
    data object None : Dialog

    /** Confirms trimming every chat down to [limit] messages. */
    data class ConfirmLimit(val limit: Int) : Dialog

    /** Asks for a limit that isn't one of the presets, starting from [initialLimit] if there is one. */
    data class CustomLimit(val initialLimit: Int?) : Dialog
  }
}
