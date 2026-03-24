/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R

@Composable
fun SelectYourBackupFolderSheetContent(
  onContinueClick: () -> Unit
) {
  Column {
    SelectInstructionsSheetContent(
      title = stringResource(R.string.SelectInstructionsSheet__select_your_backup_folder),
      onContinueClick = onContinueClick
    ) {
      InstructionRow(
        icon = ImageVector.vectorResource(R.drawable.ic_tap_outline_24),
        text = stringResource(R.string.SelectInstructionsSheet__tap_select_this_folder)
      )

      InstructionRow(
        icon = ImageVector.vectorResource(R.drawable.symbol_error_circle_24),
        text = stringResource(R.string.SelectInstructionsSheet__do_not_select_individual_files)
      )
    }
  }
}

@Composable
fun SelectYourBackupFileSheetContent(
  onContinueClick: () -> Unit
) {
  Column {
    SelectInstructionsSheetContent(
      title = stringResource(R.string.SelectInstructionsSheet__select_your_backup_file),
      onContinueClick = onContinueClick
    ) {
      InstructionRow(
        icon = ImageVector.vectorResource(R.drawable.ic_tap_outline_24),
        text = stringResource(R.string.SelectInstructionsSheet__tap_on_the_backup_file)
      )
    }
  }
}

@Composable
private fun ColumnScope.SelectInstructionsSheetContent(
  title: String,
  onContinueClick: () -> Unit,
  instructionRows: @Composable ColumnScope.() -> Unit
) {
  Text(
    text = title,
    textAlign = TextAlign.Center,
    style = MaterialTheme.typography.titleLarge,
    modifier = Modifier
      .align(alignment = Alignment.CenterHorizontally)
      .padding(bottom = 8.dp, top = 38.dp)
      .padding(horizontal = 40.dp)
  )

  Text(
    text = stringResource(R.string.SelectInstructionsSheet__after_tapping_continue),
    textAlign = TextAlign.Center,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .align(alignment = Alignment.CenterHorizontally)
      .padding(bottom = 36.dp)
      .padding(horizontal = 40.dp)
  )

  Column(verticalArrangement = spacedBy(24.dp)) {
    InstructionRow(
      icon = ImageVector.vectorResource(R.drawable.symbol_folder_24),
      text = stringResource(R.string.SelectInstructionsSheet__select_the_top_level_folder)
    )

    instructionRows()
  }

  Buttons.LargeTonal(
    onClick = onContinueClick,
    modifier = Modifier
      .widthIn(min = 220.dp)
      .padding(vertical = 56.dp)
      .align(alignment = Alignment.CenterHorizontally)
  ) {
    Text(text = stringResource(R.string.SelectInstructionsSheet__continue_button))
  }
}

@Composable
private fun InstructionRow(
  icon: ImageVector,
  text: String,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .padding(horizontal = 64.dp)
      .fillMaxWidth()
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(end = 24.dp)
    )

    Text(
      text = text,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@DayNightPreviews
@Composable
private fun SelectYourBackupFolderSheetContentPreview() {
  Previews.BottomSheetPreview {
    Column {
      SelectYourBackupFolderSheetContent(
        onContinueClick = {}
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun SelectYourBackupFileSheetContentPreview() {
  Previews.BottomSheetPreview {
    Column {
      SelectYourBackupFileSheetContent(
        onContinueClick = {}
      )
    }
  }
}
