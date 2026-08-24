/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.shared

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.registration.R
import org.signal.registration.RegistrationDependencies

/**
 * Three-option contact support dialog. Mirrors the app-module ContactSupportDialog.
 */
@Composable
fun ContactSupportDialog(
  state: ContactSupportState,
  onEvent: (ContactSupportEvents) -> Unit
) {
  if (state.showAsProgress) {
    Dialogs.IndeterminateProgressDialog()
  } else {
    Dialogs.AdvancedAlertDialog(
      title = stringResource(R.string.ContactSupportDialog__submit_debug_log),
      body = stringResource(R.string.ContactSupportDialog__your_debug_logs),
      positive = stringResource(R.string.ContactSupportDialog__submit_with_debug_log),
      onPositive = { onEvent(ContactSupportEvents.SubmitWithDebugLog) },
      neutral = stringResource(R.string.ContactSupportDialog__submit_without_debug_log),
      onNeutral = { onEvent(ContactSupportEvents.SubmitWithoutDebugLog) },
      negative = stringResource(android.R.string.cancel),
      onNegative = { onEvent(ContactSupportEvents.Cancel) }
    )
  }
}

@Composable
fun ContactSupportDialog(
  @StringRes subject: Int,
  @StringRes filter: Int,
  onDismiss: () -> Unit
) {
  val controller = RegistrationDependencies.get().contactSupportController ?: throw AssertionError("Missing support controller")
  val viewModel: ContactSupportViewModel = viewModel(factory = ContactSupportViewModel.Factory(controller))
  val state by viewModel.state.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val subjectString = stringResource(subject)
  val filterString = stringResource(filter)

  LaunchedEffect(state.sendEmail) {
    if (state.sendEmail) {
      controller.sendSupportEmail(context, subjectString, filterString, state.debugLogUrl)
      viewModel.onEvent(ContactSupportEvents.Cancel)
      onDismiss()
    }
  }

  ContactSupportDialog(
    state = state,
    onEvent = { event ->
      if (event is ContactSupportEvents.Cancel) {
        viewModel.onEvent(ContactSupportEvents.Cancel)
        onDismiss()
      } else {
        viewModel.onEvent(event)
      }
    }
  )
}

@DayNightPreviews
@Composable
private fun ContactSupportDialogPreview() {
  Previews.Preview {
    ContactSupportDialog(
      state = ContactSupportState(),
      onEvent = {}
    )
  }
}
