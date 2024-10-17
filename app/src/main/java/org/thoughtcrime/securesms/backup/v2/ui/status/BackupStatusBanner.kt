/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.status

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.signal.core.util.ByteSize
import org.signal.core.util.bytes
import org.signal.core.util.kibiBytes
import org.signal.core.util.mebiBytes
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.BackupsIconColors
import kotlin.math.max
import kotlin.math.min

private const val NONE = -1

/**
 * Displays a "heads up" widget containing information about the current
 * status of the user's backup.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupStatusBanner(
  data: BackupStatusData,
  onSkipClick: () -> Unit = {},
  onDismissClick: () -> Unit = {},
  contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .padding(contentPadding)
      .border(1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f), shape = RoundedCornerShape(12.dp))
      .fillMaxWidth()
      .defaultMinSize(minHeight = 48.dp)
      .padding(12.dp)
  ) {
    Icon(
      painter = painterResource(id = data.iconRes),
      contentDescription = null,
      tint = data.iconColors.foreground,
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
        text = data.title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
          .padding(end = 20.dp)
          .align(Alignment.CenterVertically)
      )

      data.status?.let { status ->
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

    if (data.progress >= 0f) {
      CircularProgressIndicator(
        progress = { data.progress },
        strokeWidth = 3.dp,
        strokeCap = StrokeCap.Round,
        modifier = Modifier
          .size(24.dp, 24.dp)
      )
    }

    if (data.actionRes != NONE) {
      Buttons.Small(
        onClick = onSkipClick,
        modifier = Modifier.padding(start = 8.dp)
      ) {
        Text(text = stringResource(id = data.actionRes))
      }
    }

    if (data.showDismissAction) {
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

@SignalPreview
@Composable
fun BackupStatusBannerPreview() {
  Previews.Preview {
    Column {
      BackupStatusBanner(
        data = BackupStatusData.RestoringMedia(5755000.bytes, 1253.mebiBytes)
      )

      HorizontalDivider()

      BackupStatusBanner(
        data = BackupStatusData.RestoringMedia(
          bytesDownloaded = 55000.bytes,
          bytesTotal = 1253.mebiBytes,
          restoreStatus = BackupStatusData.RestoreStatus.WAITING_FOR_WIFI
        )
      )

      HorizontalDivider()

      BackupStatusBanner(
        data = BackupStatusData.RestoringMedia(
          bytesDownloaded = 55000.bytes,
          bytesTotal = 1253.mebiBytes,
          restoreStatus = BackupStatusData.RestoreStatus.WAITING_FOR_INTERNET
        )
      )

      HorizontalDivider()

      BackupStatusBanner(
        data = BackupStatusData.RestoringMedia(
          bytesDownloaded = 55000.bytes,
          bytesTotal = 1253.mebiBytes,
          restoreStatus = BackupStatusData.RestoreStatus.FINISHED
        )
      )

      HorizontalDivider()

      BackupStatusBanner(
        data = BackupStatusData.NotEnoughFreeSpace(40900.kibiBytes)
      )

      HorizontalDivider()

      BackupStatusBanner(
        data = BackupStatusData.CouldNotCompleteBackup
      )
    }
  }
}

/**
 * Sealed interface describing status data to display in BackupStatus widget.
 */
sealed interface BackupStatusData {

  @get:DrawableRes
  val iconRes: Int

  @get:Composable
  val title: String

  val iconColors: BackupsIconColors

  @get:StringRes
  val actionRes: Int get() = NONE

  @get:Composable
  val status: String? get() = null

  val progress: Float get() = NONE.toFloat()

  val showDismissAction: Boolean get() = false

  /**
   * Generic failure
   */
  data object CouldNotCompleteBackup : BackupStatusData {
    override val iconRes: Int = R.drawable.symbol_backup_error_24

    override val title: String
      @Composable
      get() = stringResource(R.string.default_error_msg)

    override val iconColors: BackupsIconColors = BackupsIconColors.Warning
  }

  /**
   * User does not have enough space on their device to complete backup restoration
   */
  class NotEnoughFreeSpace(
    requiredSpace: ByteSize
  ) : BackupStatusData {
    val requiredSpace = requiredSpace.toUnitString(maxPlaces = 2)

    override val iconRes: Int = R.drawable.symbol_backup_error_24

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
    val bytesDownloaded: ByteSize = 0.bytes,
    val bytesTotal: ByteSize = 0.bytes,
    val restoreStatus: RestoreStatus = RestoreStatus.NORMAL
  ) : BackupStatusData {
    override val iconRes: Int = R.drawable.symbol_backup_light
    override val iconColors: BackupsIconColors = if (restoreStatus == RestoreStatus.FINISHED) BackupsIconColors.Success else BackupsIconColors.Normal
    override val showDismissAction: Boolean = restoreStatus == RestoreStatus.FINISHED

    override val title: String
      @Composable get() = stringResource(
        when (restoreStatus) {
          RestoreStatus.NORMAL -> R.string.BackupStatus__restoring_media
          RestoreStatus.LOW_BATTERY -> R.string.BackupStatus__restore_paused
          RestoreStatus.WAITING_FOR_INTERNET -> R.string.BackupStatus__restore_paused
          RestoreStatus.WAITING_FOR_WIFI -> R.string.BackupStatus__restore_paused
          RestoreStatus.FINISHED -> R.string.BackupStatus__restore_complete
        }
      )

    override val status: String
      @Composable get() = when (restoreStatus) {
        RestoreStatus.NORMAL -> stringResource(
          R.string.BackupStatus__status_size_of_size,
          bytesDownloaded.toUnitString(maxPlaces = 2),
          bytesTotal.toUnitString(maxPlaces = 2)
        )

        RestoreStatus.LOW_BATTERY -> stringResource(R.string.BackupStatus__status_device_has_low_battery)
        RestoreStatus.WAITING_FOR_INTERNET -> stringResource(R.string.BackupStatus__status_no_internet)
        RestoreStatus.WAITING_FOR_WIFI -> stringResource(R.string.BackupStatus__status_waiting_for_wifi)
        RestoreStatus.FINISHED -> bytesTotal.toUnitString()
      }

    override val progress: Float = if (bytesTotal.bytes > 0 && restoreStatus == RestoreStatus.NORMAL) {
      min(1f, max(0f, bytesDownloaded.bytes.toFloat() / bytesTotal.bytes.toFloat()))
    } else {
      NONE.toFloat()
    }
  }

  /**
   * Describes the status of an in-progress media download session.
   */
  enum class RestoreStatus {
    NORMAL,
    LOW_BATTERY,
    WAITING_FOR_INTERNET,
    WAITING_FOR_WIFI,
    FINISHED
  }
}
