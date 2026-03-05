/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import org.signal.core.ui.compose.Dialogs
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult

/**
 * Shared error dialogs for registration failures during backup key entry.
 * Used by both remote and local backup restore flows.
 */
@Composable
fun RegistrationErrorDialogs(
  showRegistrationError: Boolean,
  registerAccountResult: RegisterAccountResult?,
  onRegistrationErrorDismiss: () -> Unit,
  onBackupKeyHelp: () -> Unit
) {
  if (!showRegistrationError) return

  if (registerAccountResult is RegisterAccountResult.IncorrectRecoveryPassword) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.EnterBackupKey_incorrect_backup_key_title),
      body = stringResource(R.string.EnterBackupKey_incorrect_backup_key_message),
      confirm = stringResource(R.string.EnterBackupKey_try_again),
      dismiss = stringResource(R.string.EnterBackupKey_backup_key_help),
      onConfirm = {},
      onDeny = onBackupKeyHelp,
      onDismiss = onRegistrationErrorDismiss,
      properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
  } else {
    val message = when (registerAccountResult) {
      is RegisterAccountResult.RateLimited -> stringResource(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
      else -> stringResource(R.string.RegistrationActivity_error_connecting_to_service)
    }

    Dialogs.SimpleMessageDialog(
      message = message,
      onDismiss = onRegistrationErrorDismiss,
      dismiss = stringResource(android.R.string.ok),
      properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
  }
}
