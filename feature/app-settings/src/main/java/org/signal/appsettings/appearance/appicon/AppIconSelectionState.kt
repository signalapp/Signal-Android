/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appearance.appicon

/**
 * [AppIconSelectionScreen] state.
 *
 * This is logged, so be sure `toString()` contains nothing sensitive.
 */
data class AppIconSelectionState(
  /** The icon the launcher is using now. */
  val currentIcon: AppIconPreset = AppIconPreset.DEFAULT,
  val dialog: Dialog = Dialog.None
) {

  /** The icon highlighted in the grid: the one awaiting confirmation if there is one, otherwise [currentIcon]. */
  val selectedIcon: AppIconPreset
    get() = (dialog as? Dialog.ConfirmIconChange)?.preset ?: currentIcon

  /** Whichever dialog the screen is showing, if any. */
  sealed interface Dialog {
    data object None : Dialog

    /** Asks the user to confirm switching to [preset], since Signal has to close to apply it. */
    data class ConfirmIconChange(val preset: AppIconPreset) : Dialog
  }
}
