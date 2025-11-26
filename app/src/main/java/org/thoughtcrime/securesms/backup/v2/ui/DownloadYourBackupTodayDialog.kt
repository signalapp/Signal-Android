/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.compose.ComposeDialogFragment

/**
 * Displays a "last chance" dialog to the user to begin a media restore.
 */
class DownloadYourBackupTodayDialog : ComposeDialogFragment() {

  companion object {

    private const val ARGS = "args"

    fun create(downloadYourBackupData: BackupAlert.DownloadYourBackupData): DialogFragment {
      return DownloadYourBackupTodayDialog().apply {
        arguments = bundleOf(ARGS to downloadYourBackupData)
      }
    }
  }

  private val backupAlert: BackupAlert.DownloadYourBackupData by lazy(LazyThreadSafetyMode.NONE) {
    BundleCompat.getParcelable(requireArguments(), ARGS, BackupAlert.DownloadYourBackupData::class.java)!!
  }

  @Composable
  override fun DialogContent() {
    DownloadYourBackupTodayDialogContent(
      sizeToDownload = backupAlert.formattedSize,
      onConfirm = {
        BackupRepository.resumeMediaRestore()
      },
      onDismiss = {
        BackupRepository.snoozeDownloadYourBackupData()
        dismissAllowingStateLoss()
      }
    )
  }
}

@Composable
private fun DownloadYourBackupTodayDialogContent(
  sizeToDownload: String,
  onConfirm: () -> Unit = {},
  onDismiss: () -> Unit = {}
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.DownloadYourBackupTodayDialog__download_your_backup_today),
    body = stringResource(R.string.DownloadYourBackupTodayDialog__you_have_s_of_backup_data, sizeToDownload),
    confirm = stringResource(R.string.DownloadYourBackupTodayDialog__download),
    dismiss = stringResource(R.string.DownloadYourBackupTodayDialog__dont_download),
    dismissColor = MaterialTheme.colorScheme.error,
    onDismiss = onDismiss,
    onConfirm = onConfirm
  )
}

@DayNightPreviews
@Composable
private fun DownloadYourBackupTodayDialogContentPreview() {
  Previews.Preview {
    DownloadYourBackupTodayDialogContent(
      sizeToDownload = "2.3GB"
    )
  }
}
