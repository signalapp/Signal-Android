/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.shared

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.registration.R

/**
 * Non-dismissable popup showing restore progress: a circular indicator, a phase label, and a byte count.
 * A null [restoreProgress] (or the [RestoreProgress.Phase.Finalizing] phase) renders an indeterminate spinner.
 */
@Composable
fun RestoreProgressDialog(restoreProgress: RestoreProgress?) {
  val context = LocalContext.current

  AlertDialog(
    onDismissRequest = {},
    confirmButton = {},
    dismissButton = {},
    text = {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.wrapContentSize()
        ) {
          if (restoreProgress == null || restoreProgress.phase == RestoreProgress.Phase.Finalizing) {
            CircularProgressIndicator(
              modifier = Modifier
                .padding(top = 55.dp, bottom = 16.dp)
                .width(48.dp)
                .height(48.dp)
            )
          } else {
            CircularProgressIndicator(
              progress = { restoreProgress.progress },
              modifier = Modifier
                .padding(top = 55.dp, bottom = 16.dp)
                .width(48.dp)
                .height(48.dp)
            )
          }

          val progressText = when (restoreProgress?.phase) {
            RestoreProgress.Phase.Downloading -> stringResource(R.string.RemoteRestoreScreen__downloading_backup)
            RestoreProgress.Phase.Restoring -> stringResource(R.string.RemoteRestoreScreen__restoring_messages)
            RestoreProgress.Phase.Finalizing -> stringResource(R.string.RemoteRestoreScreen__finishing_restore)
            null -> stringResource(R.string.RemoteRestoreScreen__restoring)
          }

          Text(
            text = progressText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp)
          )

          if (restoreProgress != null && restoreProgress.phase != RestoreProgress.Phase.Finalizing && restoreProgress.totalBytes > 0) {
            val progressBytes = Formatter.formatShortFileSize(context, restoreProgress.bytesCompleted)
            val totalBytes = Formatter.formatShortFileSize(context, restoreProgress.totalBytes)
            Text(
              text = stringResource(R.string.RemoteRestoreScreen__s_of_s_s, progressBytes, totalBytes, "%.2f%%".format(restoreProgress.progress * 100)),
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(bottom = 12.dp)
            )
          }
        }
      }
    },
    modifier = Modifier.width(212.dp)
  )
}

@AllDevicePreviews
@Composable
private fun RestoreProgressDialogPreview() {
  Previews.Preview {
    RestoreProgressDialog(
      restoreProgress = RestoreProgress(
        phase = RestoreProgress.Phase.Restoring,
        bytesCompleted = 512_000,
        totalBytes = 1_024_000
      )
    )
  }
}

/**
 * Progress of an in-flight backup restore, shared between the remote and local restore flows.
 */
data class RestoreProgress(
  val phase: Phase,
  val bytesCompleted: Long,
  val totalBytes: Long
) {
  val progress: Float
    get() = if (totalBytes > 0) bytesCompleted.toFloat() / totalBytes.toFloat() else 0f

  enum class Phase {
    Downloading,
    Restoring,
    Finalizing
  }
}
