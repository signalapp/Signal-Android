/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.snackbars

import androidx.compose.material3.SnackbarDuration
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

fun Fragment.makeSnackbar(state: SnackbarState) {
  if (view == null) {
    return
  }

  val snackbar = Snackbar.make(
    requireView(),
    state.message,
    when (state.duration) {
      SnackbarDuration.Short -> Snackbar.LENGTH_SHORT
      SnackbarDuration.Long -> Snackbar.LENGTH_LONG
      SnackbarDuration.Indefinite -> Snackbar.LENGTH_INDEFINITE
    }
  )

  state.actionState?.let { actionState ->
    snackbar.setAction(actionState.action) { actionState.onActionClick() }
    snackbar.setActionTextColor(requireContext().getColor(actionState.color))
  }

  snackbar.show()
}
