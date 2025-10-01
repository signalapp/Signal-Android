/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.signal.core.ui.compose.theme.LocalSnackbarColors
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * Properly themed Snackbars. Since these use internal color state, we need to
 * use a local provider to pass the properly themed colors around. These composables
 * allow for quick and easy access to the proper theming for snackbars.
 */
object Snackbars {
  @Composable
  fun Host(snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = snackbarHostState, modifier = modifier) {
      Default(snackbarData = it)
    }
  }

  @Composable
  fun Default(snackbarData: SnackbarData) {
    val colors = LocalSnackbarColors.current
    Snackbar(
      snackbarData = snackbarData,
      containerColor = colors.color,
      contentColor = colors.contentColor,
      actionColor = colors.actionColor,
      actionContentColor = colors.actionContentColor,
      dismissActionContentColor = colors.dismissActionContentColor
    )
  }
}

@DayNightPreviews
@Composable
private fun SnackbarPreview() {
  SignalTheme {
    Snackbars.Default(snackbarData = SampleSnackbarData)
  }
}

private object SampleSnackbarData : SnackbarData {
  override val visuals = object : SnackbarVisuals {
    override val actionLabel: String = "Action Label"
    override val duration: SnackbarDuration = SnackbarDuration.Short
    override val message: String = "Message"
    override val withDismissAction: Boolean = true
  }

  override fun dismiss() = Unit

  override fun performAction() = Unit
}
