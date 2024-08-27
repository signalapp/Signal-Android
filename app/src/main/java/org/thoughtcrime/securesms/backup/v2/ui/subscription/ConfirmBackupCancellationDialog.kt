/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmBackupCancellationDialog(
  onConfirmAndDownloadNow: () -> Unit,
  onConfirmAndDownloadLater: () -> Unit,
  onKeepSubscriptionClick: () -> Unit
) {
  BasicAlertDialog(onDismissRequest = onKeepSubscriptionClick) {
    Surface(
      shape = AlertDialogDefaults.shape,
      color = AlertDialogDefaults.containerColor
    ) {
      Column {
        Text(
          text = stringResource(id = R.string.ConfirmBackupCancellationDialog__confirm_cancellation),
          color = AlertDialogDefaults.titleContentColor,
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier
            .padding(top = 24.dp)
            .padding(horizontal = 24.dp)
        )

        Text(
          text = stringResource(id = R.string.ConfirmBackupCancellationDialog__you_wont_be_charged_again),
          color = AlertDialogDefaults.textContentColor,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier
            .padding(top = 16.dp)
            .padding(horizontal = 24.dp)
        )

        TextButton(
          onClick = onConfirmAndDownloadNow,
          modifier = Modifier
            .align(Alignment.End)
            .padding(end = 12.dp)
        ) {
          Text(
            text = stringResource(id = R.string.ConfirmBackupCancellationDialog__confirm_and_download_now)
          )
        }

        TextButton(
          onClick = onConfirmAndDownloadLater,
          modifier = Modifier
            .align(Alignment.End)
            .padding(end = 12.dp)
        ) {
          Text(
            text = stringResource(id = R.string.ConfirmBackupCancellationDialog__confirm_and_download_later)
          )
        }

        TextButton(
          onClick = onKeepSubscriptionClick,
          modifier = Modifier
            .align(Alignment.End)
            .padding(end = 12.dp, bottom = 12.dp)
        ) {
          Text(
            text = stringResource(id = R.string.ConfirmBackupCancellationDialog__keep_subscription)
          )
        }
      }
    }
  }
}

@SignalPreview
@Composable
private fun ConfirmCancellationDialogPreview() {
  Previews.Preview {
    ConfirmBackupCancellationDialog(
      onKeepSubscriptionClick = {},
      onConfirmAndDownloadNow = {},
      onConfirmAndDownloadLater = {}
    )
  }
}
