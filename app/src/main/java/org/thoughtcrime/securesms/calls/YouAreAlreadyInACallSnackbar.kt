/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls

import android.view.View
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.google.android.material.snackbar.Snackbar
import org.signal.core.ui.Snackbars
import org.thoughtcrime.securesms.R

/**
 * Snackbar which can be displayed whenever the user tries to join a call but is already in another.
 */
object YouAreAlreadyInACallSnackbar {
  /**
   * Composable component
   */
  @Composable
  fun YouAreAlreadyInACallSnackbar(
    displaySnackbar: Boolean,
    modifier: Modifier = Modifier
  ) {
    val message = stringResource(R.string.CommunicationActions__you_are_already_in_a_call)
    val hostState = remember { SnackbarHostState() }
    Snackbars.Host(hostState, modifier = modifier)

    LaunchedEffect(displaySnackbar) {
      if (displaySnackbar) {
        hostState.showSnackbar(message)
      }
    }
  }

  /**
   * View system component
   */
  @JvmStatic
  fun show(view: View) {
    Snackbar.make(
      view,
      view.context.getString(R.string.CommunicationActions__you_are_already_in_a_call),
      Snackbar.LENGTH_LONG
    ).show()
  }
}
