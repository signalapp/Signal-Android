/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R

/**
 * Dialogs shared by every entry point that can rotate the user's recovery key, namely the backup key display flow and
 * the Signal Login details screen.
 */

/**
 * Tells the user they have to turn off storage optimization and pull their media back down before they can rotate
 * their recovery key. Storage optimization is only available with backups on, so this copy always applies.
 */
@Composable
fun DownloadMediaDialog(
  onTurnOffAndDownloadClick: () -> Unit = {},
  onCancelClick: () -> Unit = {}
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.MessageBackupsKeyRecordScreen__download_media),
    body = stringResource(R.string.MessageBackupsKeyRecordScreen__to_create_a_new_backup_key),
    confirm = stringResource(R.string.MessageBackupsKeyRecordScreen__turn_off_and_download),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = onTurnOffAndDownloadClick,
    onDeny = onCancelClick
  )
}

/**
 * Tells the user they've used up all of their recovery key rotations for now. Without backups there's nothing to turn
 * off and delete, so that suggestion is dropped from the body.
 */
@Composable
fun KeyLimitExceededDialog(
  areBackupsEnabled: Boolean,
  onClick: () -> Unit = {}
) {
  val body = if (areBackupsEnabled) {
    stringResource(R.string.MessageBackupsKeyRecordScreen__limit_exceeded_body)
  } else {
    stringResource(R.string.BackupKeyRotationDialogs__limit_exceeded_body_no_backups)
  }

  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.MessageBackupsKeyRecordScreen__limit_exceeded_title),
    body = body,
    confirm = stringResource(R.string.MessageBackupsKeyRecordScreen__ok),
    onConfirm = {},
    onDismiss = onClick
  )
}

@DayNightPreviews
@Composable
private fun DownloadMediaDialogPreview() {
  Previews.Preview {
    DownloadMediaDialog()
  }
}

@DayNightPreviews
@Composable
private fun KeyLimitExceededDialogPreview() {
  Previews.Preview {
    KeyLimitExceededDialog(areBackupsEnabled = true)
  }
}

@DayNightPreviews
@Composable
private fun KeyLimitExceededDialogNoBackupsPreview() {
  Previews.Preview {
    KeyLimitExceededDialog(areBackupsEnabled = false)
  }
}
