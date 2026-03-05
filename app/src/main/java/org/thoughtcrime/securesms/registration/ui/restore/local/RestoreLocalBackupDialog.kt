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
  SKIP_RESTORE_WARNING
}

@Composable
fun RestoreLocalBackupDialogDisplay(
  dialog: RestoreLocalBackupDialog?,
  onDialogConfirmed: (RestoreLocalBackupDialog) -> Unit,
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
        title = "Skip restore?",
        body = "If you skip restore now you will not be able to restore later. If you re-enable backups after skipping restore, your current backup will be replaced with your new messaging history.",
        confirm = "Skip restore",
        confirmColor = MaterialTheme.colorScheme.error,
        onConfirm = {
          onDialogConfirmed(RestoreLocalBackupDialog.SKIP_RESTORE_WARNING)
        },
        dismiss = stringResource(android.R.string.cancel)
      )
    }

    null -> return
  }
}
