/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen

/**
 * User can select either a folder-based or single backup file for restoration during registration.
 */
@Composable
fun SelectLocalBackupTypeScreen(
  onSelectBackupFolderClick: () -> Unit,
  onSelectBackupFileClick: () -> Unit,
  onCancelClick: () -> Unit
) {
  RegistrationScreen(
    title = stringResource(R.string.SelectLocalBackupTypeScreen__restore_on_device_backup),
    subtitle = stringResource(R.string.SelectLocalBackupTypeScreen__restore_your_messages_from_the_backup),
    bottomContent = {
      TextButton(onClick = onCancelClick, modifier = Modifier.align(Alignment.Center)) {
        Text(stringResource(android.R.string.cancel))
      }
    }
  ) {
    ChooseBackupFolderCard(
      onClick = onSelectBackupFolderClick,
      modifier = Modifier.align(Alignment.CenterHorizontally)
    )

    TextButton(
      onClick = onSelectBackupFileClick,
      modifier = Modifier
        .padding(top = 24.dp)
        .align(Alignment.CenterHorizontally)
    ) {
      Text(
        text = stringResource(R.string.SelectLocalBackupTypeScreen__i_saved_my_backup_as_a_single_file)
      )
    }
  }
}

@Composable
private fun ChooseBackupFolderCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .background(color = SignalTheme.colors.colorSurface2, shape = RoundedCornerShape(12.dp))
      .clickable(onClick = onClick, role = Role.Button)
      .padding(end = 24.dp)
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.symbol_folder_24),
      tint = MaterialTheme.colorScheme.primary,
      contentDescription = null,
      modifier = Modifier
        .padding(horizontal = 16.dp, vertical = 21.dp)
        .size(40.dp)
    )

    Column {
      Text(
        text = stringResource(R.string.SelectLocalBackupTypeScreen__choose_backup_folder)
      )

      Text(
        text = stringResource(R.string.SelectLocalBackupTypeScreen__select_the_folder_on_your_device),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun SelectLocalBackupTypeScreenPreview() {
  Previews.Preview {
    SelectLocalBackupTypeScreen(
      onSelectBackupFolderClick = {},
      onSelectBackupFileClick = {},
      onCancelClick = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun ChooseBackupFolderCardPreview() {
  Previews.Preview {
    ChooseBackupFolderCard(onClick = {})
  }
}
