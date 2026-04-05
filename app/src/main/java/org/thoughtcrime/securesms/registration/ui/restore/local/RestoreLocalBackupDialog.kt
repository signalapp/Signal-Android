/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.Dialogs
import org.thoughtcrime.securesms.R

enum class RestoreLocalBackupDialog {
  FAILED_TO_LOAD_ARCHIVE,
  SKIP_RESTORE_WARNING,
  CONFIRM_DIFFERENT_ACCOUNT
}

@Composable
fun RestoreLocalBackupDialogDisplay(
  dialog: RestoreLocalBackupDialog?,
  onDialogConfirmed: (RestoreLocalBackupDialog) -> Unit,
  onDialogDenied: (RestoreLocalBackupDialog) -> Unit,
  onDismiss: () -> Unit
) {
  when (dialog) {
    RestoreLocalBackupDialog.FAILED_TO_LOAD_ARCHIVE -> {
      Dialogs.SimpleMessageDialog(
        message = stringResource(R.string.RestoreLocalBackupDialog__failed_to_load_archive),
        onDismiss = onDismiss,
        dismiss = stringResource(R.string.RestoreLocalBackupDialog__ok)
      )
    }

    RestoreLocalBackupDialog.SKIP_RESTORE_WARNING -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.RestoreLocalBackupDialog__skip_restore),
        body = stringResource(R.string.RestoreLocalBackupDialog__skip_restore_body),
        confirm = stringResource(R.string.RestoreLocalBackupDialog__skip_restore_confirm),
        confirmColor = MaterialTheme.colorScheme.error,
        onConfirm = {
          onDialogConfirmed(RestoreLocalBackupDialog.SKIP_RESTORE_WARNING)
        },
        dismiss = stringResource(android.R.string.cancel)
      )
    }

    RestoreLocalBackupDialog.CONFIRM_DIFFERENT_ACCOUNT -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.RestoreLocalBackupDialog__restore_to_new_account),
        body = stringResource(R.string.RestoreLocalBackupDialog__restore_to_new_account_body),
        confirm = stringResource(R.string.RestoreLocalBackupDialog__restore),
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = {
          onDialogConfirmed(RestoreLocalBackupDialog.CONFIRM_DIFFERENT_ACCOUNT)
        },
        onDeny = {
          onDialogDenied(RestoreLocalBackupDialog.CONFIRM_DIFFERENT_ACCOUNT)
        }
      )
    }

    null -> return
  }
}
