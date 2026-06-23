/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.progress

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.registration.R
import org.signal.registration.screens.RegistrationScaffold

@Composable
fun DeviceTransferProgressScreen(
  state: DeviceTransferProgressState,
  showCancelDialog: Boolean,
  onEvent: (DeviceTransferProgressScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler(enabled = state.status != DeviceTransferProgressState.Status.FAILED) {
    onEvent(DeviceTransferProgressScreenEvents.CancelClicked)
  }

  if (showCancelDialog) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.DeviceTransferProgress__stop_transfer),
      body = stringResource(R.string.DeviceTransferProgress__all_transfer_progress_will_be_lost),
      confirm = stringResource(R.string.DeviceTransferProgress__stop_transfer),
      dismiss = stringResource(android.R.string.cancel),
      onConfirm = { onEvent(DeviceTransferProgressScreenEvents.CancelConfirmed) },
      onDismiss = { onEvent(DeviceTransferProgressScreenEvents.CancelDismissed) },
      confirmColor = MaterialTheme.colorScheme.error
    )
  }

  RegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    content = {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(modifier = Modifier.height(64.dp))

        when (state.status) {
          DeviceTransferProgressState.Status.RECEIVING,
          DeviceTransferProgressState.Status.IMPORTING,
          DeviceTransferProgressState.Status.FINALIZING -> {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))

            Spacer(modifier = Modifier.height(24.dp))

            Text(
              text = stringResource(R.string.DeviceTransferProgress__transferring_data),
              style = MaterialTheme.typography.headlineSmall,
              textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = stringResource(R.string.DeviceTransferProgress__d_messages_so_far, state.messageCount.toInt()),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
          }

          DeviceTransferProgressState.Status.FAILED -> {
            Text(
              text = stringResource(R.string.DeviceTransferProgress__unable_to_transfer),
              style = MaterialTheme.typography.headlineSmall,
              textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            val messageRes = when (state.errorReason) {
              DeviceTransferProgressState.ErrorReason.VERSION_DOWNGRADE -> R.string.DeviceTransferProgress__cannot_transfer_from_newer_signal
              DeviceTransferProgressState.ErrorReason.FOREIGN_KEY -> R.string.DeviceTransferProgress__failure_foreign_key
              DeviceTransferProgressState.ErrorReason.UNKNOWN, null -> R.string.DeviceTransferProgress__transfer_failed
            }
            Text(
              text = stringResource(messageRes),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    },
    footer = {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(
          modifier = Modifier.widthIn(max = 320.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          if (state.status == DeviceTransferProgressState.Status.FAILED) {
            Buttons.LargeTonal(
              onClick = { onEvent(DeviceTransferProgressScreenEvents.TryAgainClicked) },
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(stringResource(R.string.DeviceTransferProgress__try_again))
            }
            Spacer(modifier = Modifier.height(12.dp))
          }

          Buttons.LargeTonal(
            onClick = { onEvent(DeviceTransferProgressScreenEvents.CancelClicked) },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(stringResource(R.string.DeviceTransferProgress__cancel))
          }
        }
      }
    }
  )
}

@AllDevicePreviews
@Composable
private fun DeviceTransferProgressScreenPreview() {
  Previews.Preview {
    DeviceTransferProgressScreen(
      state = DeviceTransferProgressState(messageCount = 1234),
      showCancelDialog = false,
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun DeviceTransferProgressScreenFailedPreview() {
  Previews.Preview {
    DeviceTransferProgressScreen(
      state = DeviceTransferProgressState(status = DeviceTransferProgressState.Status.FAILED, errorReason = DeviceTransferProgressState.ErrorReason.UNKNOWN),
      showCancelDialog = false,
      onEvent = {}
    )
  }
}
