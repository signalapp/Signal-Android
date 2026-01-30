/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.snackbars

import androidx.annotation.ColorRes
import org.signal.core.ui.compose.Snackbars
import org.thoughtcrime.securesms.R

/**
 * Represents the state of a snackbar to be displayed.
 *
 * @property message The text message to display in the snackbar.
 * @property actionState Optional action button configuration.
 * @property duration How long the snackbar should be displayed.
 * @property hostKey The target host where this snackbar should be displayed. Defaults to [SnackbarHostKey.Global]
 * @property fallbackKey Optional host to fallback upon if the host key is not registered. Defaults to the Global key.
 */
data class SnackbarState(
  val message: String,
  val actionState: ActionState? = null,
  val duration: Snackbars.Duration = Snackbars.Duration.SHORT,
  val hostKey: SnackbarHostKey = SnackbarHostKey.Global,
  val fallbackKey: SnackbarHostKey? = SnackbarHostKey.Global
) {
  /**
   * Configuration for a snackbar action button.
   *
   * @property action The text label for the action button.
   * @property color The color resource for the action text.
   * @property onActionClick Callback invoked when the action is clicked.
   */
  data class ActionState(
    val action: String,
    @ColorRes val color: Int = R.color.core_white,
    val onActionClick: () -> Unit
  )
}
