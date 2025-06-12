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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.util.ByteSize
import org.thoughtcrime.securesms.R
import kotlin.math.roundToInt
import org.signal.core.ui.R as CoreUiR

private val YELLOW_DOT = Color(0xFFFFCC00)

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
  backupStatusData: BackupStatusData,
  restoreType: RestoreType = RestoreType.RESTORE,
  onSkipClick: () -> Unit = {},
  onCancelClick: (() -> Unit)? = null,
  onLearnMoreClick: () -> Unit = {}
) {
  val endPad = if (onCancelClick == null) {
    dimensionResource(CoreUiR.dimen.gutter)
  } else {
    dimensionResource(CoreUiR.dimen.gutter) - 8.dp
  }

  Column(
    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
  ) {
    if (backupStatusData !is BackupStatusData.CouldNotCompleteBackup &&
      backupStatusData !is BackupStatusData.BackupFailed
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
          progress = { backupStatusData.progress },
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
    }

    when (backupStatusData) {
      is BackupStatusData.RestoringMedia -> {
        val string = when (restoreType) {
          RestoreType.RESTORE -> getRestoringMediaString(backupStatusData)
          RestoreType.DOWNLOAD -> getDownloadingMediaString(backupStatusData)
        }

        BackupAlertText(text = string)
      }

      is BackupStatusData.NotEnoughFreeSpace -> {
        BackupAlertText(
          text = stringResource(
            R.string.BackupStatusRow__not_enough_space,
            backupStatusData.requiredSpace,
            "%d".format((backupStatusData.progress * 100).roundToInt())
          )
        )

        Rows.TextRow(
          text = stringResource(R.string.BackupStatusRow__skip_download),
          onClick = onSkipClick
        )
      }

      BackupStatusData.CouldNotCompleteBackup -> {
        val inlineContentMap = mapOf(
          "yellow_bullet" to InlineTextContent(
            Placeholder(20.sp, 12.sp, PlaceholderVerticalAlign.TextCenter)
          ) {
            Box(
              modifier = Modifier
                .size(12.dp)
                .background(color = YELLOW_DOT, shape = CircleShape)
            )
          }
        )

        BackupAlertText(
          text = buildAnnotatedString {
            appendInlineContent("yellow_bullet")
            append(" ")
            append(stringResource(R.string.BackupStatusRow__your_last_backup))
          },
          inlineContent = inlineContentMap
        )
      }
      BackupStatusData.BackupFailed -> {
        val inlineContentMap = mapOf(
          "yellow_bullet" to InlineTextContent(
            Placeholder(20.sp, 12.sp, PlaceholderVerticalAlign.TextCenter)
          ) {
            Box(
              modifier = Modifier
                .size(12.dp)
                .background(color = YELLOW_DOT, shape = CircleShape)
            )
          }
        )

        BackupAlertText(
          text = buildAnnotatedString {
            appendInlineContent("yellow_bullet")
            append(" ")
            append(stringResource(R.string.BackupStatusRow__your_last_backup_latest_version))
            append(" ")
            withLink(
              LinkAnnotation.Clickable(
                stringResource(R.string.BackupStatusRow__learn_more),
                styles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary))
              ) {
                onLearnMoreClick()
              }
            ) {
              append(stringResource(R.string.BackupStatusRow__learn_more))
            }
          },
          inlineContent = inlineContentMap
        )
      }
    }
  }
}

@Composable
private fun BackupAlertText(text: String) {
  BackupAlertText(
    text = remember(text) { AnnotatedString(text) },
    inlineContent = emptyMap()
  )
}

@Composable
private fun BackupAlertText(text: AnnotatedString, inlineContent: Map<String, InlineTextContent>) {
  Text(
    text = text,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.padding(horizontal = dimensionResource(CoreUiR.dimen.gutter)),
    inlineContent = inlineContent
  )
}

@Composable
private fun getRestoringMediaString(backupStatusData: BackupStatusData.RestoringMedia): String {
  return when (backupStatusData.restoreStatus) {
    BackupStatusData.RestoreStatus.NORMAL -> {
      stringResource(
        R.string.BackupStatusRow__restoring_s_of_s_s,
        backupStatusData.bytesDownloaded.toUnitString(2),
        backupStatusData.bytesTotal.toUnitString(2),
        "%d".format((backupStatusData.progress * 100).roundToInt())
      )
    }
    BackupStatusData.RestoreStatus.LOW_BATTERY -> stringResource(R.string.BackupStatusRow__restore_device_has_low_battery)
    BackupStatusData.RestoreStatus.WAITING_FOR_INTERNET -> stringResource(R.string.BackupStatusRow__restore_no_internet)
    BackupStatusData.RestoreStatus.WAITING_FOR_WIFI -> stringResource(R.string.BackupStatusRow__restore_waiting_for_wifi)
    BackupStatusData.RestoreStatus.FINISHED -> stringResource(R.string.BackupStatus__restore_complete)
  }
}

@Composable
private fun getDownloadingMediaString(backupStatusData: BackupStatusData.RestoringMedia): String {
  return when (backupStatusData.restoreStatus) {
    BackupStatusData.RestoreStatus.NORMAL -> {
      stringResource(
        R.string.BackupStatusRow__downloading_s_of_s_s,
        backupStatusData.bytesDownloaded.toUnitString(2),
        backupStatusData.bytesTotal.toUnitString(2),
        "%d".format((backupStatusData.progress * 100).roundToInt())
      )
    }
    BackupStatusData.RestoreStatus.LOW_BATTERY -> stringResource(R.string.BackupStatusRow__download_device_has_low_battery)
    BackupStatusData.RestoreStatus.WAITING_FOR_INTERNET -> stringResource(R.string.BackupStatusRow__download_no_internet)
    BackupStatusData.RestoreStatus.WAITING_FOR_WIFI -> stringResource(R.string.BackupStatusRow__download_waiting_for_wifi)
    BackupStatusData.RestoreStatus.FINISHED -> stringResource(R.string.BackupStatus__restore_complete)
  }
}

@Composable
private fun progressColor(backupStatusData: BackupStatusData): Color {
  return if (backupStatusData is BackupStatusData.RestoringMedia && backupStatusData.restoreStatus == BackupStatusData.RestoreStatus.NORMAL) {
    MaterialTheme.colorScheme.primary
  } else {
    backupStatusData.iconColors.foreground
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
      ),
      onCancelClick = {}
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

@SignalPreview
@Composable
fun BackupStatusRowBackupFailedPreview() {
  Previews.Preview {
    BackupStatusRow(
      backupStatusData = BackupStatusData.BackupFailed
    )
  }
}
