/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.SignalTheme
import org.signal.core.ui.R as CoreUiR

enum class MessageBackupsKeyEducationScreenMode {
  /**
   * Displayed when the user is enabling remote backups and does not have unified local backups enabled
   */
  REMOTE_BACKUP_WITH_LOCAL_DISABLED,

  /**
   * Displayed when the user is upgrading legacy to unified local backup
   */
  LOCAL_BACKUP_UPGRADE,

  /**
   * Displayed when the user has unified local backup and is enabling remote backups
   */
  REMOTE_BACKUP_WITH_LOCAL_ENABLED
}

/**
 * Screen detailing how a backups key is used to restore a backup
 */
@Composable
fun MessageBackupsKeyEducationScreen(
  onNavigationClick: () -> Unit = {},
  onNextClick: () -> Unit = {},
  mode: MessageBackupsKeyEducationScreenMode = MessageBackupsKeyEducationScreenMode.REMOTE_BACKUP_WITH_LOCAL_DISABLED
) {
  val scrollState = rememberScrollState()

  Scaffolds.Settings(
    title = "",
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    onNavigationClick = onNavigationClick
  ) {
    Column(
      modifier = Modifier
        .padding(it)
        .padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
        .fillMaxSize()
        .verticalScroll(scrollState),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Image(
        painter = painterResource(R.drawable.image_signal_backups_key),
        contentDescription = null,
        modifier = Modifier
          .padding(top = 24.dp)
          .size(80.dp)
      )

      Text(
        text = getTitleText(mode),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(top = 16.dp)
      )

      when (mode) {
        MessageBackupsKeyEducationScreenMode.REMOTE_BACKUP_WITH_LOCAL_DISABLED -> {
          RemoteBackupWithLocalDisabledInfo()
        }

        MessageBackupsKeyEducationScreenMode.LOCAL_BACKUP_UPGRADE -> {
          LocalBackupUpgradeInfo()
        }
        MessageBackupsKeyEducationScreenMode.REMOTE_BACKUP_WITH_LOCAL_ENABLED -> {
          RemoteBackupWithLocalEnabledInfo()
        }
      }

      Spacer(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      )

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 16.dp, bottom = 24.dp)
      ) {
        Buttons.LargeTonal(
          onClick = onNextClick,
          modifier = Modifier.align(Alignment.Center)
        ) {
          Text(
            text = stringResource(R.string.MessageBackupsKeyEducationScreen__view_recovery_key),
            modifier = Modifier.padding(horizontal = 20.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun getTitleText(mode: MessageBackupsKeyEducationScreenMode): String {
  return when (mode) {
    MessageBackupsKeyEducationScreenMode.REMOTE_BACKUP_WITH_LOCAL_DISABLED -> stringResource(R.string.MessageBackupsKeyEducationScreen__your_backup_key)
    MessageBackupsKeyEducationScreenMode.LOCAL_BACKUP_UPGRADE -> stringResource(R.string.MessageBackupsKeyEducationScreen__your_new_recovery_key)
    MessageBackupsKeyEducationScreenMode.REMOTE_BACKUP_WITH_LOCAL_ENABLED -> stringResource(R.string.MessageBackupsKeyEducationScreen__your_recovery_key)
  }
}

@Composable
private fun LocalBackupUpgradeInfo() {
  val normalText = stringResource(R.string.MessageBackupsKeyEducationScreen__local_backup_upgrade_description)
  val boldText = stringResource(R.string.MessageBackupsKeyEducationScreen__local_backup_upgrade_description_bold)

  DescriptionText(
    normalText = normalText,
    boldText = boldText
  )

  UseThisKeyToContainer {
    UseThisKeyToRow(
      icon = ImageVector.vectorResource(R.drawable.symbol_folder_24),
      text = stringResource(R.string.MessageBackupsKeyEducationScreen__restore_on_device_backup)
    )

    Spacer(modifier = Modifier.padding(vertical = 16.dp))

    UseThisKeyToRow(
      icon = ImageVector.vectorResource(CoreUiR.drawable.symbol_backup_24),
      text = stringResource(R.string.MessageBackupsKeyEducationScreen__restore_a_signal_secure_backup)
    )
  }
}

@Composable
private fun RemoteBackupWithLocalEnabledInfo() {
  val normalText = stringResource(R.string.MessageBackupsKeyEducationScreen__remote_backup_with_local_enabled_description)
  val boldText = stringResource(R.string.MessageBackupsKeyEducationScreen__remote_backup_with_local_enabled_description_bold)

  DescriptionText(
    normalText = normalText,
    boldText = boldText
  )

  UseThisKeyToContainer {
    UseThisKeyToRow(
      icon = ImageVector.vectorResource(CoreUiR.drawable.symbol_backup_24),
      text = stringResource(R.string.MessageBackupsKeyEducationScreen__restore_your_signal_secure_backup)
    )

    Spacer(modifier = Modifier.padding(vertical = 16.dp))

    UseThisKeyToRow(
      icon = ImageVector.vectorResource(R.drawable.symbol_folder_24),
      text = stringResource(R.string.MessageBackupsKeyEducationScreen__restore_on_device_backup)
    )
  }
}

@Composable
private fun DescriptionText(
  normalText: String,
  boldText: String
) {
  Text(
    text = buildAnnotatedString {
      append(normalText)
      append(" ")
      withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
        append(boldText)
      }
    },
    textAlign = TextAlign.Center,
    modifier = Modifier
      .padding(top = 12.dp)
      .horizontalGutters()
  )
}

@Composable
private fun UseThisKeyToContainer(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit
) {
  Column(
    modifier = modifier
      .padding(top = 28.dp)
      .horizontalGutters()
      .fillMaxWidth()
      .background(color = SignalTheme.colors.colorSurface1, shape = RoundedCornerShape(10.dp))
      .padding(24.dp)
  ) {
    Text(
      text = stringResource(R.string.MessageBackupsKeyEducationScreen__use_this_key_to),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 14.dp)
    )

    content()
  }
}

@Composable
private fun UseThisKeyToRow(
  icon: ImageVector,
  text: String,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .padding(start = 12.dp)
  ) {
    Icon(
      imageVector = icon,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      contentDescription = null,
      modifier = Modifier.size(24.dp)
    )
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 16.dp)
    )
  }
}

@Composable
private fun RemoteBackupWithLocalDisabledInfo() {
  InfoRow(
    R.drawable.symbol_number_24,
    R.string.MessageBackupsKeyEducationScreen__your_backup_key_is_a
  )

  InfoRow(
    CoreUiR.drawable.symbol_lock_24,
    R.string.MessageBackupsKeyEducationScreen__store_your_recovery
  )

  InfoRow(
    R.drawable.symbol_error_circle_24,
    R.string.MessageBackupsKeyEducationScreen__if_you_lose_it
  )
}

@Composable
private fun InfoRow(@DrawableRes iconId: Int, @StringRes textId: Int) {
  Row(
    verticalAlignment = Alignment.Top,
    modifier = Modifier.padding(top = 24.dp)
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(iconId),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant

    )
    Text(
      text = stringResource(textId),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 16.dp)
    )
  }
}

@DayNightPreviews
@Composable
private fun MessageBackupsKeyEducationScreenRemoteBackupWithLocalDisabledPreview() {
  Previews.Preview {
    MessageBackupsKeyEducationScreen(
      mode = MessageBackupsKeyEducationScreenMode.REMOTE_BACKUP_WITH_LOCAL_DISABLED
    )
  }
}

@DayNightPreviews
@Composable
private fun MessageBackupsKeyEducationScreenLocalBackupUpgradePreview() {
  Previews.Preview {
    MessageBackupsKeyEducationScreen(
      mode = MessageBackupsKeyEducationScreenMode.LOCAL_BACKUP_UPGRADE
    )
  }
}

@DayNightPreviews
@Composable
private fun MessageBackupsKeyEducationScreenRemoteBackupWithLocalEnabledPreview() {
  Previews.Preview {
    MessageBackupsKeyEducationScreen(
      mode = MessageBackupsKeyEducationScreenMode.REMOTE_BACKUP_WITH_LOCAL_ENABLED
    )
  }
}
