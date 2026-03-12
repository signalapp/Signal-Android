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
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.BackupCreationProgress
import org.signal.core.ui.R as CoreUiR

@Composable
fun BackupCreationProgressRow(
  progress: BackupCreationProgress,
  isRemote: Boolean,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter))
      .padding(top = 16.dp, bottom = 14.dp)
  ) {
    Column(
      modifier = Modifier.weight(1f)
    ) {
      BackupCreationProgressIndicator(progress = progress)

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
  progress: BackupCreationProgress
) {
  val fraction = when (progress) {
    is BackupCreationProgress.Exporting -> progress.exportProgress()
    is BackupCreationProgress.Transferring -> progress.transferProgress()
    else -> 0f
  }

  val hasDeterminateProgress = when (progress) {
    is BackupCreationProgress.Exporting -> progress.frameTotalCount > 0 && progress.phase == BackupCreationProgress.ExportPhase.MESSAGE
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
private fun getExportPhaseMessage(progress: BackupCreationProgress.Exporting): String {
  return when (progress.phase) {
    BackupCreationProgress.ExportPhase.MESSAGE -> {
      if (progress.frameTotalCount > 0) {
        stringResource(
          R.string.BackupCreationProgressRow__processing_messages_s_of_s_d,
          "%,d".format(progress.frameExportCount),
          "%,d".format(progress.frameTotalCount),
          (progress.exportProgress() * 100).toInt()
        )
      } else {
        stringResource(R.string.BackupCreationProgressRow__processing_messages)
      }
    }
    BackupCreationProgress.ExportPhase.NONE -> stringResource(R.string.BackupCreationProgressRow__processing_backup)
    else -> stringResource(R.string.BackupCreationProgressRow__preparing_backup)
  }
}

@Composable
private fun getTransferPhaseMessage(progress: BackupCreationProgress.Transferring, isRemote: Boolean): String {
  val percent = (progress.transferProgress() * 100).toInt()
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
      progress = BackupCreationProgress.Exporting(phase = BackupCreationProgress.ExportPhase.NONE),
      isRemote = false
    )
  }
}

@DayNightPreviews
@Composable
private fun ExportingMessagesPreview() {
  Previews.Preview {
    BackupCreationProgressRow(
      progress = BackupCreationProgress.Exporting(
        phase = BackupCreationProgress.ExportPhase.MESSAGE,
        frameExportCount = 1000,
        frameTotalCount = 100_000
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
      progress = BackupCreationProgress.Transferring(
        completed = 50,
        total = 200,
        mediaPhase = true
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
      progress = BackupCreationProgress.Transferring(
        completed = 50,
        total = 200,
        mediaPhase = true
      ),
      isRemote = true
    )
  }
}
