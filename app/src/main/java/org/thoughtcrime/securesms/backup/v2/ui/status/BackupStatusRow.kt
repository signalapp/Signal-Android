/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
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
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.Previews
import org.signal.core.ui.Rows
import org.signal.core.ui.SignalPreview
import org.signal.core.util.ByteSize
import org.thoughtcrime.securesms.R
import kotlin.math.roundToInt

/**
 * Backup status displayable as a row on a settings page.
 */
@Composable
fun BackupStatusRow(
  backupStatusData: BackupStatusData,
  onSkipClick: () -> Unit = {},
  onCancelClick: () -> Unit = {}
) {
  Column {
    if (backupStatusData !is BackupStatusData.CouldNotCompleteBackup) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.core_ui__gutter))
      ) {
        LinearProgressIndicator(
          color = progressColor(backupStatusData),
          progress = { backupStatusData.progress },
          modifier = Modifier.weight(1f)
        )

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

    when (backupStatusData) {
      is BackupStatusData.RestoringMedia -> {
        Text(
          text = getRestoringMediaString(backupStatusData),
          modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.core_ui__gutter))
        )
      }

      is BackupStatusData.NotEnoughFreeSpace -> {
        Text(
          text = stringResource(
            R.string.BackupStatusRow__not_enough_space,
            backupStatusData.requiredSpace,
            "%d".format((backupStatusData.progress * 100).roundToInt())
          ),
          modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.core_ui__gutter))
        )

        Rows.TextRow(
          text = stringResource(R.string.BackupStatusRow__skip_download),
          onClick = onSkipClick
        )
      }

      BackupStatusData.CouldNotCompleteBackup -> {
        val inlineContentMap = mapOf(
          "yellow_bullet" to InlineTextContent(
            Placeholder(12.sp, 12.sp, PlaceholderVerticalAlign.TextCenter)
          ) {
            Box(
              modifier = Modifier
                .size(12.dp)
                .background(color = backupStatusData.iconColors.foreground, shape = CircleShape)
            )
          }
        )

        Text(
          text = buildAnnotatedString {
            appendInlineContent("yellow_bullet")
            append(" ")
            append(stringResource(R.string.BackupStatusRow__your_last_backup))
          },
          inlineContent = inlineContentMap,
          modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.core_ui__gutter))
        )
      }
    }
  }
}

@Composable
private fun getRestoringMediaString(backupStatusData: BackupStatusData.RestoringMedia): String {
  return when (backupStatusData.restoreStatus) {
    BackupStatusData.RestoreStatus.NORMAL -> {
      stringResource(
        R.string.BackupStatusRow__downloading_s_of_s_s,
        backupStatusData.bytesDownloaded.toUnitString(2),
        backupStatusData.bytesTotal.toUnitString(2),
        "%d".format((backupStatusData.progress * 100).roundToInt())
      )
    }
    BackupStatusData.RestoreStatus.LOW_BATTERY -> stringResource(R.string.BackupStatus__status_device_has_low_battery)
    BackupStatusData.RestoreStatus.WAITING_FOR_INTERNET -> stringResource(R.string.BackupStatus__status_no_internet)
    BackupStatusData.RestoreStatus.WAITING_FOR_WIFI -> stringResource(R.string.BackupStatus__status_waiting_for_wifi)
    BackupStatusData.RestoreStatus.FINISHED -> stringResource(R.string.BackupStatus__restore_complete)
  }
}

@Composable
private fun progressColor(backupStatusData: BackupStatusData): Color {
  return when (backupStatusData) {
    is BackupStatusData.RestoringMedia -> MaterialTheme.colorScheme.primary
    else -> backupStatusData.iconColors.foreground
  }
}

@SignalPreview
@Composable
fun BackupStatusRowNormalPreview() {
  Previews.Preview {
    BackupStatusRow(
      backupStatusData = BackupStatusData.RestoringMedia(
        bytesTotal = ByteSize(100),
        bytesDownloaded = ByteSize(50),
        restoreStatus = BackupStatusData.RestoreStatus.NORMAL
      )
    )
  }
}

@SignalPreview
@Composable
fun BackupStatusRowWaitingForWifiPreview() {
  Previews.Preview {
    BackupStatusRow(
      backupStatusData = BackupStatusData.RestoringMedia(
        bytesTotal = ByteSize(100),
        bytesDownloaded = ByteSize(50),
        restoreStatus = BackupStatusData.RestoreStatus.WAITING_FOR_WIFI
      )
    )
  }
}

@SignalPreview
@Composable
fun BackupStatusRowWaitingForInternetPreview() {
  Previews.Preview {
    BackupStatusRow(
      backupStatusData = BackupStatusData.RestoringMedia(
        bytesTotal = ByteSize(100),
        bytesDownloaded = ByteSize(50),
        restoreStatus = BackupStatusData.RestoreStatus.WAITING_FOR_INTERNET
      )
    )
  }
}

@SignalPreview
@Composable
fun BackupStatusRowLowBatteryPreview() {
  Previews.Preview {
    BackupStatusRow(
      backupStatusData = BackupStatusData.RestoringMedia(
        bytesTotal = ByteSize(100),
        bytesDownloaded = ByteSize(50),
        restoreStatus = BackupStatusData.RestoreStatus.LOW_BATTERY
      )
    )
  }
}

@SignalPreview
@Composable
fun BackupStatusRowFinishedPreview() {
  Previews.Preview {
    BackupStatusRow(
      backupStatusData = BackupStatusData.RestoringMedia(
        bytesTotal = ByteSize(100),
        bytesDownloaded = ByteSize(50),
        restoreStatus = BackupStatusData.RestoreStatus.FINISHED
      )
    )
  }
}

@SignalPreview
@Composable
fun BackupStatusRowNotEnoughFreeSpacePreview() {
  Previews.Preview {
    BackupStatusRow(
      backupStatusData = BackupStatusData.NotEnoughFreeSpace(
        requiredSpace = ByteSize(50)
      )
    )
  }
}

@SignalPreview
@Composable
fun BackupStatusRowCouldNotCompleteBackupPreview() {
  Previews.Preview {
    BackupStatusRow(
      backupStatusData = BackupStatusData.CouldNotCompleteBackup
    )
  }
}
