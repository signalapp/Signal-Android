/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restoreselection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.registration.R
import org.signal.registration.test.TestTags

@Composable
fun ArchiveRestoreSelectionScreen(
  state: ArchiveRestoreSelectionState,
  onEvent: (ArchiveRestoreSelectionScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  if (state.showSkipWarningDialog) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.ArchiveRestoreSelectionScreen__skip_restore_dialog_title),
      body = stringResource(R.string.ArchiveRestoreSelectionScreen__skip_restore_dialog_warning),
      confirm = stringResource(R.string.ArchiveRestoreSelectionScreen__skip_restore_dialog_confirm_button),
      dismiss = stringResource(android.R.string.cancel),
      onConfirm = { onEvent(ArchiveRestoreSelectionScreenEvents.ConfirmSkip) },
      onDismiss = { onEvent(ArchiveRestoreSelectionScreenEvents.DismissSkipWarning) },
      confirmColor = MaterialTheme.colorScheme.error,
      properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
  }

  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(horizontal = 24.dp)
      .testTag(TestTags.ARCHIVE_RESTORE_SELECTION_SCREEN),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(modifier = Modifier.height(40.dp))

    Text(
      text = stringResource(R.string.ArchiveRestoreSelectionScreen__restore_or_transfer_account),
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(R.string.ArchiveRestoreSelectionScreen__subheading),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(28.dp))

    state.restoreOptions.forEachIndexed { index, option ->
      if (index > 0) {
        Spacer(modifier = Modifier.height(12.dp))
      }
      RestoreOptionCard(
        option = option,
        onClick = { onEvent(ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected(option)) }
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    if (state.showSkipButton) {
      TextButton(
        onClick = { onEvent(ArchiveRestoreSelectionScreenEvents.Skip) },
        modifier = Modifier
          .padding(bottom = 32.dp)
          .testTag(TestTags.ARCHIVE_RESTORE_SELECTION_SKIP)
      ) {
        Text(
          text = stringResource(R.string.ArchiveRestoreSelectionScreen__skip),
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}

@Composable
private fun RestoreOptionCard(
  option: ArchiveRestoreOption,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  when (option) {
    ArchiveRestoreOption.SignalSecureBackup -> {
      SelectionCard(
        icon = { Icon(painter = SignalIcons.Backup.painter, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
        title = stringResource(R.string.ArchiveRestoreSelectionScreen__from_signal_backups),
        subtitle = stringResource(R.string.ArchiveRestoreSelectionScreen__your_free_or_paid_signal_backup_plan),
        onClick = onClick,
        modifier = modifier.testTag(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
      )
    }
    ArchiveRestoreOption.DeviceTransfer -> {
      SelectionCard(
        icon = { Icon(painter = painterResource(R.drawable.symbol_transfer_24), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
        title = stringResource(R.string.ArchiveRestoreSelectionScreen__from_your_old_phone),
        subtitle = stringResource(R.string.ArchiveRestoreSelectionScreen__transfer_directly_from_old),
        onClick = onClick,
        modifier = modifier.testTag(TestTags.ARCHIVE_RESTORE_SELECTION_DEVICE_TRANSFER)
      )
    }
    ArchiveRestoreOption.LocalBackup -> {
      SelectionCard(
        icon = { Icon(painter = painterResource(R.drawable.symbol_folder_24), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
        title = stringResource(R.string.ArchiveRestoreSelectionScreen__local_backup_card_title),
        subtitle = stringResource(R.string.ArchiveRestoreSelectionScreen__local_backup_card_description),
        onClick = onClick,
        modifier = modifier.testTag(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER)
      )
    }

    ArchiveRestoreOption.None -> {
      SelectionCard(
        icon = { Icon(painter = painterResource(R.drawable.symbol_folder_24), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
        title = stringResource(R.string.ArchiveRestoreSelectionScreen__skip_restore_title),
        subtitle = stringResource(R.string.ArchiveRestoreSelectionScreen__skip_restore_description),
        onClick = onClick,
        modifier = modifier.testTag(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER)
      )
    }
  }
}

@Composable
private fun SelectionCard(
  icon: @Composable () -> Unit,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ),
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(16.dp)
    ) {
      icon()

      Spacer(modifier = Modifier.width(16.dp))

      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@AllDevicePreviews
@Composable
private fun ArchiveRestoreSelectionScreenPreview() {
  Previews.Preview {
    ArchiveRestoreSelectionScreen(
      state = ArchiveRestoreSelectionState(
        restoreOptions = listOf(ArchiveRestoreOption.SignalSecureBackup, ArchiveRestoreOption.LocalBackup, ArchiveRestoreOption.DeviceTransfer, ArchiveRestoreOption.None)
      ),
      onEvent = {}
    )
  }
}
