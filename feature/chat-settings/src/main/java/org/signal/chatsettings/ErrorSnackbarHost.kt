/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.chatsettings

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.showSnackbar

/**
 * Reports a rejected change, and tells the view model once it's been seen so the next rejection can show.
 */
@Composable
internal fun ErrorSnackbarHost(
  @StringRes errorMessage: Int?,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val hostState = remember { SnackbarHostState() }
  val message = errorMessage?.let { stringResource(it) }

  LaunchedEffect(errorMessage) {
    if (message != null) {
      hostState.showSnackbar(message = message, duration = Snackbars.Duration.LONG)
      onDismiss()
    }
  }

  Snackbars.Host(hostState, modifier = modifier)
}
