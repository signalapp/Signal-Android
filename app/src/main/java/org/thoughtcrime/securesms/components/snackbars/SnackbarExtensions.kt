/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.snackbars

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Snackbars

fun Fragment.makeSnackbar(state: SnackbarState) {
  if (view == null) {
    return
  }

  val snackbar = Snackbar.make(
    requireView(),
    state.message,
    when (state.duration) {
      Snackbars.Duration.SHORT -> Snackbar.LENGTH_SHORT
      Snackbars.Duration.LONG -> Snackbar.LENGTH_LONG
      Snackbars.Duration.INDEFINITE -> Snackbar.LENGTH_INDEFINITE
      else -> Snackbar.LENGTH_INDEFINITE
    }
  )

  state.actionState?.let { actionState ->
    snackbar.setAction(actionState.action) { actionState.onActionClick() }
    snackbar.setActionTextColor(requireContext().getColor(actionState.color))
  }

  snackbar.show()

  if (state.duration is Snackbars.Duration.Custom) {
    viewLifecycleOwner.lifecycleScope.launch {
      delay(state.duration.duration)
      snackbar.dismiss()
    }
  }
}
