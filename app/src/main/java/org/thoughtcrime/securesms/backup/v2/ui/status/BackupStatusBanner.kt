/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.status

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.mebiBytes
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.RestoreState
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgressState
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgressState.RestoreStatus
import org.thoughtcrime.securesms.backup.v2.ui.BackupsIconColors

private const val NONE = -1

/**
 * Displays a "heads up" widget containing information about the current
 * status of the user's backup.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupStatusBanner(
  data: ArchiveRestoreProgressState,
  onBannerClick: () -> Unit = {},
  onActionClick: (ArchiveRestoreProgressState) -> Unit = {},
  onDismissClick: () -> Unit = {},
  contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
) {
  if (!data.restoreState.isMediaRestoreOperation && data.restoreStatus != RestoreStatus.FINISHED) {
    return
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .padding(contentPadding)
      .border(1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f), shape = RoundedCornerShape(12.dp))
      .fillMaxWidth()
      .defaultMinSize(minHeight = 48.dp)
      .clickable(onClick = onBannerClick)
      .padding(12.dp)
  ) {
    Icon(
      painter = painterResource(id = data.iconResource()),
      contentDescription = null,
      tint = data.iconColor(),
      modifier = Modifier
        .padding(start = 4.dp)
        .size(24.dp)
    )

    FlowRow(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .padding(start = 12.dp)
        .weight(1f)
    ) {
      Text(
        text = data.title(),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
          .padding(end = 20.dp)
          .align(Alignment.CenterVertically)
      )

      data.status()?.let { status ->
        Text(
          text = status,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
            .padding(end = 12.dp)
            .align(Alignment.CenterVertically)
        )
      }
    }

    if (data.restoreState == RestoreState.CALCULATING_MEDIA ||
      data.restoreState == RestoreState.CANCELING_MEDIA ||
      (data.restoreState == RestoreState.RESTORING_MEDIA && data.restoreStatus == RestoreStatus.RESTORING)
    ) {
      CircularProgressIndicator(
        progress = { data.progress!! },
        strokeWidth = 3.dp,
        strokeCap = StrokeCap.Round,
        modifier = Modifier
          .size(24.dp, 24.dp)
      )
    }

    if (data.actionResource() != NONE) {
      Buttons.Small(
        onClick = { onActionClick(data) },
        modifier = Modifier.padding(start = 8.dp)
      ) {
        Text(text = stringResource(id = data.actionResource()))
      }
    }

    if (data.restoreStatus == RestoreStatus.FINISHED) {
      val interactionSource = remember { MutableInteractionSource() }

      Icon(
        painter = painterResource(id = R.drawable.symbol_x_24),
        contentDescription = stringResource(R.string.Material3SearchToolbar__close),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .size(24.dp)
          .clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = false),
            onClick = onDismissClick
          )
      )
    }
  }
}

private fun ArchiveRestoreProgressState.iconResource(): Int {
  return when (this.restoreState) {
    RestoreState.CALCULATING_MEDIA,
    RestoreState.CANCELING_MEDIA -> R.drawable.symbol_backup_light

    RestoreState.RESTORING_MEDIA -> {
      when (this.restoreStatus) {
        RestoreStatus.RESTORING,
        RestoreStatus.WAITING_FOR_INTERNET,
        RestoreStatus.WAITING_FOR_WIFI,
        RestoreStatus.LOW_BATTERY -> R.drawable.symbol_backup_light

        RestoreStatus.NOT_ENOUGH_DISK_SPACE -> R.drawable.symbol_backup_error_24
        RestoreStatus.FINISHED -> R.drawable.symbol_check_circle_24
        RestoreStatus.NONE -> throw IllegalStateException()
      }
    }

    RestoreState.NONE -> {
      if (this.restoreStatus == RestoreStatus.FINISHED) {
        R.drawable.symbol_check_circle_24
      } else {
        throw IllegalStateException()
      }
    }

    RestoreState.PENDING,
    RestoreState.RESTORING_DB -> throw IllegalStateException()
  }
}

@Composable
private fun ArchiveRestoreProgressState.iconColor(): Color {
  return when (this.restoreState) {
    RestoreState.CALCULATING_MEDIA,
    RestoreState.CANCELING_MEDIA -> BackupsIconColors.Normal.foreground

    RestoreState.RESTORING_MEDIA -> {
      when (this.restoreStatus) {
        RestoreStatus.RESTORING -> BackupsIconColors.Normal.foreground
        RestoreStatus.WAITING_FOR_INTERNET,
        RestoreStatus.WAITING_FOR_WIFI,
        RestoreStatus.LOW_BATTERY,
        RestoreStatus.NOT_ENOUGH_DISK_SPACE -> BackupsIconColors.Warning.foreground

        RestoreStatus.FINISHED -> BackupsIconColors.Success.foreground
        RestoreStatus.NONE -> throw IllegalStateException()
      }
    }

    RestoreState.NONE -> {
      if (this.restoreStatus == RestoreStatus.FINISHED) {
        BackupsIconColors.Success.foreground
      } else {
        throw IllegalStateException()
      }
    }

    RestoreState.PENDING,
    RestoreState.RESTORING_DB -> throw IllegalStateException()
  }
}

@Composable
private fun ArchiveRestoreProgressState.title(): String {
  return when (this.restoreState) {
    RestoreState.CALCULATING_MEDIA -> stringResource(R.string.BackupStatus__restoring_media)
    RestoreState.CANCELING_MEDIA -> stringResource(R.string.BackupStatus__cancel_restore_media)
    RestoreState.RESTORING_MEDIA -> {
      when (this.restoreStatus) {
        RestoreStatus.RESTORING -> stringResource(R.string.BackupStatus__restoring_media)
        RestoreStatus.WAITING_FOR_INTERNET,
        RestoreStatus.WAITING_FOR_WIFI,
        RestoreStatus.LOW_BATTERY -> stringResource(R.string.BackupStatus__restore_paused)

        RestoreStatus.NOT_ENOUGH_DISK_SPACE -> {
          stringResource(R.string.BackupStatus__free_up_s_of_space_to_download_your_media, this.remainingRestoreSize.toUnitString())
        }

        RestoreStatus.FINISHED -> stringResource(R.string.BackupStatus__restore_complete)
        RestoreStatus.NONE -> throw IllegalStateException()
      }
    }

    RestoreState.NONE -> {
      if (this.restoreStatus == RestoreStatus.FINISHED) {
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
private fun ArchiveRestoreProgressState.status(): String? {
  return when (this.restoreState) {
    RestoreState.CALCULATING_MEDIA -> {
      stringResource(
        R.string.BackupStatus__status_size_of_size,
        this.completedRestoredSize.toUnitString(),
        this.totalRestoreSize.toUnitString()
      )
    }

    RestoreState.CANCELING_MEDIA -> null
    RestoreState.RESTORING_MEDIA -> {
      when (this.restoreStatus) {
        RestoreStatus.RESTORING -> {
          stringResource(
            R.string.BackupStatus__status_size_of_size,
            this.completedRestoredSize.toUnitString(),
            this.totalRestoreSize.toUnitString()
          )
        }

        RestoreStatus.WAITING_FOR_INTERNET -> stringResource(R.string.BackupStatus__status_no_internet)
        RestoreStatus.WAITING_FOR_WIFI -> stringResource(R.string.BackupStatus__status_waiting_for_wifi)
        RestoreStatus.LOW_BATTERY -> stringResource(R.string.BackupStatus__status_device_has_low_battery)
        RestoreStatus.NOT_ENOUGH_DISK_SPACE -> null
        RestoreStatus.FINISHED -> this.totalToRestoreThisRun.toUnitString()
        RestoreStatus.NONE -> throw IllegalStateException()
      }
    }

    RestoreState.NONE -> {
      if (this.restoreStatus == RestoreStatus.FINISHED) {
        this.totalToRestoreThisRun.toUnitString()
      } else {
        throw IllegalStateException()
      }
    }

    RestoreState.PENDING,
    RestoreState.RESTORING_DB -> throw IllegalStateException()
  }
}

private fun ArchiveRestoreProgressState.actionResource(): Int {
  return when (this.restoreState) {
    RestoreState.CALCULATING_MEDIA,
    RestoreState.CANCELING_MEDIA -> NONE

    RestoreState.RESTORING_MEDIA -> {
      when (this.restoreStatus) {
        RestoreStatus.NOT_ENOUGH_DISK_SPACE -> R.string.BackupStatus__details
        RestoreStatus.WAITING_FOR_WIFI -> R.string.BackupStatus__resume
        else -> NONE
      }
    }

    else -> NONE
  }
}

@DayNightPreviews
@Composable
fun BackupStatusBannerPreview() {
  Previews.Preview {
    Column {
      BackupStatusBanner(
        data = ArchiveRestoreProgressState(restoreState = RestoreState.RESTORING_MEDIA, restoreStatus = RestoreStatus.RESTORING, remainingRestoreSize = 800.mebiBytes, totalRestoreSize = 1024.mebiBytes)
      )

      HorizontalDivider()

      BackupStatusBanner(
        data = ArchiveRestoreProgressState(restoreState = RestoreState.CALCULATING_MEDIA, restoreStatus = RestoreStatus.RESTORING, remainingRestoreSize = 1024.mebiBytes, totalRestoreSize = 1024.mebiBytes)
      )

      HorizontalDivider()

      BackupStatusBanner(
        data = ArchiveRestoreProgressState(restoreState = RestoreState.CANCELING_MEDIA, restoreStatus = RestoreStatus.RESTORING, remainingRestoreSize = 200.mebiBytes, totalRestoreSize = 1024.mebiBytes)
      )

      HorizontalDivider()

      BackupStatusBanner(
        data = ArchiveRestoreProgressState(restoreState = RestoreState.RESTORING_MEDIA, restoreStatus = RestoreStatus.WAITING_FOR_WIFI, remainingRestoreSize = 800.mebiBytes, totalRestoreSize = 1024.mebiBytes)
      )

      HorizontalDivider()

      BackupStatusBanner(
        data = ArchiveRestoreProgressState(restoreState = RestoreState.RESTORING_MEDIA, restoreStatus = RestoreStatus.WAITING_FOR_INTERNET, remainingRestoreSize = 800.mebiBytes, totalRestoreSize = 1024.mebiBytes)
      )

      HorizontalDivider()

      BackupStatusBanner(
        data = ArchiveRestoreProgressState(restoreState = RestoreState.NONE, restoreStatus = RestoreStatus.FINISHED, remainingRestoreSize = 0.mebiBytes, totalRestoreSize = 0.mebiBytes, totalToRestoreThisRun = 1024.mebiBytes)
      )

      HorizontalDivider()

      BackupStatusBanner(
        data = ArchiveRestoreProgressState(restoreState = RestoreState.RESTORING_MEDIA, restoreStatus = RestoreStatus.NOT_ENOUGH_DISK_SPACE, remainingRestoreSize = 500.mebiBytes, totalRestoreSize = 1024.mebiBytes)
      )
    }
  }
}
