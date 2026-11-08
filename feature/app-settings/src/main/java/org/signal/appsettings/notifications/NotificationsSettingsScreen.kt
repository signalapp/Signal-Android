/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.notifications

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object NotificationsSettingsTestTags {
  const val SCROLLER = "notifications_settings_scroller"
  const val ROW_MESSAGE_NOTIFICATIONS = "notifications_settings_row_message_notifications"
  const val ROW_PRIVACY = "notifications_settings_row_privacy"
  const val ROW_WHILE_MUTED = "notifications_settings_row_while_muted"
  const val ROW_REACTIONS = "notifications_settings_row_reactions"
  const val ROW_UNREAD_REMINDERS = "notifications_settings_row_unread_reminders"
  const val ROW_CONTACT_JOINS = "notifications_settings_row_contact_joins"
  const val ROW_REPEAT_ALERTS = "notifications_settings_row_repeat_alerts"
  const val ROW_CUSTOMIZE = "notifications_settings_row_customize"
  const val ROW_MESSAGE_SOUND = "notifications_settings_row_message_sound"
  const val ROW_MESSAGE_VIBRATE = "notifications_settings_row_message_vibrate"
  const val ROW_LED_COLOR = "notifications_settings_row_led_color"
  const val ROW_LED_BLINK = "notifications_settings_row_led_blink"
  const val ROW_IN_CHAT_SOUNDS = "notifications_settings_row_in_chat_sounds"
  const val ROW_TROUBLESHOOT = "notifications_settings_row_troubleshoot"
  const val ROW_PRIORITY = "notifications_settings_row_priority"
  const val ROW_CALL_NOTIFICATIONS = "notifications_settings_row_call_notifications"
  const val ROW_CALL_RINGTONE = "notifications_settings_row_call_ringtone"
  const val ROW_CALL_VIBRATE = "notifications_settings_row_call_vibrate"
  const val ROW_NOTIFICATION_PROFILES = "notifications_settings_row_notification_profiles"
  const val ROW_RESET = "notifications_settings_row_reset"
}

/**
 * App-wide notification settings: message and call notifications, their sounds, and the way into notification profiles.
 */
@Composable
fun NotificationsSettingsScreen(
  state: NotificationsSettingsState,
  onEvent: (NotificationsSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  LifecycleResumeEffect(Unit) {
    onEvent(NotificationsSettingsEvent.ScreenResumed)
    onPauseOrDispose {}
  }

  val messages = state.messageNotificationsState
  val calls = state.callNotificationsState
  val device = state.deviceState

  Scaffolds.Settings(
    title = stringResource(R.string.NotificationsSettingsScreen__notifications),
    onNavigationClick = { onEvent(NotificationsSettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    snackbarHost = {
      Snackbars.Host(
        message = state.snackbar.message(),
        onDismissed = { onEvent(NotificationsSettingsEvent.SnackbarDismissed) }
      )
    },
    modifier = modifier,
    expandableTitle = true
  ) { paddingValues ->
    AppSettingsLazyColumn(
      scaffoldPadding = paddingValues,
      modifier = Modifier.testTag(NotificationsSettingsTestTags.SCROLLER)
    ) {
      if (!messages.canEnableNotifications) {
        item {
          Banner(
            text = stringResource(R.string.NotificationSettingsFragment__to_enable_notifications),
            action = stringResource(R.string.NotificationSettingsFragment__turn_on),
            onActionClick = { onEvent(NotificationsSettingsEvent.TurnOnNotificationsClicked) }
          )
        }
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.preferences__enable_notifications),
          enabled = messages.canEnableNotifications,
          checked = messages.notificationsEnabled,
          onCheckChanged = { onEvent(NotificationsSettingsEvent.MessageNotificationsToggled(it)) },
          modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_MESSAGE_NOTIFICATIONS)
        )
      }

      item {
        Rows.RadioListRow(
          text = stringResource(R.string.preferences_notifications__show),
          labels = stringArrayResource(R.array.pref_notification_privacy_entries),
          values = stringArrayResource(R.array.pref_notification_privacy_values),
          selectedValue = messages.messagePrivacy,
          enabled = messages.notificationsEnabled,
          onSelected = { onEvent(NotificationsSettingsEvent.MessagePrivacySelected(it)) },
          modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_PRIVACY)
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.preferences_notifications__while_muted),
          label = whileMutedSummary(messages),
          enabled = messages.notificationsEnabled,
          onClick = { onEvent(NotificationsSettingsEvent.WhileMutedClicked) },
          modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_WHILE_MUTED)
        )
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.preferences_notifications__reaction),
          label = stringResource(R.string.preferences_notifications__notify_reaction),
          enabled = messages.canEnableNotifications,
          checked = messages.reactionNotificationEnabled,
          onCheckChanged = { onEvent(NotificationsSettingsEvent.ReactionNotificationsToggled(it)) },
          modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_REACTIONS)
        )
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.NotificationsSettingsScreen__unread_reminders),
          label = stringResource(R.string.preferences_notifications__notify_unread),
          enabled = messages.canEnableNotifications,
          checked = messages.unreadReminderEnabled,
          onCheckChanged = { onEvent(NotificationsSettingsEvent.UnreadRemindersToggled(it)) },
          modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_UNREAD_REMINDERS)
        )
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.NotificationsSettingsFragment__contact_joins_signal),
          label = stringResource(R.string.NotificationsSettingsFragment__notify_contact),
          enabled = messages.canEnableNotifications,
          checked = state.notifyWhenContactJoinsSignal,
          onCheckChanged = { onEvent(NotificationsSettingsEvent.ContactJoinsSignalToggled(it)) },
          modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_CONTACT_JOINS)
        )
      }

      item {
        Rows.RadioListRow(
          text = stringResource(R.string.preferences__repeat_alerts),
          labels = stringArrayResource(R.array.pref_repeat_alerts_entries),
          values = stringArrayResource(R.array.pref_repeat_alerts_values),
          selectedValue = messages.repeatAlerts.toString(),
          enabled = messages.notificationsEnabled,
          onSelected = { onEvent(NotificationsSettingsEvent.RepeatAlertsSelected(it)) },
          modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_REPEAT_ALERTS)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(stringResource(R.string.NotificationsSettingsFragment__sounds))
      }

      if (device.apiLevel >= 30) {
        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__customize),
            label = stringResource(R.string.preferences__change_sound_and_vibration),
            enabled = messages.notificationsEnabled,
            onClick = { onEvent(NotificationsSettingsEvent.CustomizeClicked) },
            modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_CUSTOMIZE)
          )
        }
      } else {
        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__sound),
            label = messages.sound.summary(),
            enabled = messages.notificationsEnabled,
            onClick = { onEvent(NotificationsSettingsEvent.MessageSoundClicked) },
            modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_MESSAGE_SOUND)
          )
        }

        item {
          Rows.ToggleRow(
            text = stringResource(R.string.preferences__vibrate),
            checked = messages.vibrateEnabled,
            enabled = messages.notificationsEnabled,
            onCheckChanged = { onEvent(NotificationsSettingsEvent.MessageVibrateToggled(it)) },
            modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_MESSAGE_VIBRATE)
          )
        }

        item {
          Rows.RadioListRow(
            text = {
              Box(
                modifier = Modifier
                  .clip(CircleShape)
                  .size(24.dp)
                  .background(color = ledColor(messages.ledColor))
              )

              Spacer(modifier = Modifier.size(10.dp))

              Text(text = stringResource(R.string.preferences__led_color))
            },
            dialogTitle = stringResource(R.string.preferences__led_color),
            labels = stringArrayResource(R.array.pref_led_color_entries),
            values = stringArrayResource(R.array.pref_led_color_values),
            selectedValue = messages.ledColor,
            enabled = messages.notificationsEnabled,
            onSelected = { onEvent(NotificationsSettingsEvent.LedColorSelected(it)) },
            modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_LED_COLOR)
          )
        }

        if (!device.supportsNotificationChannels) {
          item {
            Rows.RadioListRow(
              text = stringResource(R.string.preferences__pref_led_blink_title),
              labels = stringArrayResource(R.array.pref_led_blink_pattern_entries),
              values = stringArrayResource(R.array.pref_led_blink_pattern_values),
              selectedValue = messages.ledBlink,
              enabled = messages.notificationsEnabled,
              onSelected = { onEvent(NotificationsSettingsEvent.LedBlinkSelected(it)) },
              modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_LED_BLINK)
            )
          }
        }
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.preferences_notifications__in_chat_sounds),
          checked = messages.inChatSoundsEnabled,
          enabled = messages.notificationsEnabled,
          onCheckChanged = { onEvent(NotificationsSettingsEvent.InChatSoundsToggled(it)) },
          modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_IN_CHAT_SOUNDS)
        )
      }

      if (messages.troubleshootNotifications) {
        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences_notifications__troubleshoot),
            onClick = { onEvent(NotificationsSettingsEvent.TroubleshootClicked) },
            modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_TROUBLESHOOT)
          )
        }
      }

      if (device.apiLevel < 30) {
        if (device.supportsNotificationChannels) {
          item {
            Rows.TextRow(
              text = stringResource(R.string.preferences_notifications__priority),
              enabled = messages.notificationsEnabled,
              onClick = { onEvent(NotificationsSettingsEvent.PriorityClicked) },
              modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_PRIORITY)
            )
          }
        } else {
          item {
            Rows.RadioListRow(
              text = stringResource(R.string.preferences_notifications__priority),
              labels = stringArrayResource(R.array.pref_notification_priority_entries),
              values = stringArrayResource(R.array.pref_notification_priority_values),
              selectedValue = messages.priority.toString(),
              enabled = messages.notificationsEnabled,
              onSelected = { onEvent(NotificationsSettingsEvent.PrioritySelected(it)) },
              modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_PRIORITY)
            )
          }
        }
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(stringResource(R.string.NotificationsSettingsFragment__calls))
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.preferences__call_notifications),
          enabled = calls.canEnableNotifications,
          checked = calls.notificationsEnabled,
          onCheckChanged = { onEvent(NotificationsSettingsEvent.CallNotificationsToggled(it)) },
          modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_CALL_NOTIFICATIONS)
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.preferences_notifications__ringtone),
          label = calls.ringtone.summary(),
          enabled = calls.notificationsEnabled,
          onClick = { onEvent(NotificationsSettingsEvent.CallRingtoneClicked) },
          modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_CALL_RINGTONE)
        )
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.preferences__vibrate),
          checked = calls.vibrateEnabled,
          enabled = calls.notificationsEnabled,
          onCheckChanged = { onEvent(NotificationsSettingsEvent.CallVibrateToggled(it)) },
          modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_CALL_VIBRATE)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.NotificationsSettingsScreen__notification_profiles),
          label = stringResource(R.string.NotificationsSettingsFragment__create_a_profile_to_receive_notifications_only_from_people_and_groups_you_choose),
          onClick = { onEvent(NotificationsSettingsEvent.NotificationProfilesClicked) },
          modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_NOTIFICATION_PROFILES)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.NotificationsSettingsFragment__reset),
          label = stringResource(R.string.NotificationsSettingsFragment__reset_notifications),
          onClick = { onEvent(NotificationsSettingsEvent.ResetClicked) },
          modifier = Modifier.testTag(NotificationsSettingsTestTags.ROW_RESET)
        )
      }
    }
  }

  when (state.dialog) {
    NotificationsSettingsState.Dialog.None -> Unit
    NotificationsSettingsState.Dialog.ConfirmReset -> {
      Dialogs.SimpleAlertDialog(
        title = "",
        body = stringResource(R.string.NotificationsSettingsFragment__reset_body),
        dismiss = stringResource(android.R.string.cancel),
        onDeny = { onEvent(NotificationsSettingsEvent.DialogDismissed) },
        onDismissRequest = { onEvent(NotificationsSettingsEvent.DialogDismissed) },
        confirm = stringResource(R.string.NotificationsSettingsFragment__reset_confirm),
        onConfirm = { onEvent(NotificationsSettingsEvent.ResetConfirmed) },
        confirmColor = MaterialTheme.colorScheme.error
      )
    }
  }
}

@Composable
private fun whileMutedSummary(messages: MessageNotificationsState): String {
  val allowed = buildList {
    if (messages.allowCallsWhileMuted) {
      add(stringResource(R.string.NotificationsSettingsScreen__calls))
    }
    if (messages.allowMentionsWhileMuted) {
      add(stringResource(R.string.NotificationsSettingsScreen__mentions))
    }
    if (messages.allowRepliesWhileMuted) {
      add(stringResource(R.string.NotificationsSettingsScreen__replies))
    }
  }

  return if (allowed.isNotEmpty()) {
    allowed.joinToString(", ")
  } else {
    stringResource(CoreUiR.string.preferences__none)
  }
}

@Composable
private fun SoundSummary.summary(): String {
  return when (this) {
    SoundSummary.Default -> stringResource(R.string.preferences__default)
    SoundSummary.Silent -> stringResource(R.string.NotificationsSettingsScreen__silent)
    SoundSummary.Unknown -> stringResource(R.string.NotificationsSettingsFragment__unknown_ringtone)
    is SoundSummary.Named -> title
  }
}

@Composable
private fun NotificationsSettingsState.Snackbar.message(): String? {
  return when (this) {
    NotificationsSettingsState.Snackbar.None -> null
    NotificationsSettingsState.Snackbar.SoundPickerUnavailable -> stringResource(R.string.NotificationSettingsFragment__failed_to_open_picker)
  }
}

@Composable
private fun ledColor(ledColorString: String): Color {
  return when (ledColorString) {
    "green" -> colorResource(CoreUiR.color.green_500)
    "red" -> colorResource(CoreUiR.color.red_500)
    "blue" -> colorResource(CoreUiR.color.blue_500)
    "yellow" -> colorResource(CoreUiR.color.yellow_500)
    "cyan" -> colorResource(CoreUiR.color.cyan_500)
    "magenta" -> colorResource(CoreUiR.color.pink_500)
    "white" -> Color.White
    else -> Color.Transparent
  }
}

@VisibleForTesting
internal val notificationsSettingsPreviewState = NotificationsSettingsState(
  messageNotificationsState = MessageNotificationsState(
    notificationsEnabled = true,
    canEnableNotifications = true,
    sound = SoundSummary.Named("Chime"),
    vibrateEnabled = true,
    ledColor = "blue",
    inChatSoundsEnabled = true,
    repeatAlerts = 1,
    messagePrivacy = "all",
    priority = 1,
    troubleshootNotifications = true,
    reactionNotificationEnabled = true,
    unreadReminderEnabled = true,
    allowMentionsWhileMuted = true,
    allowRepliesWhileMuted = true
  ),
  callNotificationsState = CallNotificationsState(
    notificationsEnabled = true,
    canEnableNotifications = true,
    ringtone = SoundSummary.Default,
    vibrateEnabled = true
  ),
  notifyWhenContactJoinsSignal = true,
  deviceState = DeviceState(apiLevel = 35, supportsNotificationChannels = true)
)

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun NotificationsSettingsScreenPreview() {
  NotificationsSettingsScreen(state = notificationsSettingsPreviewState, onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun NotificationsSettingsScreenApi25Preview() {
  NotificationsSettingsScreen(
    state = notificationsSettingsPreviewState.copy(deviceState = DeviceState(apiLevel = 25, supportsNotificationChannels = false)),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun NotificationsSettingsScreenDisabledBySystemPreview() {
  NotificationsSettingsScreen(
    state = notificationsSettingsPreviewState.copy(
      messageNotificationsState = notificationsSettingsPreviewState.messageNotificationsState.copy(notificationsEnabled = false, canEnableNotifications = false),
      callNotificationsState = notificationsSettingsPreviewState.callNotificationsState.copy(notificationsEnabled = false, canEnableNotifications = false)
    ),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun NotificationsSettingsScreenConfirmResetPreview() {
  NotificationsSettingsScreen(
    state = notificationsSettingsPreviewState.copy(dialog = NotificationsSettingsState.Dialog.ConfirmReset),
    onEvent = {}
  )
}
