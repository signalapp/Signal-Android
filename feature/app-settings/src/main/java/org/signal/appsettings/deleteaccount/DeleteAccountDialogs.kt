/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.deleteaccount

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.signal.appsettings.R
import org.signal.appsettings.deleteaccount.DeleteAccountState.Dialog
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.R as CoreUiR

/**
 * Every dialog both delete account screens show the same way, which is all of them bar the confirmation prompt. That
 * one differs between the two, so each screen renders its own.
 */
@Composable
internal fun DeleteAccountDialogs(
  dialog: Dialog,
  onEvent: (DeleteAccountEvent) -> Unit
) {
  when (dialog) {
    Dialog.None,
    Dialog.ConfirmDeletion,
    is Dialog.ConfirmNumberlessDeletion -> Unit

    Dialog.NumberDoesNotMatch -> {
      Dialogs.SimpleMessageDialog(
        message = stringResource(R.string.DeleteAccountFragment__the_phone_number),
        dismiss = stringResource(android.R.string.ok),
        onDismiss = { onEvent(DeleteAccountEvent.DialogDismissed) },
        modifier = Modifier.testTag(DeleteAccountTestTags.DIALOG_NUMBER_DOES_NOT_MATCH)
      )
    }

    Dialog.DeletionFailed -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.DeleteAccountFragment__account_not_deleted),
        body = stringResource(R.string.DeleteAccountFragment__there_was_a_problem),
        confirm = stringResource(android.R.string.ok),
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = { onEvent(DeleteAccountEvent.DeletionConfirmed) },
        onDismiss = { onEvent(DeleteAccountEvent.DialogDismissed) },
        modifier = Modifier.testTag(DeleteAccountTestTags.DIALOG_DELETION_FAILED)
      )
    }

    Dialog.LocalDataDeletionFailed -> {
      Dialogs.SimpleMessageDialog(
        message = stringResource(R.string.DeleteAccountFragment__failed_to_delete_local_data),
        dismiss = stringResource(R.string.DeleteAccountFragment__launch_app_settings),
        onDismiss = { onEvent(DeleteAccountEvent.LaunchAppSettingsClicked) },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        modifier = Modifier.testTag(DeleteAccountTestTags.DIALOG_LOCAL_DATA_DELETION_FAILED)
      )
    }

    Dialog.CancelingSubscription -> {
      ProgressDialog(
        title = stringResource(R.string.DeleteAccountFragment__deleting_account),
        message = stringResource(R.string.DeleteAccountFragment__canceling_your_subscription),
        progress = null
      )
    }

    is Dialog.LeavingGroups -> {
      ProgressDialog(
        title = stringResource(R.string.DeleteAccountFragment__leaving_groups),
        message = stringResource(R.string.DeleteAccountFragment__depending_on_the_number_of_groups),
        progress = if (dialog.totalCount > 0) dialog.leaveCount.toFloat() / dialog.totalCount else null
      )
    }

    Dialog.DeletingAccount -> {
      ProgressDialog(
        title = stringResource(R.string.DeleteAccountFragment__deleting_account),
        message = stringResource(R.string.DeleteAccountFragment__deleting_all_user_data_and_resetting),
        progress = null
      )
    }
  }
}

/**
 * Non-dismissable spinner shown for the length of the deletion, which reports what part of it is underway.
 */
@Composable
private fun ProgressDialog(
  title: String,
  message: String,
  progress: Float?
) {
  Dialogs.BaseAlertDialog(
    onDismissRequest = {},
    confirmButton = {},
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    text = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
      ) {
        Spacer(modifier = Modifier.height(24.dp))

        if (progress == null) {
          CircularProgressIndicator(modifier = Modifier.size(48.dp))
        } else {
          CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
        )

        Spacer(modifier = Modifier.height(24.dp))
      }
    },
    modifier = Modifier.testTag(DeleteAccountTestTags.DIALOG_PROGRESS)
  )
}
