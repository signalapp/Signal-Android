/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R

@Composable
fun SelectLocalBackupSheetContent(
  selectedBackup: SelectableBackup,
  selectableBackups: PersistentList<SelectableBackup>,
  onBackupSelected: (SelectableBackup) -> Unit
) {
  var selection by remember { mutableStateOf(selectedBackup) }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(R.string.SelectLocalBackupSheet__choose_a_backup_to_restore),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .padding(horizontal = 32.dp)
        .padding(bottom = 6.dp, top = 20.dp)
    )

    Text(
      text = stringResource(R.string.SelectLocalBackupSheet__choosing_an_older_backup),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier
        .padding(horizontal = 32.dp)
        .padding(bottom = 38.dp)
    )

    val backups = remember(selectableBackups) {
      selectableBackups.take(2)
    }

    backups.forEachIndexed { idx, backup ->
      val shape = if (idx == 0) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
      } else {
        RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
      }

      Rows.RadioRow(
        text = backup.backupTime,
        label = backup.backupSize,
        selected = selection == backup,
        modifier = Modifier
          .horizontalGutters()
          .clip(shape)
          .background(
            color = MaterialTheme.colorScheme.surface,
            shape = shape
          )
          .clickable(onClick = {
            selection = backup
          })
      )
    }

    Buttons.LargeTonal(
      onClick = { onBackupSelected(selection) },
      enabled = selectedBackup != selection,
      modifier = Modifier
        .padding(vertical = 56.dp)
        .widthIn(min = 220.dp)
    ) {
      Text(text = stringResource(R.string.SelectLocalBackupSheet__continue_button))
    }
  }
}

@DayNightPreviews
@Composable
private fun SelectLocalBackupSheetContentPreview() {
  Previews.BottomSheetPreview {
    SelectLocalBackupSheetContent(
      selectedBackup = SelectableBackup(
        timestamp = 0L,
        backupTime = "Today \u2022 3:38am",
        backupSize = "1.38 GB"
      ),
      selectableBackups = persistentListOf(
        SelectableBackup(
          timestamp = 0L,
          backupTime = "Today \u2022 3:38am",
          backupSize = "1.38 GB"
        ),
        SelectableBackup(
          timestamp = 1L,
          backupTime = "August 13, 2024 \u2022 3:21am",
          backupSize = "1.34 GB"
        )
      ),
      onBackupSelected = {}
    )
  }
}
