/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.notifications

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class NotificationsSettingsLayoutTest : SignalLayoutStressTest() {

  private val longSoundName = SoundSummary.Named("A very long custom ringtone name that someone downloaded from the internet")

  private val populated = notificationsSettingsPreviewState.copy(
    messageNotificationsState = notificationsSettingsPreviewState.messageNotificationsState.copy(
      sound = longSoundName,
      allowCallsWhileMuted = true
    ),
    callNotificationsState = notificationsSettingsPreviewState.callNotificationsState.copy(ringtone = longSoundName)
  )

  @Test
  fun `default`() = assertPassesLayoutChecks {
    NotificationsSettingsScreen(state = NotificationsSettingsState(), onEvent = {})
  }

  @Test
  fun `populated with channel settings`() = assertPassesLayoutChecks {
    NotificationsSettingsScreen(state = populated, onEvent = {})
  }

  @Test
  fun `populated before channel settings own sound`() = assertPassesLayoutChecks {
    NotificationsSettingsScreen(state = populated.copy(deviceState = DeviceState(apiLevel = 29, supportsNotificationChannels = true)), onEvent = {})
  }

  @Test
  fun `populated without notification channels`() = assertPassesLayoutChecks {
    NotificationsSettingsScreen(state = populated.copy(deviceState = DeviceState(apiLevel = 25, supportsNotificationChannels = false)), onEvent = {})
  }

  @Test
  fun `notifications turned off by the system`() = assertPassesLayoutChecks {
    NotificationsSettingsScreen(
      state = populated.copy(
        messageNotificationsState = populated.messageNotificationsState.copy(notificationsEnabled = false, canEnableNotifications = false),
        callNotificationsState = populated.callNotificationsState.copy(notificationsEnabled = false, canEnableNotifications = false)
      ),
      onEvent = {}
    )
  }

  @Test
  fun `confirming reset`() = assertPassesLayoutChecks {
    NotificationsSettingsScreen(state = populated.copy(dialog = NotificationsSettingsState.Dialog.ConfirmReset), onEvent = {})
  }
}
