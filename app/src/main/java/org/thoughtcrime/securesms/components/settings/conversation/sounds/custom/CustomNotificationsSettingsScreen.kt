/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.sounds.custom

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.Texts
import org.signal.core.util.getParcelableExtraCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.RecipientTable.VibrateState
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.RingtoneUtil

private const val TAG = "CustomNotificationsScreen"

/**
 * Per-recipient notification sound and vibration settings.
 *
 * @param ringtonePickerRequests Each request opens the system ringtone picker, whose result comes back as a
 *                               [CustomNotificationsEvents.SetMessageSound] or [CustomNotificationsEvents.SetCallSound].
 */
@Composable
fun CustomNotificationsSettingsScreen(
  state: CustomNotificationsSettingsState,
  ringtonePickerRequests: Flow<RingtonePickerRequest>,
  onEvent: (CustomNotificationsEvents) -> Unit,
  onNavigationClick: () -> Unit
) {
  val activity = LocalActivity.current
  val vibrateLabels = stringArrayResource(R.array.recipient_vibrate_entries)
  val vibrateValues = remember { VibrateState.entries.map { it.id.toString() }.toTypedArray() }

  val messageSoundLauncher = rememberRingtonePickerLauncher { onEvent(CustomNotificationsEvents.SetMessageSound(it)) }
  val callSoundLauncher = rememberRingtonePickerLauncher { onEvent(CustomNotificationsEvents.SetCallSound(it)) }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    onEvent(CustomNotificationsEvents.Foregrounded)
  }

  LaunchedEffect(ringtonePickerRequests, messageSoundLauncher, callSoundLauncher) {
    ringtonePickerRequests.collect { request ->
      when (request.target) {
        RingtonePickerRequest.Target.MESSAGE -> messageSoundLauncher.launch(request.toIntent())
        RingtonePickerRequest.Target.CALL -> callSoundLauncher.launch(request.toIntent())
      }
    }
  }

  Scaffolds.Settings(
    title = stringResource(R.string.CustomNotificationsDialogFragment__custom_notifications),
    onNavigationClick = onNavigationClick,
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(R.string.CallScreenTopBar__go_back)
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .testTag(CustomNotificationsTestTags.CONTENT)
    ) {
      item {
        Texts.SectionHeader(text = stringResource(R.string.CustomNotificationsDialogFragment__messages))
      }

      if (state.supportsNotificationChannels) {
        item {
          Rows.ToggleRow(
            checked = state.hasCustomNotifications,
            text = stringResource(R.string.CustomNotificationsDialogFragment__use_custom_notifications),
            onCheckChanged = { onEvent(CustomNotificationsEvents.SetHasCustomNotifications(it)) },
            modifier = Modifier.testTag(CustomNotificationsTestTags.CUSTOM_NOTIFICATIONS_TOGGLE),
            enabled = state.isInitialLoadComplete
          )
        }
      }

      if (state.canOpenChannelSettings) {
        item {
          Rows.TextRow(
            modifier = Modifier.testTag(CustomNotificationsTestTags.CUSTOMIZE_ROW),
            text = stringResource(R.string.CustomNotificationsDialogFragment__customize),
            label = stringResource(R.string.CustomNotificationsDialogFragment__change_sound_and_vibration),
            enabled = state.controlsEnabled,
            onClick = {
              val notificationChannel = state.notificationChannel
              if (activity != null && notificationChannel != null) {
                NotificationChannels.getInstance().openChannelSettings(activity, notificationChannel, ConversationUtil.getShortcutId(state.recipientId))
              }
            }
          )
        }
      } else {
        item {
          Rows.TextRow(
            modifier = Modifier.testTag(CustomNotificationsTestTags.MESSAGE_SOUND_ROW),
            text = stringResource(R.string.CustomNotificationsDialogFragment__notification_sound),
            label = rememberRingtoneSummary(state.messageSound, Settings.System.DEFAULT_NOTIFICATION_URI),
            enabled = state.controlsEnabled,
            onClick = { onEvent(CustomNotificationsEvents.SelectMessageSound) }
          )
        }

        if (state.supportsNotificationChannels) {
          item {
            Rows.ToggleRow(
              checked = state.messageVibrateEnabled,
              text = stringResource(R.string.CustomNotificationsDialogFragment__vibrate),
              onCheckChanged = { onEvent(CustomNotificationsEvents.SetMessageVibrate(VibrateState.fromBoolean(it))) },
              modifier = Modifier.testTag(CustomNotificationsTestTags.MESSAGE_VIBRATE_TOGGLE),
              enabled = state.controlsEnabled
            )
          }
        } else {
          item {
            Rows.RadioListRow(
              text = stringResource(R.string.CustomNotificationsDialogFragment__vibrate),
              labels = vibrateLabels,
              values = vibrateValues,
              selectedValue = state.messageVibrateState.id.toString(),
              onSelected = { onEvent(CustomNotificationsEvents.SetMessageVibrate(VibrateState.fromId(it.toInt()))) },
              modifier = Modifier.testTag(CustomNotificationsTestTags.MESSAGE_VIBRATE_ROW),
              enabled = state.controlsEnabled
            )
          }
        }
      }

      if (state.showCallingOptions) {
        item {
          Dividers.Default()
        }

        item {
          Texts.SectionHeader(text = stringResource(R.string.CustomNotificationsDialogFragment__call_settings))
        }

        item {
          Rows.TextRow(
            modifier = Modifier.testTag(CustomNotificationsTestTags.CALL_SOUND_ROW),
            text = stringResource(R.string.CustomNotificationsDialogFragment__ringtone),
            label = rememberRingtoneSummary(state.callSound, Settings.System.DEFAULT_RINGTONE_URI),
            enabled = state.controlsEnabled,
            onClick = { onEvent(CustomNotificationsEvents.SelectCallSound) }
          )
        }

        item {
          Rows.RadioListRow(
            text = stringResource(R.string.CustomNotificationsDialogFragment__vibrate),
            labels = vibrateLabels,
            values = vibrateValues,
            selectedValue = state.callVibrateState.id.toString(),
            onSelected = { onEvent(CustomNotificationsEvents.SetCallVibrate(VibrateState.fromId(it.toInt()))) },
            modifier = Modifier.testTag(CustomNotificationsTestTags.CALL_VIBRATE_ROW),
            enabled = state.controlsEnabled
          )
        }
      }
    }
  }
}

@Composable
private fun rememberRingtonePickerLauncher(onPicked: (Uri?) -> Unit): ActivityResultLauncher<Intent> {
  return rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val data = result.data

    if (result.resultCode == Activity.RESULT_OK && data != null) {
      onPicked(data.getParcelableExtraCompat(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java))
    }
  }
}

private fun RingtonePickerRequest.toIntent(): Intent {
  return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, if (target == RingtonePickerRequest.Target.CALL) RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION)
    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
  }
}

/**
 * Display name for a notification sound, falling back to the name of the system default when we can't resolve it.
 *
 * Resolving a sound hits the media store, so the result is remembered until the sound itself changes.
 */
@Composable
private fun rememberRingtoneSummary(ringtone: Uri?, defaultUri: Uri?): String {
  val context = LocalContext.current
  val defaultSummary = stringResource(R.string.CustomNotificationsDialogFragment__default)
  val silentSummary = stringResource(R.string.preferences__silent)
  val unknownSummary = stringResource(R.string.CustomNotificationsDialogFragment__unknown)

  return remember(context, ringtone, defaultUri, defaultSummary, silentSummary, unknownSummary) {
    if (ringtone == null || ringtone == defaultUri) {
      return@remember defaultSummary
    }

    if (ringtone.toString().isEmpty()) {
      return@remember silentSummary
    }

    val tone = RingtoneUtil.getRingtone(context, ringtone) ?: return@remember defaultSummary

    try {
      tone.getTitle(context)
    } catch (e: NullPointerException) {
      Log.w(TAG, "Could not get correct title for ringtone.", e)
      unknownSummary
    } catch (e: SecurityException) {
      Log.w(TAG, "Could not get correct title for ringtone.", e)
      unknownSummary
    }
  }
}

@DayNightPreviews
@Composable
private fun CustomNotificationsSettingsScreenChannelSettingsPreview() {
  Previews.Preview {
    CustomNotificationsSettingsScreen(
      state = CustomNotificationsSettingsState(
        isInitialLoadComplete = true,
        supportsNotificationChannels = true,
        canOpenChannelSettings = true,
        notificationChannel = "channel",
        showCallingOptions = true
      ),
      ringtonePickerRequests = emptyFlow(),
      onEvent = {},
      onNavigationClick = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun CustomNotificationsSettingsScreenInAppSettingsPreview() {
  Previews.Preview {
    CustomNotificationsSettingsScreen(
      state = CustomNotificationsSettingsState(
        isInitialLoadComplete = true,
        supportsNotificationChannels = true,
        canOpenChannelSettings = false,
        notificationChannel = "channel",
        messageVibrateEnabled = true,
        showCallingOptions = true
      ),
      ringtonePickerRequests = emptyFlow(),
      onEvent = {},
      onNavigationClick = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun CustomNotificationsSettingsScreenWithoutChannelsPreview() {
  Previews.Preview {
    CustomNotificationsSettingsScreen(
      state = CustomNotificationsSettingsState(
        isInitialLoadComplete = true,
        supportsNotificationChannels = false,
        canOpenChannelSettings = false,
        messageVibrateState = VibrateState.ENABLED,
        callVibrateState = VibrateState.DISABLED,
        showCallingOptions = true
      ),
      ringtonePickerRequests = emptyFlow(),
      onEvent = {},
      onNavigationClick = {}
    )
  }
}
