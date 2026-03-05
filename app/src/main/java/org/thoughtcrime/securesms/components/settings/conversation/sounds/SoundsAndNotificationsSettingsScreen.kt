/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.sounds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.RecipientTable.NotificationSetting
import org.signal.core.ui.R as CoreUiR

@Composable
fun SoundsAndNotificationsSettingsScreen(
  state: SoundsAndNotificationsSettingsState2,
  formatMuteUntil: (Long) -> String,
  onEvent: (SoundsAndNotificationsEvent) -> Unit,
  onNavigationClick: () -> Unit,
  onMuteClick: () -> Unit
) {
  val isMuted = state.muteUntil > 0
  var showUnmuteDialog by remember { mutableStateOf(false) }

  Scaffolds.Settings(
    title = stringResource(R.string.ConversationSettingsFragment__sounds_and_notifications),
    onNavigationClick = onNavigationClick,
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(R.string.CallScreenTopBar__go_back)
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier.padding(paddingValues)
    ) {
      // Custom notifications
      item {
        val summary = if (state.hasCustomNotificationSettings) {
          stringResource(R.string.preferences_on)
        } else {
          stringResource(R.string.preferences_off)
        }

        Rows.TextRow(
          text = stringResource(R.string.SoundsAndNotificationsSettingsFragment__custom_notifications),
          label = summary,
          icon = painterResource(R.drawable.ic_speaker_24),
          onClick = { onEvent(SoundsAndNotificationsEvent.NavigateToCustomNotifications) }
        )
      }

      // Mute
      item {
        val muteSummary = if (isMuted) {
          formatMuteUntil(state.muteUntil)
        } else {
          stringResource(R.string.SoundsAndNotificationsSettingsFragment__not_muted)
        }

        val muteIcon = if (isMuted) {
          R.drawable.ic_bell_disabled_24
        } else {
          R.drawable.ic_bell_24
        }

        Rows.TextRow(
          text = stringResource(R.string.SoundsAndNotificationsSettingsFragment__mute_notifications),
          label = muteSummary,
          icon = painterResource(muteIcon),
          onClick = {
            if (isMuted) showUnmuteDialog = true else onMuteClick()
          }
        )
      }

      // Divider + When muted section
      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(text = stringResource(R.string.SoundsAndNotificationsSettingsFragment__when_muted))
      }

      // Calls
      item {
        NotificationSettingRow(
          title = stringResource(R.string.SoundsAndNotificationsSettingsFragment__calls),
          dialogTitle = stringResource(R.string.SoundsAndNotificationsSettingsFragment__calls),
          dialogMessage = stringResource(R.string.SoundsAndNotificationsSettingsFragment__calls_dialog_message),
          icon = painterResource(CoreUiR.drawable.symbol_phone_24),
          setting = state.callNotificationSetting,
          onSelected = { onEvent(SoundsAndNotificationsEvent.SetCallNotificationSetting(it)) }
        )
      }

      // Mentions (only for groups)
      if (state.hasMentionsSupport) {
        item {
          NotificationSettingRow(
            title = stringResource(R.string.SoundsAndNotificationsSettingsFragment__mentions),
            dialogTitle = stringResource(R.string.SoundsAndNotificationsSettingsFragment__mentions),
            dialogMessage = stringResource(R.string.SoundsAndNotificationsSettingsFragment__mentions_dialog_message),
            icon = painterResource(R.drawable.ic_at_24),
            setting = state.mentionSetting,
            onSelected = { onEvent(SoundsAndNotificationsEvent.SetMentionSetting(it)) }
          )
        }
      }

      // Replies (only for groups)
      if (state.hasMentionsSupport) {
        item {
          NotificationSettingRow(
            title = stringResource(R.string.SoundsAndNotificationsSettingsFragment__replies_to_you),
            dialogTitle = stringResource(R.string.SoundsAndNotificationsSettingsFragment__replies_to_you),
            dialogMessage = stringResource(R.string.SoundsAndNotificationsSettingsFragment__replies_dialog_message),
            icon = painterResource(R.drawable.symbol_reply_24),
            setting = state.replyNotificationSetting,
            onSelected = { onEvent(SoundsAndNotificationsEvent.SetReplyNotificationSetting(it)) }
          )
        }
      }
    }
  }

  if (showUnmuteDialog) {
    Dialogs.SimpleAlertDialog(
      title = Dialogs.NoTitle,
      body = formatMuteUntil(state.muteUntil),
      confirm = stringResource(R.string.ConversationSettingsFragment__unmute),
      dismiss = stringResource(android.R.string.cancel),
      onConfirm = { onEvent(SoundsAndNotificationsEvent.Unmute) },
      onDismiss = { showUnmuteDialog = false }
    )
  }
}

@Composable
private fun NotificationSettingRow(
  title: String,
  dialogTitle: String,
  dialogMessage: String,
  icon: Painter,
  setting: NotificationSetting,
  onSelected: (NotificationSetting) -> Unit
) {
  var showDialog by remember { mutableStateOf(false) }

  val labels = arrayOf(
    stringResource(R.string.SoundsAndNotificationsSettingsFragment__always_notify),
    stringResource(R.string.SoundsAndNotificationsSettingsFragment__do_not_notify)
  )
  val selectedLabel = if (setting == NotificationSetting.ALWAYS_NOTIFY) labels[0] else labels[1]

  Rows.TextRow(
    text = title,
    label = selectedLabel,
    icon = icon,
    onClick = { showDialog = true }
  )

  if (showDialog) {
    NotificationSettingDialog(
      title = dialogTitle,
      message = dialogMessage,
      labels = labels,
      selectedIndex = if (setting == NotificationSetting.ALWAYS_NOTIFY) 0 else 1,
      onDismiss = { showDialog = false },
      onSelected = { index ->
        onSelected(if (index == 0) NotificationSetting.ALWAYS_NOTIFY else NotificationSetting.DO_NOT_NOTIFY)
        showDialog = false
      }
    )
  }
}

@Composable
private fun NotificationSettingDialog(
  title: String,
  message: String,
  labels: Array<String>,
  selectedIndex: Int,
  onDismiss: () -> Unit,
  onSelected: (Int) -> Unit
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = AlertDialogDefaults.shape,
      color = SignalTheme.colors.colorSurface2
    ) {
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier
            .padding(top = 24.dp)
            .horizontalGutters()
        )

        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
            .padding(top = 8.dp)
            .horizontalGutters()
        )

        Column(modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)) {
          labels.forEachIndexed { index, label ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable { onSelected(index) }
                .horizontalGutters()
            ) {
              RadioButton(
                selected = index == selectedIndex,
                onClick = { onSelected(index) }
              )
              Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp)
              )
            }
          }
        }
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun SoundsAndNotificationsSettingsScreenMutedPreview() {
  Previews.Preview {
    SoundsAndNotificationsSettingsScreen(
      state = SoundsAndNotificationsSettingsState2(
        muteUntil = Long.MAX_VALUE,
        callNotificationSetting = NotificationSetting.ALWAYS_NOTIFY,
        mentionSetting = NotificationSetting.ALWAYS_NOTIFY,
        replyNotificationSetting = NotificationSetting.DO_NOT_NOTIFY,
        hasMentionsSupport = true,
        hasCustomNotificationSettings = false,
        channelConsistencyCheckComplete = true
      ),
      formatMuteUntil = { "Always" },
      onEvent = {},
      onNavigationClick = {},
      onMuteClick = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun SoundsAndNotificationsSettingsScreenUnmutedPreview() {
  Previews.Preview {
    SoundsAndNotificationsSettingsScreen(
      state = SoundsAndNotificationsSettingsState2(
        muteUntil = 0L,
        callNotificationSetting = NotificationSetting.ALWAYS_NOTIFY,
        mentionSetting = NotificationSetting.ALWAYS_NOTIFY,
        replyNotificationSetting = NotificationSetting.ALWAYS_NOTIFY,
        hasMentionsSupport = false,
        hasCustomNotificationSettings = true,
        channelConsistencyCheckComplete = true
      ),
      formatMuteUntil = { "" },
      onEvent = {},
      onNavigationClick = {},
      onMuteClick = {}
    )
  }
}
