/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.notifications.NotificationsSettingsAction
import org.signal.appsettings.notifications.NotificationsSettingsEvent
import org.signal.appsettings.notifications.NotificationsSettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.CollectActions
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.PromptBatterySaverDialogFragment
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.TurnOnNotificationsBottomSheet

private val TAG = Log.tag(NotificationsSettingsAction::class)

/**
 * App-wide notification settings, wired to their view model and to the system pickers and sheets they open.
 */
@Composable
fun NotificationsSettingsEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: NotificationsSettingsViewModel = viewModel { NotificationsSettingsViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val activity = LocalActivity.current

  val messageSoundPicker = rememberLauncherForActivityResult(
    contract = remember { NotificationSoundSelectionContract(NotificationSoundSelectionContract.Target.MESSAGE) },
    onResult = { viewModel.onEvent(NotificationsSettingsEvent.MessageSoundPicked(it)) }
  )

  val callRingtonePicker = rememberLauncherForActivityResult(
    contract = remember { NotificationSoundSelectionContract(NotificationSoundSelectionContract.Target.CALL) },
    onResult = { viewModel.onEvent(NotificationsSettingsEvent.CallRingtonePicked(it)) }
  )

  val prioritySettings = rememberLauncherForActivityResult(
    contract = remember { NotificationPrioritySelectionContract() },
    onResult = {}
  )

  CollectActions(viewModel.actions) { action ->
    when (action) {
      NotificationsSettingsAction.ShowTurnOnNotificationsSheet -> activity.asFragmentActivity(action) {
        TurnOnNotificationsBottomSheet.turnOnSystemNotificationsFragment(it).show(it.supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
      }
      NotificationsSettingsAction.OpenMessageChannelSettings -> {
        if (activity != null) {
          NotificationChannels.getInstance().openChannelSettings(activity, NotificationChannels.getInstance().messagesChannel, null)
        }
      }
      NotificationsSettingsAction.LaunchMessageSoundPicker -> messageSoundPicker.launchSoundPicker { viewModel.onEvent(NotificationsSettingsEvent.SoundPickerUnavailable) }
      NotificationsSettingsAction.LaunchCallRingtonePicker -> callRingtonePicker.launchSoundPicker { viewModel.onEvent(NotificationsSettingsEvent.SoundPickerUnavailable) }
      NotificationsSettingsAction.LaunchNotificationPrioritySettings -> prioritySettings.launch(Unit)
      NotificationsSettingsAction.ShowTroubleshootNotificationsDialog -> activity.asFragmentActivity(action) { PromptBatterySaverDialogFragment.show(it.supportFragmentManager) }
    }
  }

  NotificationsSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

/** Runs [block] with this activity as the [FragmentActivity] whose fragment manager the sheets and dialogs here are shown on. */
private inline fun Activity?.asFragmentActivity(action: NotificationsSettingsAction, block: (FragmentActivity) -> Unit) {
  val fragmentActivity = this as? FragmentActivity
  if (fragmentActivity == null) {
    Log.w(TAG, "Unable to handle $action without a FragmentActivity.")
    return
  }

  block(fragmentActivity)
}

private fun ManagedActivityResultLauncher<Unit, *>.launchSoundPicker(onUnavailable: () -> Unit) {
  try {
    launch(Unit)
  } catch (e: ActivityNotFoundException) {
    onUnavailable()
  }
}
