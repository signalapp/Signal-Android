/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.exportaccountdata

import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.signal.appsettings.R
import org.signal.core.ui.compose.Dialogs

@Composable
internal fun DownloadProgressDialog() {
  Dialog(
    onDismissRequest = {},
    DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
  ) {
    Card(modifier = Modifier.testTag(ExportAccountDataTestTags.DIALOG_GENERATING_REPORT)) {
      Box(contentAlignment = Alignment.Center) {
        Column(
          verticalArrangement = Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          CircularProgressIndicator(
            modifier = Modifier
              .padding(top = 50.dp, bottom = 18.dp)
              .size(42.dp)
          )
          Text(text = stringResource(R.string.ExportAccountDataFragment__download_progress), Modifier.padding(bottom = 48.dp, start = 35.dp, end = 35.dp))
        }
      }
    }
  }
}

@Composable
internal fun DownloadFailedDialog(onEvent: (ExportAccountDataEvent) -> Unit) {
  Dialogs.SimpleMessageDialog(
    message = stringResource(id = R.string.ExportAccountDataFragment__check_network),
    dismiss = stringResource(id = R.string.ExportAccountDataFragment__ok_action),
    title = stringResource(id = R.string.ExportAccountDataFragment__report_generation_failed),
    onDismiss = { onEvent(ExportAccountDataEvent.DialogDismissed) },
    modifier = Modifier.testTag(ExportAccountDataTestTags.DIALOG_REPORT_GENERATION_FAILED)
  )
}

@Composable
internal fun ExportReportConfirmationDialog(onEvent: (ExportAccountDataEvent) -> Unit) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.ExportAccountDataFragment__export_report_confirmation),
    body = stringResource(R.string.ExportAccountDataFragment__export_report_confirmation_message),
    confirm = stringResource(R.string.ExportAccountDataFragment__export_report_action),
    dismiss = stringResource(R.string.ExportAccountDataFragment__cancel_action),
    onConfirm = { onEvent(ExportAccountDataEvent.ExportConfirmed) },
    onDeny = { onEvent(ExportAccountDataEvent.DialogDismissed) },
    onDismissRequest = { onEvent(ExportAccountDataEvent.DialogDismissed) },
    modifier = Modifier.testTag(ExportAccountDataTestTags.DIALOG_CONFIRM_EXPORT)
  )
}
