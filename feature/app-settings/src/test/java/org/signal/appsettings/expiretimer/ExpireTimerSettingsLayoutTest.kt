/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.expiretimer

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ExpireTimerSettingsLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `new chats`() = assertPassesLayoutChecks {
    ExpireTimerSettingsScreen(state = ExpireTimerSettingsState(initialTimer = 1.days), onEvent = {})
  }

  @Test
  fun `a chat with a custom timer while saving`() = assertPassesLayoutChecks {
    ExpireTimerSettingsScreen(
      state = ExpireTimerSettingsState(
        mode = ExpireTimerSettingsState.Mode.RECIPIENT,
        initialTimer = 6.days + 23.hours + 59.minutes,
        isSaving = true
      ),
      onEvent = {}
    )
  }

  @Test
  fun `custom time dialog`() = assertPassesLayoutChecks {
    ExpireTimerSettingsScreen(state = ExpireTimerSettingsState(dialog = ExpireTimerSettingsState.Dialog.CustomTimer), onEvent = {})
  }
}
