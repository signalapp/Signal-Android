/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen

/**
 * Allows the user to select a specific on-device backup to restore.
 */
@Composable
fun SelectLocalBackupScreen(
  selectedBackup: SelectableBackup,
  isSelectedBackupLatest: Boolean,
  onRestoreBackupClick: () -> Unit,
  onCancelClick: () -> Unit,
  onChooseADifferentBackupClick: () -> Unit
) {
  RegistrationScreen(
    title = stringResource(R.string.SelectLocalBackupScreen__restore_on_device_backup),
    subtitle = stringResource(R.string.SelectLocalBackupScreen__restore_your_messages_from_the_backup_folder),
    bottomContent = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Buttons.LargeTonal(
          onClick = onRestoreBackupClick,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = stringResource(R.string.SelectLocalBackupScreen__restore_backup)
          )
        }

        TextButton(
          onClick = onCancelClick,
          modifier = Modifier.padding(top = 24.dp)
        ) {
          Text(stringResource(android.R.string.cancel))
        }
      }
    }
  ) {
    YourBackupCard(
      selectedBackup = selectedBackup,
      isSelectedBackupLatest = isSelectedBackupLatest
    )

    TextButton(
      onClick = onChooseADifferentBackupClick,
      modifier = Modifier
        .padding(top = 28.dp)
        .align(alignment = Alignment.CenterHorizontally)
    ) {
      Icon(
        imageVector = SignalIcons.Backup.imageVector,
        contentDescription = null,
        modifier = Modifier.padding(end = 8.dp)
      )

      if (isSelectedBackupLatest) {
        Text(text = stringResource(R.string.SelectLocalBackupScreen__choose_an_earlier_backup))
      } else {
        Text(text = stringResource(R.string.SelectLocalBackupScreen__choose_a_different_backup))
      }
    }
  }
}

@Composable
fun YourBackupCard(
  selectedBackup: SelectableBackup,
  isSelectedBackupLatest: Boolean,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(color = SignalTheme.colors.colorSurface2, shape = RoundedCornerShape(12.dp))
      .padding(20.dp)
  ) {
    Text(
      text = if (isSelectedBackupLatest) {
        stringResource(R.string.SelectLocalBackupScreen__your_latest_backup)
      } else {
        stringResource(R.string.SelectLocalBackupScreen__your_backup)
      },
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(bottom = 16.dp)
    )

    BackupInfoRow(
      icon = ImageVector.vectorResource(R.drawable.symbol_recent_24),
      text = selectedBackup.backupTime
    )

    BackupInfoRow(
      icon = ImageVector.vectorResource(R.drawable.symbol_file_24),
      text = selectedBackup.backupSize
    )
  }
}

@Composable
fun BackupInfoRow(
  icon: ImageVector,
  text: String
) {
  Row {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary
    )
    Text(
      text = text,
      modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)
    )
  }
}

@DayNightPreviews
@Composable
fun SelectLocalBackupScreenPreview() {
  Previews.Preview {
    SelectLocalBackupScreen(
      selectedBackup = SelectableBackup(
        timestamp = 0L,
        backupTime = "Today \u2022 12:34 PM",
        backupSize = "1.38 GB"
      ),
      isSelectedBackupLatest = true,
      onRestoreBackupClick = {},
      onCancelClick = {},
      onChooseADifferentBackupClick = {}
    )
  }
}
