/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.signal.core.ui.R as CoreUiR

/**
 * Bottom sheet shown after a successful paid backup subscription from a storage upsell megaphone.
 * Allows the user to start their first backup and optionally enable storage optimization.
 */
class BackupSetupCompleteBottomSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.75f

  @Composable
  override fun SheetContent() {
    SetupCompleteSheetContent(
      onBackUpNowClick = { optimizeStorage ->
        SignalStore.backup.optimizeStorage = optimizeStorage
        BackupMessagesJob.enqueue()
        dismissAllowingStateLoss()
      }
    )
  }
}

@Composable
private fun SetupCompleteSheetContent(
  onBackUpNowClick: (optimizeStorage: Boolean) -> Unit
) {
  var optimizeStorage by rememberSaveable { mutableStateOf(true) }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter))
  ) {
    BottomSheets.Handle()

    Spacer(modifier = Modifier.size(26.dp))

    Image(
      imageVector = ImageVector.vectorResource(id = R.drawable.image_signal_backups_subscribed),
      contentDescription = null,
      modifier = Modifier
        .size(80.dp)
        .padding(2.dp)
    )

    Text(
      text = stringResource(R.string.BackupSetupCompleteBottomSheet__title),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
    )

    Text(
      text = stringResource(R.string.BackupSetupCompleteBottomSheet__body),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 24.dp)
    )

    Rows.ToggleRow(
      checked = optimizeStorage,
      text = stringResource(R.string.BackupSetupCompleteBottomSheet__optimize_storage),
      label = stringResource(R.string.BackupSetupCompleteBottomSheet__optimize_subtitle),
      onCheckChanged = { optimizeStorage = it },
      modifier = Modifier.padding(bottom = 24.dp)
    )

    Buttons.LargeTonal(
      onClick = { onBackUpNowClick(optimizeStorage) },
      modifier = Modifier
        .defaultMinSize(minWidth = 220.dp)
        .padding(bottom = 56.dp)
    ) {
      Text(text = stringResource(R.string.BackupSetupCompleteBottomSheet__back_up_now))
    }
  }
}

@DayNightPreviews
@Composable
private fun BackupSetupCompleteBottomSheetPreview() {
  Previews.BottomSheetContentPreview {
    SetupCompleteSheetContent(
      onBackUpNowClick = {}
    )
  }
}
