/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Displays an alert to the user if they've passed a given threshold without
 * performing a manual backup.
 */
class NoManualBackupBottomSheet : ComposeBottomSheetDialogFragment() {
  @Composable
  override fun SheetContent() {
    val durationSinceLastBackup = remember {
      System.currentTimeMillis().milliseconds - SignalStore.backup.lastBackupTime.milliseconds
    }

    NoManualBackupSheetContent(
      durationSinceLastBackup = durationSinceLastBackup,
      onBackUpNowClick = {
        BackupMessagesJob.enqueue()
        startActivity(AppSettingsActivity.remoteBackups(requireActivity()))
        dismissAllowingStateLoss()
      },
      onNotNowClick = this::dismissAllowingStateLoss
    )
  }
}

@Composable
private fun NoManualBackupSheetContent(
  durationSinceLastBackup: Duration,
  onBackUpNowClick: () -> Unit = {},
  onNotNowClick: () -> Unit = {}
) {
  val primaryActionLabel = stringResource(R.string.BackupAlertBottomSheet__back_up_now)
  val primaryAction = remember { BackupAlertActionButtonState(primaryActionLabel, onBackUpNowClick) }
  val secondaryActionLabel = stringResource(android.R.string.cancel)
  val secondaryAction = remember { BackupAlertActionButtonState(secondaryActionLabel, onNotNowClick) }
  val days: Int = durationSinceLastBackup.inWholeDays.toInt()

  BackupAlertBottomSheetContainer(
    icon = { BackupAlertIcon(iconColors = BackupsIconColors.Warning) },
    title = pluralStringResource(R.plurals.NoManualBackupBottomSheet__no_backup_for_d_days, days, days),
    primaryActionButtonState = primaryAction,
    secondaryActionButtonState = secondaryAction
  ) {
    BackupAlertText(
      text = pluralStringResource(R.plurals.NoManualBackupBottomSheet__you_have_not_completed_a_backup, days, days),
      modifier = Modifier.padding(bottom = 38.dp)
    )
  }
}

@DayNightPreviews
@Composable
private fun NoManualBackupSheetContentPreview() {
  Previews.BottomSheetPreview {
    NoManualBackupSheetContent(
      durationSinceLastBackup = 30.days
    )
  }
}
