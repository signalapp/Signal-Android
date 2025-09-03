/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.status

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.util.mebiBytes
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.RestoreState
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgressState
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgressState.RestoreStatus
import org.thoughtcrime.securesms.backup.v2.ui.BackupsIconColors
import kotlin.math.roundToInt
import org.signal.core.ui.R as CoreUiR

/**
 * Specifies what kind of restore this is. Slightly different messaging
 * is utilized for downloads.
 */
enum class RestoreType {
  /**
   * Restoring, when the user has downloads enabled but is restoring optimized media.
   */
  RESTORE,

  /**
   * Downloading, when the user has opted to turn off and delete download, and we are
   * downloading optimized media.
   */
  DOWNLOAD
}

/**
 * Backup status displayable as a row on a settings page.
 */
@Composable
fun BackupStatusRow(
  backupStatusData: ArchiveRestoreProgressState,
  restoreType: RestoreType = RestoreType.RESTORE,
  onSkipClick: () -> Unit = {},
  onCancelClick: (() -> Unit)? = null
) {
  val endPad = if (onCancelClick == null) {
    dimensionResource(CoreUiR.dimen.gutter)
  } else {
    dimensionResource(CoreUiR.dimen.gutter) - 8.dp
  }

  Column(
    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(
        start = dimensionResource(CoreUiR.dimen.gutter),
        end = endPad
      )
    ) {
      LinearProgressIndicator(
        color = progressColor(backupStatusData),
        progress = { backupStatusData.progress ?: 0f },
        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        gapSize = 0.dp,
        drawStopIndicator = {}
      )

      if (onCancelClick != null) {
        IconButton(
          onClick = onCancelClick
        ) {
          Icon(
            painter = painterResource(R.drawable.symbol_x_24),
            contentDescription = stringResource(R.string.BackupStatusRow__cancel_download)
          )
        }
      }
    }

    if (backupStatusData.restoreStatus == RestoreStatus.NOT_ENOUGH_DISK_SPACE) {
      BackupAlertText(
        text = stringResource(R.string.BackupStatusRow__not_enough_space, backupStatusData.remainingRestoreSize)
      )

      Rows.TextRow(
        text = stringResource(R.string.BackupStatusRow__skip_download),
        onClick = onSkipClick
      )
    } else {
      val string = when (restoreType) {
        RestoreType.RESTORE -> getRestoringMediaString(backupStatusData)
        RestoreType.DOWNLOAD -> getDownloadingMediaString(backupStatusData)
      }

      BackupAlertText(text = string)
    }
  }
}

@Composable
private fun BackupAlertText(text: String) {
  Text(
    text = text,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
  )
}

@Composable
private fun getRestoringMediaString(backupStatusData: ArchiveRestoreProgressState): String {
  return when (backupStatusData.restoreState) {
    RestoreState.CALCULATING_MEDIA -> {
      stringResource(
        R.string.BackupStatusRow__restoring_s_of_s_s,
        backupStatusData.completedRestoredSize.toUnitString(2),
        backupStatusData.totalRestoreSize.toUnitString(2),
        "%d".format(((backupStatusData.progress ?: 0f) * 100).roundToInt())
      )
    }
    RestoreState.CANCELING_MEDIA -> stringResource(R.string.BackupStatus__cancel_restore_media)
    RestoreState.RESTORING_MEDIA -> {
      when (backupStatusData.restoreStatus) {
        RestoreStatus.RESTORING -> {
          stringResource(
            R.string.BackupStatusRow__restoring_s_of_s_s,
            backupStatusData.completedRestoredSize.toUnitString(2),
            backupStatusData.totalRestoreSize.toUnitString(2),
            "%d".format(((backupStatusData.progress ?: 0f) * 100).roundToInt())
          )
        }
        RestoreStatus.WAITING_FOR_INTERNET -> stringResource(R.string.BackupStatusRow__restore_no_internet)
        RestoreStatus.WAITING_FOR_WIFI -> stringResource(R.string.BackupStatusRow__restore_waiting_for_wifi)
        RestoreStatus.LOW_BATTERY -> stringResource(R.string.BackupStatusRow__restore_device_has_low_battery)
        RestoreStatus.FINISHED -> stringResource(R.string.BackupStatus__restore_complete)
        else -> throw IllegalStateException()
      }
    }
    RestoreState.NONE -> {
      if (backupStatusData.restoreStatus == RestoreStatus.FINISHED) {
        stringResource(R.string.BackupStatus__restore_complete)
      } else {
        throw IllegalStateException()
      }
    }
    RestoreState.PENDING,
    RestoreState.RESTORING_DB -> throw IllegalStateException()
  }
}

@Composable
private fun getDownloadingMediaString(backupStatusData: ArchiveRestoreProgressState): String {
  return when (backupStatusData.restoreState) {
    RestoreState.CALCULATING_MEDIA -> {
      stringResource(
        R.string.BackupStatusRow__downloading_s_of_s_s,
        backupStatusData.completedRestoredSize.toUnitString(2),
        backupStatusData.totalRestoreSize.toUnitString(2),
        "%d".format(((backupStatusData.progress ?: 0f) * 100).roundToInt())
      )
    }
    RestoreState.CANCELING_MEDIA -> stringResource(R.string.BackupStatus__cancel_restore_media)
    RestoreState.RESTORING_MEDIA -> {
      when (backupStatusData.restoreStatus) {
        RestoreStatus.RESTORING -> {
          stringResource(
            R.string.BackupStatusRow__downloading_s_of_s_s,
            backupStatusData.completedRestoredSize.toUnitString(2),
            backupStatusData.totalRestoreSize.toUnitString(2),
            "%d".format(((backupStatusData.progress ?: 0f) * 100).roundToInt())
          )
        }
        RestoreStatus.WAITING_FOR_INTERNET -> stringResource(R.string.BackupStatusRow__download_no_internet)
        RestoreStatus.WAITING_FOR_WIFI -> stringResource(R.string.BackupStatusRow__download_waiting_for_wifi)
        RestoreStatus.LOW_BATTERY -> stringResource(R.string.BackupStatusRow__download_device_has_low_battery)
        RestoreStatus.FINISHED -> stringResource(R.string.BackupStatus__restore_complete)
        else -> throw IllegalStateException()
      }
    }
    RestoreState.NONE -> {
      if (backupStatusData.restoreStatus == RestoreStatus.FINISHED) {
        stringResource(R.string.BackupStatus__restore_complete)
      } else {
        throw IllegalStateException()
      }
    }
    RestoreState.PENDING,
    RestoreState.RESTORING_DB -> throw IllegalStateException()
  }
}

@Composable
private fun progressColor(backupStatusData: ArchiveRestoreProgressState): Color {
  return when (backupStatusData.restoreStatus) {
    RestoreStatus.RESTORING -> MaterialTheme.colorScheme.primary
    RestoreStatus.WAITING_FOR_INTERNET,
    RestoreStatus.WAITING_FOR_WIFI,
    RestoreStatus.LOW_BATTERY,
    RestoreStatus.NOT_ENOUGH_DISK_SPACE -> BackupsIconColors.Warning.foreground
    RestoreStatus.FINISHED -> BackupsIconColors.Success.foreground
    RestoreStatus.NONE -> BackupsIconColors.Normal.foreground
  }
}

@SignalPreview
@Composable
fun BackupStatusRowNormalPreview() {
  Previews.Preview {
    BackupStatusRow(
      backupStatusData = ArchiveRestoreProgressState(restoreState = RestoreState.RESTORING_MEDIA, restoreStatus = RestoreStatus.RESTORING, remainingRestoreSize = 800.mebiBytes, totalRestoreSize = 1024.mebiBytes),
      onCancelClick = {}
    )
  }
}

@SignalPreview
@Composable
fun BackupStatusRowWaitingForWifiPreview() {
  Previews.Preview {
    BackupStatusRow(
      backupStatusData = ArchiveRestoreProgressState(restoreState = RestoreState.RESTORING_MEDIA, restoreStatus = RestoreStatus.WAITING_FOR_WIFI, remainingRestoreSize = 800.mebiBytes, totalRestoreSize = 1024.mebiBytes)
    )
  }
}

@SignalPreview
@Composable
fun BackupStatusRowWaitingForInternetPreview() {
  Previews.Preview {
    BackupStatusRow(
      backupStatusData = ArchiveRestoreProgressState(restoreState = RestoreState.RESTORING_MEDIA, restoreStatus = RestoreStatus.WAITING_FOR_INTERNET, remainingRestoreSize = 800.mebiBytes, totalRestoreSize = 1024.mebiBytes)
    )
  }
}

@SignalPreview
@Composable
fun BackupStatusRowLowBatteryPreview() {
  Previews.Preview {
    BackupStatusRow(
      backupStatusData = ArchiveRestoreProgressState(restoreState = RestoreState.RESTORING_MEDIA, restoreStatus = RestoreStatus.LOW_BATTERY, remainingRestoreSize = 800.mebiBytes, totalRestoreSize = 1024.mebiBytes)
    )
  }
}

@SignalPreview
@Composable
fun BackupStatusRowFinishedPreview() {
  Previews.Preview {
    BackupStatusRow(
      backupStatusData = ArchiveRestoreProgressState(restoreState = RestoreState.NONE, restoreStatus = RestoreStatus.FINISHED, remainingRestoreSize = 0.mebiBytes, totalRestoreSize = 0.mebiBytes, totalToRestoreThisRun = 1024.mebiBytes),
      onCancelClick = {}
    )
  }
}

@SignalPreview
@Composable
fun BackupStatusRowNotEnoughFreeSpacePreview() {
  Previews.Preview {
    BackupStatusRow(
      backupStatusData = ArchiveRestoreProgressState(restoreState = RestoreState.RESTORING_MEDIA, restoreStatus = RestoreStatus.NOT_ENOUGH_DISK_SPACE, remainingRestoreSize = 500.mebiBytes, totalRestoreSize = 1024.mebiBytes)
    )
  }
}
