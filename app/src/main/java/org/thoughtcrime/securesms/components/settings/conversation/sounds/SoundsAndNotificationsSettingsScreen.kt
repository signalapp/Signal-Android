/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.sounds

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.Texts
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.RecipientTable.NotificationSetting
import org.thoughtcrime.securesms.util.RemoteConfig

@Composable
fun SoundsAndNotificationsSettingsScreen(
  state: SoundsAndNotificationsSettingsState,
  formatMuteUntil: (Long) -> String,
  onEvent: (SoundsAndNotificationsEvent) -> Unit,
  onNavigationClick: () -> Unit,
  onMuteClick: () -> Unit
) {
  val isMuted = state.isMuted
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

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(text = stringResource(R.string.preferences__notifications))
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

      if (RemoteConfig.internalUser || state.hasMentionsSupport) {
        item {
          Rows.TextRow(
            text = stringResource(R.string.SoundsAndNotificationsSettingsFragment__when_muted),
            label = getMuteSummary(state),
            icon = ImageVector.vectorResource(R.drawable.symbol_bell_badge_24),
            onClick = { onEvent(SoundsAndNotificationsEvent.NavigateToMutedNotifications) }
          )
        }
      }

//      TODO(michelle): Unread reminders
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
fun getMuteSummary(state: SoundsAndNotificationsSettingsState): String {
  val body = mutableListOf<String>()
  if (RemoteConfig.internalUser && state.callNotificationSetting == NotificationSetting.ALWAYS_NOTIFY) {
    body.add(stringResource(R.string.MutedNotificationsFragment__calls))
  }
  if (state.hasMentionsSupport && state.mentionSetting == NotificationSetting.ALWAYS_NOTIFY) {
    body.add(stringResource(R.string.MutedNotificationsFragment__mentions))
  }
  if (RemoteConfig.internalUser && state.replyNotificationSetting == NotificationSetting.ALWAYS_NOTIFY) {
    body.add(stringResource(R.string.MutedNotificationsFragment__replies))
  }
  return if (body.isNotEmpty()) {
    body.joinToString(", ")
  } else {
    stringResource(R.string.preferences__none)
  }
}

@DayNightPreviews
@Composable
private fun SoundsAndNotificationsSettingsScreenMutedPreview() {
  Previews.Preview {
    SoundsAndNotificationsSettingsScreen(
      state = SoundsAndNotificationsSettingsState(
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
      state = SoundsAndNotificationsSettingsState(
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
