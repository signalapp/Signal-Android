/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restoreselection

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
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

  when (val layoutParams = RegistrationScaffold.rememberLayoutParams()) {
    is RegistrationScaffold.Params.OnePane -> OnePaneLayout(layoutParams, state, onEvent, modifier)
    is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(layoutParams, state, onEvent, modifier)
  }
}

@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  state: ArchiveRestoreSelectionState,
  onEvent: (ArchiveRestoreSelectionScreenEvents) -> Unit,
  modifier: Modifier
) {
  val scrollState = rememberScrollState()
  OnePaneRegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    params = params,
    content = { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(paddingValues)
          .testTag(TestTags.ARCHIVE_RESTORE_SELECTION_SCREEN),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Description()

        Spacer(modifier = Modifier.height(28.dp))

        RestoreOptions(state, onEvent)
      }
    }
  )
}

@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: ArchiveRestoreSelectionState,
  onEvent: (ArchiveRestoreSelectionScreenEvents) -> Unit,
  modifier: Modifier
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()

  TwoPaneRegistrationScaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.ARCHIVE_RESTORE_SELECTION_SCREEN),
    params = params,
    firstPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues)
      ) {
        Description()
      }
    },
    secondPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues)
      ) {
        RestoreOptions(state, onEvent)
      }
    }
  )
}

@Composable
private fun Description() {
  Text(
    text = stringResource(R.string.ArchiveRestoreSelectionScreen__restore_or_transfer_account),
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )

  Text(
    text = stringResource(R.string.ArchiveRestoreSelectionScreen__subheading),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 16.dp)
  )
}

@Composable
private fun RestoreOptions(state: ArchiveRestoreSelectionState, onEvent: (ArchiveRestoreSelectionScreenEvents) -> Unit) {
  state.restoreOptions.forEachIndexed { index, option ->
    if (index > 0) {
      Spacer(modifier = Modifier.height(12.dp))
    }
    RestoreOptionCard(
      option = option,
      onClick = { onEvent(ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected(option)) }
    )
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
        imageVector = SignalIcons.SignalBackupsDisplay.imageVector,
        title = stringResource(R.string.ArchiveRestoreSelectionScreen__from_signal_backups),
        subtitle = stringResource(R.string.ArchiveRestoreSelectionScreen__your_free_or_paid_signal_backup_plan),
        onClick = onClick,
        modifier = modifier.testTag(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
      )
    }

    ArchiveRestoreOption.DeviceTransfer -> {
      SelectionCard(
        imageVector = SignalIcons.TransferDisplay.imageVector,
        title = stringResource(R.string.ArchiveRestoreSelectionScreen__from_your_old_phone),
        subtitle = stringResource(R.string.ArchiveRestoreSelectionScreen__transfer_directly_from_old),
        onClick = onClick,
        modifier = modifier.testTag(TestTags.ARCHIVE_RESTORE_SELECTION_DEVICE_TRANSFER)
      )
    }

    ArchiveRestoreOption.LocalBackup -> {
      SelectionCard(
        imageVector = SignalIcons.FolderDisplay.imageVector,
        title = stringResource(R.string.ArchiveRestoreSelectionScreen__local_backup_card_title),
        subtitle = stringResource(R.string.ArchiveRestoreSelectionScreen__local_backup_card_description),
        onClick = onClick,
        modifier = modifier.testTag(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER)
      )
    }

    ArchiveRestoreOption.None -> {
      SelectionCard(
        imageVector = SignalIcons.MobileNextDisplay.imageVector,
        title = stringResource(R.string.ArchiveRestoreSelectionScreen__skip_restore_title),
        subtitle = stringResource(R.string.ArchiveRestoreSelectionScreen__skip_restore_description),
        onClick = onClick,
        modifier = modifier.testTag(TestTags.ARCHIVE_RESTORE_SELECTION_NONE)
      )
    }
  }
}

@Composable
private fun SelectionCard(
  imageVector: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    onClick = onClick,
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ),
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(16.dp)
    ) {
      Icon(imageVector = imageVector, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))

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
