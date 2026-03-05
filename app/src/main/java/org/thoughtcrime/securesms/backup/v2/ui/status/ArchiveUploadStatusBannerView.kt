/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.status

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.BackupsIconColors
import org.signal.core.ui.R as CoreUiR

/**
 * Displays a "heads up" widget containing information about the current
 * status of the user's backup.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ArchiveUploadStatusBannerView(
  state: ArchiveUploadStatusBannerViewState,
  emitter: (ArchiveUploadStatusBannerViewEvents) -> Unit = {},
  contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
) {
  val iconRes: Int = when (state) {
    ArchiveUploadStatusBannerViewState.CreatingBackupFile -> R.drawable.symbol_backup_light
    is ArchiveUploadStatusBannerViewState.Uploading -> R.drawable.symbol_backup_light
    is ArchiveUploadStatusBannerViewState.PausedMissingWifi -> R.drawable.symbol_backup_light
    is ArchiveUploadStatusBannerViewState.PausedNoInternet -> R.drawable.symbol_backup_light
    is ArchiveUploadStatusBannerViewState.Finished -> CoreUiR.drawable.symbol_check_circle_24
  }

  val iconColor: Color = when (state) {
    ArchiveUploadStatusBannerViewState.CreatingBackupFile -> BackupsIconColors.Normal.foreground
    is ArchiveUploadStatusBannerViewState.Uploading -> BackupsIconColors.Normal.foreground
    is ArchiveUploadStatusBannerViewState.PausedMissingWifi -> BackupsIconColors.Warning.foreground
    is ArchiveUploadStatusBannerViewState.PausedNoInternet -> BackupsIconColors.Warning.foreground
    is ArchiveUploadStatusBannerViewState.Finished -> BackupsIconColors.Success.foreground
  }

  val title: String = when (state) {
    ArchiveUploadStatusBannerViewState.CreatingBackupFile -> stringResource(R.string.BackupStatus__status_creating_backup)
    is ArchiveUploadStatusBannerViewState.Uploading -> stringResource(R.string.BackupStatus__uploading_backup)
    is ArchiveUploadStatusBannerViewState.PausedMissingWifi -> stringResource(R.string.BackupStatus__upload_paused)
    is ArchiveUploadStatusBannerViewState.PausedNoInternet -> stringResource(R.string.BackupStatus__upload_paused)
    is ArchiveUploadStatusBannerViewState.Finished -> stringResource(R.string.BackupStatus__upload_complete)
  }

  val status: String? = when (state) {
    ArchiveUploadStatusBannerViewState.CreatingBackupFile -> null
    is ArchiveUploadStatusBannerViewState.Uploading -> stringResource(R.string.BackupStatus__status_size_of_size, state.completedSize, state.totalSize)
    is ArchiveUploadStatusBannerViewState.PausedMissingWifi -> stringResource(R.string.BackupStatus__status_waiting_for_wifi)
    is ArchiveUploadStatusBannerViewState.PausedNoInternet -> stringResource(R.string.BackupStatus__status_no_internet)
    is ArchiveUploadStatusBannerViewState.Finished -> state.uploadedSize
  }

  val showIndeterminateProgress: Boolean = when (state) {
    ArchiveUploadStatusBannerViewState.CreatingBackupFile -> true
    else -> false
  }

  val progress: Float? = when (state) {
    is ArchiveUploadStatusBannerViewState.Uploading -> state.progress
    else -> null
  }

  val actionLabelRes: Int? = when (state) {
    is ArchiveUploadStatusBannerViewState.PausedMissingWifi -> R.string.BackupStatus__resume
    else -> null
  }

  val showDismiss: Boolean = when (state) {
    is ArchiveUploadStatusBannerViewState.Finished -> true
    else -> false
  }

  val dropdownAvailable: Boolean = when (state) {
    is ArchiveUploadStatusBannerViewState.Finished -> false
    else -> true
  }

  val menuController = remember { DropdownMenus.MenuController() }

  Box(modifier = Modifier.padding(contentPadding)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .border(1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f), shape = RoundedCornerShape(12.dp))
        .clip(RoundedCornerShape(12.dp))
        .fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .combinedClickable(
          onClick = { emitter(ArchiveUploadStatusBannerViewEvents.BannerClicked) },
          onLongClick = { menuController.show() }
        )
        .padding(12.dp)
    ) {
      Icon(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        tint = iconColor,
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
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier
            .padding(end = 20.dp)
            .align(Alignment.CenterVertically)
        )

        status?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
              .padding(end = 12.dp)
              .align(Alignment.CenterVertically)
          )
        }
      }

      if (showIndeterminateProgress) {
        CircularProgressIndicator(
          strokeWidth = 3.dp,
          strokeCap = StrokeCap.Round,
          modifier = Modifier
            .size(24.dp, 24.dp)
        )
      }

      progress?.let {
        CircularProgressIndicator(
          progress = { it },
          strokeWidth = 3.dp,
          strokeCap = StrokeCap.Round,
          modifier = Modifier
            .size(24.dp, 24.dp)
        )
      }

      if (showDismiss) {
        val interactionSource = remember { MutableInteractionSource() }

        Icon(
          painter = SignalIcons.X.painter,
          contentDescription = stringResource(R.string.Material3SearchToolbar__close),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
            .size(24.dp)
            .clickable(
              interactionSource = interactionSource,
              indication = ripple(bounded = false),
              onClick = { emitter(ArchiveUploadStatusBannerViewEvents.HideClicked) }
            )
        )
      }
    }

    if (dropdownAvailable) {
      Box(modifier = Modifier.align(Alignment.BottomEnd)) {
        DropdownMenus.Menu(
          controller = menuController,
          offsetX = 0.dp,
          offsetY = 10.dp
        ) {
          DropdownMenus.ItemWithIcon(
            menuController = menuController,
            drawableResId = R.drawable.symbol_visible_slash,
            stringResId = R.string.BackupStatus__hide,
            onClick = { emitter(ArchiveUploadStatusBannerViewEvents.HideClicked) }
          )
          DropdownMenus.ItemWithIcon(
            menuController = menuController,
            drawableResId = R.drawable.symbol_x_circle_24,
            stringResId = R.string.BackupStatus__cancel_upload,
            onClick = { emitter(ArchiveUploadStatusBannerViewEvents.CancelClicked) }
          )
        }
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun ArchiveUploadStatusBannerViewPreview() {
  Previews.Preview {
    Column {
      ArchiveUploadStatusBannerView(
        state = ArchiveUploadStatusBannerViewState.CreatingBackupFile
      )

      HorizontalDivider()

      ArchiveUploadStatusBannerView(
        state = ArchiveUploadStatusBannerViewState.Uploading(
          completedSize = "224 MB",
          totalSize = "1 GB",
          progress = 0.22f
        )
      )

      HorizontalDivider()

      ArchiveUploadStatusBannerView(
        state = ArchiveUploadStatusBannerViewState.Uploading(
          completedSize = "0 B",
          totalSize = "1 GB",
          progress = 0f
        )
      )

      HorizontalDivider()

      ArchiveUploadStatusBannerView(
        state = ArchiveUploadStatusBannerViewState.PausedMissingWifi
      )

      HorizontalDivider()

      ArchiveUploadStatusBannerView(
        state = ArchiveUploadStatusBannerViewState.PausedNoInternet
      )

      HorizontalDivider()

      ArchiveUploadStatusBannerView(
        state = ArchiveUploadStatusBannerViewState.Finished(uploadedSize = "1 GB")
      )
    }
  }
}
