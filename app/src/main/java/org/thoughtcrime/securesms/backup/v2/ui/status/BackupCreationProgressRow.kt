/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.status

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
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.exportProgress
import org.thoughtcrime.securesms.backup.transferProgress
import org.thoughtcrime.securesms.keyvalue.protos.LocalBackupCreationProgress
import org.signal.core.ui.R as CoreUiR

@Composable
fun BackupCreationProgressRow(
  progress: LocalBackupCreationProgress,
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
  progress: LocalBackupCreationProgress,
  onCancel: (() -> Unit)? = null
) {
  val exporting = progress.exporting
  val transferring = progress.transferring

  val fraction = when {
    exporting != null -> progress.exportProgress()
    transferring != null -> progress.transferProgress()
    else -> 0f
  }

  val hasDeterminateProgress = when {
    exporting != null -> exporting.frameTotalCount > 0 && (exporting.phase == LocalBackupCreationProgress.ExportPhase.MESSAGE || exporting.phase == LocalBackupCreationProgress.ExportPhase.INITIALIZING || exporting.phase == LocalBackupCreationProgress.ExportPhase.FINALIZING)
    transferring != null -> transferring.total > 0
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
private fun getProgressMessage(progress: LocalBackupCreationProgress, isRemote: Boolean): String {
  val exporting = progress.exporting
  val transferring = progress.transferring

  return when {
    exporting != null -> getExportPhaseMessage(exporting, progress)
    transferring != null -> getTransferPhaseMessage(transferring, isRemote)
    else -> stringResource(R.string.BackupCreationProgressRow__processing_backup)
  }
}

@Composable
private fun getExportPhaseMessage(exporting: LocalBackupCreationProgress.Exporting, progress: LocalBackupCreationProgress): String {
  return when (exporting.phase) {
    LocalBackupCreationProgress.ExportPhase.MESSAGE -> {
      if (exporting.frameTotalCount > 0) {
        stringResource(
          R.string.BackupCreationProgressRow__processing_messages_s_of_s_d,
          "%,d".format(exporting.frameExportCount),
          "%,d".format(exporting.frameTotalCount),
          (progress.exportProgress() * 100).toInt()
        )
      } else {
        stringResource(R.string.BackupCreationProgressRow__processing_messages)
      }
    }
    LocalBackupCreationProgress.ExportPhase.NONE -> stringResource(R.string.BackupCreationProgressRow__processing_backup)
    LocalBackupCreationProgress.ExportPhase.FINALIZING -> stringResource(R.string.BackupCreationProgressRow__finalizing)
    else -> stringResource(R.string.BackupCreationProgressRow__preparing_backup)
  }
}

@Composable
private fun getTransferPhaseMessage(transferring: LocalBackupCreationProgress.Transferring, isRemote: Boolean): String {
  val percent = if (transferring.total == 0L) 0 else (transferring.completed * 100 / transferring.total).toInt()
  return if (isRemote) {
    stringResource(R.string.BackupCreationProgressRow__uploading_media_d, percent)
  } else {
    stringResource(R.string.BackupCreationProgressRow__exporting_media_d, percent)
  }
}

@DayNightPreviews
@Composable
private fun ExportingIndeterminatePreview() {
  Previews.Preview {
    BackupCreationProgressRow(
      progress = LocalBackupCreationProgress(exporting = LocalBackupCreationProgress.Exporting(phase = LocalBackupCreationProgress.ExportPhase.NONE)),
      isRemote = false
    )
  }
}

@DayNightPreviews
@Composable
private fun InitializingIndeterminatePreview() {
  Previews.Preview {
    BackupCreationProgressRow(
      progress = LocalBackupCreationProgress(exporting = LocalBackupCreationProgress.Exporting(phase = LocalBackupCreationProgress.ExportPhase.INITIALIZING)),
      isRemote = false
    )
  }
}

@DayNightPreviews
@Composable
private fun InitializingDeterminatePreview() {
  Previews.Preview {
    BackupCreationProgressRow(
      progress = LocalBackupCreationProgress(
        exporting = LocalBackupCreationProgress.Exporting(
          phase = LocalBackupCreationProgress.ExportPhase.INITIALIZING,
          frameExportCount = 128,
          frameTotalCount = 256
        )
      ),
      isRemote = false
    )
  }
}

@DayNightPreviews
@Composable
private fun ExportingMessagesPreview() {
  Previews.Preview {
    BackupCreationProgressRow(
      progress = LocalBackupCreationProgress(
        exporting = LocalBackupCreationProgress.Exporting(
          phase = LocalBackupCreationProgress.ExportPhase.MESSAGE,
          frameExportCount = 1000,
          frameTotalCount = 100_000
        )
      ),
      isRemote = false
    )
  }
}

@DayNightPreviews
@Composable
private fun TransferringLocalPreview() {
  Previews.Preview {
    BackupCreationProgressRow(
      progress = LocalBackupCreationProgress(
        transferring = LocalBackupCreationProgress.Transferring(
          completed = 50,
          total = 200,
          mediaPhase = true
        )
      ),
      isRemote = false
    )
  }
}

@DayNightPreviews
@Composable
private fun TransferringRemotePreview() {
  Previews.Preview {
    BackupCreationProgressRow(
      progress = LocalBackupCreationProgress(
        transferring = LocalBackupCreationProgress.Transferring(
          completed = 50,
          total = 200,
          mediaPhase = true
        )
      ),
      isRemote = true,
      onCancel = {}
    )
  }
}
