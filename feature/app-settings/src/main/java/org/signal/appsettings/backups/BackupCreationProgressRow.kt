/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.backups

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.R as CoreUiR

@Composable
fun BackupCreationProgressRow(
  progress: BackupCreationProgress,
  isRemote: Boolean,
  modifier: Modifier = Modifier,
  onCancel: (() -> Unit)? = null
) {
  Row(
    modifier = modifier
      .padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter))
      .padding(top = 16.dp, bottom = 14.dp)
  ) {
    Column(
      modifier = Modifier.weight(1f)
    ) {
      BackupCreationProgressIndicator(progress = progress, onCancel = onCancel)

      Text(
        text = getProgressMessage(progress, isRemote),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun BackupCreationProgressIndicator(
  progress: BackupCreationProgress,
  onCancel: (() -> Unit)? = null
) {
  val fraction = when (progress) {
    is BackupCreationProgress.Exporting -> progress.fraction
    is BackupCreationProgress.Transferring -> progress.fraction
    else -> 0f
  }

  val hasDeterminateProgress = when (progress) {
    is BackupCreationProgress.Exporting -> progress.frameTotalCount > 0 && (progress.phase == BackupCreationProgress.ExportPhase.MESSAGE || progress.phase == BackupCreationProgress.ExportPhase.INITIALIZING || progress.phase == BackupCreationProgress.ExportPhase.FINALIZING)
    is BackupCreationProgress.Transferring -> progress.total > 0
    else -> false
  }

  Row(
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (hasDeterminateProgress) {
      val animatedProgress by animateFloatAsState(targetValue = fraction, animationSpec = tween(durationMillis = 250))
      LinearProgressIndicator(
        trackColor = MaterialTheme.colorScheme.secondaryContainer,
        progress = { animatedProgress },
        drawStopIndicator = {},
        modifier = Modifier
          .weight(1f)
          .padding(vertical = 12.dp)
      )
    } else {
      LinearProgressIndicator(
        trackColor = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
          .weight(1f)
          .padding(vertical = 12.dp)
      )
    }

    if (onCancel != null) {
      IconButton(onClick = onCancel) {
        Icon(
          imageVector = SignalIcons.X.imageVector,
          contentDescription = "Cancel"
        )
      }
    }
  }
}

@Composable
private fun getProgressMessage(progress: BackupCreationProgress, isRemote: Boolean): String {
  return when (progress) {
    is BackupCreationProgress.Exporting -> getExportPhaseMessage(progress)
    is BackupCreationProgress.Transferring -> getTransferPhaseMessage(progress, isRemote)
    else -> stringResource(R.string.BackupCreationProgressRow__processing_backup)
  }
}

@Composable
private fun getExportPhaseMessage(exporting: BackupCreationProgress.Exporting): String {
  return when (exporting.phase) {
    BackupCreationProgress.ExportPhase.MESSAGE -> {
      if (exporting.frameTotalCount > 0) {
        stringResource(
          R.string.BackupCreationProgressRow__processing_messages_s_of_s_d,
          "%,d".format(exporting.frameExportCount),
          "%,d".format(exporting.frameTotalCount),
          (exporting.fraction * 100).toInt()
        )
      } else {
        stringResource(R.string.BackupCreationProgressRow__processing_messages)
      }
    }
    BackupCreationProgress.ExportPhase.NONE -> stringResource(R.string.BackupCreationProgressRow__processing_backup)
    BackupCreationProgress.ExportPhase.FINALIZING -> stringResource(R.string.BackupCreationProgressRow__finalizing)
    else -> stringResource(R.string.BackupCreationProgressRow__preparing_backup)
  }
}

@Composable
private fun getTransferPhaseMessage(transferring: BackupCreationProgress.Transferring, isRemote: Boolean): String {
  val percent = if (transferring.total == 0L) 0 else (transferring.completed * 100 / transferring.total).toInt()
  return if (isRemote) {
    stringResource(R.string.BackupCreationProgressRow__uploading_media_d, percent)
  } else {
    stringResource(R.string.BackupCreationProgressRow__exporting_media_d, percent)
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ExportingIndeterminatePreview() {
  BackupCreationProgressRow(
    progress = BackupCreationProgress.Exporting(phase = BackupCreationProgress.ExportPhase.NONE, frameExportCount = 0, frameTotalCount = 0),
    isRemote = false
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun InitializingIndeterminatePreview() {
  BackupCreationProgressRow(
    progress = BackupCreationProgress.Exporting(phase = BackupCreationProgress.ExportPhase.INITIALIZING, frameExportCount = 0, frameTotalCount = 0),
    isRemote = false
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun InitializingDeterminatePreview() {
  BackupCreationProgressRow(
    progress = BackupCreationProgress.Exporting(
      phase = BackupCreationProgress.ExportPhase.INITIALIZING,
      frameExportCount = 128,
      frameTotalCount = 256
    ),
    isRemote = false
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ExportingMessagesPreview() {
  BackupCreationProgressRow(
    progress = BackupCreationProgress.Exporting(
      phase = BackupCreationProgress.ExportPhase.MESSAGE,
      frameExportCount = 1000,
      frameTotalCount = 100_000
    ),
    isRemote = false
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun TransferringLocalPreview() {
  BackupCreationProgressRow(
    progress = BackupCreationProgress.Transferring(completed = 50, total = 200),
    isRemote = false
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun TransferringRemotePreview() {
  BackupCreationProgressRow(
    progress = BackupCreationProgress.Transferring(completed = 50, total = 200),
    isRemote = true,
    onCancel = {}
  )
}
