/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.devicetransfer.moreoptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.devicetransfer.newdevice.BackupRestorationType

/**
 * Lists a set of options the user can choose from for restoring backup or skipping restoration
 */
class MoreTransferOrRestoreOptionsSheet : ComposeBottomSheetDialogFragment() {

  private val args by navArgs<MoreTransferOrRestoreOptionsSheetArgs>()

  @Composable
  override fun SheetContent() {
    var selectedOption by remember {
      mutableStateOf<BackupRestorationType?>(null)
    }

    MoreOptionsSheetContent(
      mode = args.mode,
      selectedOption = selectedOption,
      onOptionSelected = { selectedOption = it },
      onCancelClick = { findNavController().popBackStack() },
      onNextClick = {
        this.onNextClicked(selectedOption ?: BackupRestorationType.NONE)
      }
    )
  }

  private fun onNextClicked(selectedOption: BackupRestorationType) {
    // TODO [message-requests] -- Launch next screen based off user choice
  }
}

@Preview
@Composable
private fun MoreOptionsSheetContentPreview() {
  Previews.BottomSheetPreview {
    MoreOptionsSheetContent(
      mode = MoreTransferOrRestoreOptionsMode.SKIP_ONLY,
      selectedOption = null,
      onOptionSelected = {},
      onCancelClick = {},
      onNextClick = {}
    )
  }
}

@Preview
@Composable
private fun MoreOptionsSheetSelectableContentPreview() {
  Previews.BottomSheetPreview {
    MoreOptionsSheetContent(
      mode = MoreTransferOrRestoreOptionsMode.SELECTION,
      selectedOption = null,
      onOptionSelected = {},
      onCancelClick = {},
      onNextClick = {}
    )
  }
}

@Composable
private fun MoreOptionsSheetContent(
  mode: MoreTransferOrRestoreOptionsMode,
  selectedOption: BackupRestorationType?,
  onOptionSelected: (BackupRestorationType) -> Unit,
  onCancelClick: () -> Unit,
  onNextClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
  ) {
    BottomSheets.Handle()

    Spacer(modifier = Modifier.size(42.dp))

    if (mode == MoreTransferOrRestoreOptionsMode.SELECTION) {
      TransferFromAndroidDeviceOption(
        selectedOption = selectedOption,
        onOptionSelected = onOptionSelected
      )
      Spacer(modifier = Modifier.size(16.dp))
      RestoreLocalBackupOption(
        selectedOption = selectedOption,
        onOptionSelected = onOptionSelected
      )
      Spacer(modifier = Modifier.size(16.dp))
    }

    LogInWithoutTransferringOption(
      selectedOption = selectedOption,
      onOptionSelected = when (mode) {
        MoreTransferOrRestoreOptionsMode.SKIP_ONLY -> { _ -> onNextClick() }
        MoreTransferOrRestoreOptionsMode.SELECTION -> onOptionSelected
      }
    )

    if (mode == MoreTransferOrRestoreOptionsMode.SELECTION) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 30.dp, bottom = 24.dp)
      ) {
        TextButton(
          onClick = onCancelClick
        ) {
          Text(text = stringResource(id = android.R.string.cancel))
        }

        Spacer(modifier = Modifier.weight(1f))

        Buttons.LargeTonal(
          enabled = selectedOption != null,
          onClick = onNextClick
        ) {
          Text(text = stringResource(id = R.string.RegistrationActivity_next))
        }
      }
    } else {
      Spacer(modifier = Modifier.size(45.dp))
    }
  }
}

@Preview
@Composable
private fun LogInWithoutTransferringOptionPreview() {
  Previews.BottomSheetPreview {
    LogInWithoutTransferringOption(
      selectedOption = null,
      onOptionSelected = {}
    )
  }
}

@Composable
private fun LogInWithoutTransferringOption(
  selectedOption: BackupRestorationType?,
  onOptionSelected: (BackupRestorationType) -> Unit
) {
  Option(
    icon = {
      Box(
        modifier = Modifier.padding(horizontal = 18.dp)
      ) {
        Icon(
          painter = painterResource(id = R.drawable.symbol_backup_light), // TODO [message-backups] Finalized asset.
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(36.dp)
        )
      }
    },
    isSelected = selectedOption == BackupRestorationType.NONE,
    title = stringResource(id = R.string.MoreTransferOrRestoreOptionsSheet__log_in_without_transferring),
    subtitle = stringResource(id = R.string.MoreTransferOrRestoreOptionsSheet__continue_without_transferring),
    onClick = { onOptionSelected(BackupRestorationType.NONE) }
  )
}

@Preview
@Composable
private fun TransferFromAndroidDeviceOptionPreview() {
  Previews.BottomSheetPreview {
    TransferFromAndroidDeviceOption(
      selectedOption = null,
      onOptionSelected = {}
    )
  }
}

@Composable
private fun TransferFromAndroidDeviceOption(
  selectedOption: BackupRestorationType?,
  onOptionSelected: (BackupRestorationType) -> Unit
) {
  Option(
    icon = {
      Box(
        modifier = Modifier.padding(horizontal = 18.dp)
      ) {
        Icon(
          painter = painterResource(id = R.drawable.symbol_backup_light), // TODO [message-backups] Finalized asset.
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(36.dp)
        )
      }
    },
    isSelected = selectedOption == BackupRestorationType.DEVICE_TRANSFER,
    title = stringResource(id = R.string.MoreTransferOrRestoreOptionsSheet__transfer_from_android_device),
    subtitle = stringResource(id = R.string.MoreTransferOrRestoreOptionsSheet__transfer_your_account_and_messages),
    onClick = { onOptionSelected(BackupRestorationType.DEVICE_TRANSFER) }
  )
}

@Preview
@Composable
private fun RestoreLocalBackupOptionPreview() {
  Previews.BottomSheetPreview {
    RestoreLocalBackupOption(
      selectedOption = null,
      onOptionSelected = {}
    )
  }
}

@Composable
private fun RestoreLocalBackupOption(
  selectedOption: BackupRestorationType?,
  onOptionSelected: (BackupRestorationType) -> Unit
) {
  Option(
    icon = {
      Box(
        modifier = Modifier.padding(horizontal = 18.dp)
      ) {
        Icon(
          painter = painterResource(id = R.drawable.symbol_backup_light), // TODO [message-backups] Finalized asset.
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(36.dp)
        )
      }
    },
    isSelected = selectedOption == BackupRestorationType.LOCAL_BACKUP,
    title = stringResource(id = R.string.MoreTransferOrRestoreOptionsSheet__restore_local_backup),
    subtitle = stringResource(id = R.string.MoreTransferOrRestoreOptionsSheet__restore_your_messages),
    onClick = { onOptionSelected(BackupRestorationType.LOCAL_BACKUP) }
  )
}

@Preview
@Composable
private fun OptionPreview() {
  Previews.BottomSheetPreview {
    Option(
      icon = {
        Box(
          modifier = Modifier.padding(horizontal = 18.dp)
        ) {
          Icon(
            painter = painterResource(id = R.drawable.symbol_backup_light),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp)
          )
        }
      },
      isSelected = false,
      title = "Option Preview Title",
      subtitle = "Option Preview Subtitle",
      onClick = {}
    )
  }
}

@Composable
private fun Option(
  icon: @Composable () -> Unit,
  isSelected: Boolean,
  title: String,
  subtitle: String,
  onClick: () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .background(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
      )
      .border(
        width = if (isSelected) 2.dp else 0.dp,
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
      )
      .clip(RoundedCornerShape(12.dp))
      .clickable { onClick() }
      .padding(vertical = 21.dp)
  ) {
    icon()
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
