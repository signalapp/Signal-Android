/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.status

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.Buttons
import org.signal.core.ui.Icons
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.BackupsIconColors
import kotlin.math.max
import kotlin.math.min

private const val NONE = -1

/**
 * Displays a "heads up" widget containing information about the current
 * status of the user's backup.
 */
@Composable
fun BackupStatus(
  data: BackupStatusData,
  onActionClick: () -> Unit = {}
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .border(1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f), shape = RoundedCornerShape(12.dp))
      .fillMaxWidth()
      .padding(14.dp)
  ) {
    val foreground: Brush = data.iconColors.foreground
    Icons.BrushedForeground(
      painter = painterResource(id = data.iconRes),
      contentDescription = null,
      foregroundBrush = foreground,
      modifier = Modifier
        .background(color = data.iconColors.background, shape = CircleShape)
        .padding(8.dp)
    )

    Column(
      modifier = Modifier
        .padding(start = 12.dp)
        .weight(1f)
    ) {
      Text(
        text = data.title,
        style = MaterialTheme.typography.bodyMedium
      )

      if (data.progress >= 0f) {
        LinearProgressIndicator(
          progress = { data.progress },
          strokeCap = StrokeCap.Round,
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
        )
      }

      if (data.statusRes != NONE) {
        Text(
          text = stringResource(id = data.statusRes),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    if (data.actionRes != NONE) {
      Buttons.Small(
        onClick = onActionClick,
        modifier = Modifier.padding(start = 8.dp)
      ) {
        Text(text = stringResource(id = data.actionRes))
      }
    }
  }
}

@SignalPreview
@Composable
fun BackupStatusPreview() {
  Previews.Preview {
    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      BackupStatus(
        data = BackupStatusData.CouldNotCompleteBackup
      )

      BackupStatus(
        data = BackupStatusData.NotEnoughFreeSpace("12 GB")
      )

      BackupStatus(
        data = BackupStatusData.RestoringMedia(50, 100)
      )
    }
  }
}

/**
 * Sealed interface describing status data to display in BackupStatus widget.
 *
 * TODO [message-requests] - Finalize assets and text
 */
sealed interface BackupStatusData {

  @get:DrawableRes
  val iconRes: Int

  @get:Composable
  val title: String

  val iconColors: BackupsIconColors

  @get:StringRes
  val actionRes: Int get() = NONE

  @get:StringRes
  val statusRes: Int get() = NONE

  val progress: Float get() = NONE.toFloat()

  /**
   * Generic failure
   */
  data object CouldNotCompleteBackup : BackupStatusData {
    override val iconRes: Int = R.drawable.symbol_backup_light

    override val title: String
      @Composable
      get() = stringResource(R.string.default_error_msg)

    override val iconColors: BackupsIconColors = BackupsIconColors.Warning
  }

  /**
   * User does not have enough space on their device to complete backup restoration
   */
  class NotEnoughFreeSpace(
    private val requiredSpace: String
  ) : BackupStatusData {
    override val iconRes: Int = R.drawable.symbol_backup_light

    override val title: String
      @Composable
      get() = stringResource(R.string.BackupStatus__free_up_s_of_space_to_download_your_media, requiredSpace)

    override val iconColors: BackupsIconColors = BackupsIconColors.Warning
    override val actionRes: Int = R.string.registration_activity__skip
  }

  /**
   * Restoring media, finished, and paused states.
   */
  data class RestoringMedia(
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val status: Status = Status.NONE
  ) : BackupStatusData {
    override val iconRes: Int = R.drawable.symbol_backup_light
    override val iconColors: BackupsIconColors = BackupsIconColors.Normal

    override val title: String
      @Composable get() = stringResource(
        when (status) {
          Status.NONE -> R.string.default_error_msg
          Status.LOW_BATTERY -> R.string.default_error_msg
          Status.WAITING_FOR_INTERNET -> R.string.default_error_msg
          Status.WAITING_FOR_WIFI -> R.string.default_error_msg
          Status.FINISHED -> R.string.default_error_msg
        }
      )

    override val statusRes: Int = when (status) {
      Status.NONE -> R.string.default_error_msg
      Status.LOW_BATTERY -> R.string.default_error_msg
      Status.WAITING_FOR_INTERNET -> R.string.default_error_msg
      Status.WAITING_FOR_WIFI -> R.string.default_error_msg
      Status.FINISHED -> R.string.default_error_msg
    }

    override val progress: Float = if (bytesTotal > 0) {
      min(1f, max(0f, bytesDownloaded.toFloat() / bytesTotal))
    } else {
      0f
    }
  }

  /**
   * Describes the status of an in-progress media download session.
   */
  enum class Status {
    NONE,
    LOW_BATTERY,
    WAITING_FOR_INTERNET,
    WAITING_FOR_WIFI,
    FINISHED
  }
}
