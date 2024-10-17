/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import android.content.res.Configuration
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.signal.core.ui.theme.LocalSnackbarColors
import org.signal.core.ui.theme.SignalTheme

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
  internal fun Default(snackbarData: SnackbarData) {
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

@Preview
@Composable
private fun SnackbarLightPreview() {
  SignalTheme {
    Snackbars.Default(snackbarData = SampleSnackbarData)
  }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SnackbarDarkPreview() {
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
