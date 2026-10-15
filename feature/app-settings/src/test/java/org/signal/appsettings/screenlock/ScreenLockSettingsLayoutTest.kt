/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.screenlock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ScreenLockSettingsLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `off`() = assertPassesLayoutChecks {
    Screen(ScreenLockSettingsState())
  }

  @Test
  fun `on with a preset timeout`() = assertPassesLayoutChecks {
    Screen(ScreenLockSettingsState(isScreenLockEnabled = true, timeout = 5.minutes))
  }

  @Test
  fun `on with a custom timeout`() = assertPassesLayoutChecks {
    Screen(ScreenLockSettingsState(isScreenLockEnabled = true, timeout = 6.days + 23.hours))
  }

  @Test
  fun `picking a custom timeout`() = assertPassesLayoutChecks {
    Screen(ScreenLockSettingsState(isScreenLockEnabled = true, timeout = 3.hours, dialog = ScreenLockSettingsState.Dialog.CustomTimeout))
  }

  @Composable
  private fun Screen(state: ScreenLockSettingsState) {
    // The biometrics helper needs a FragmentActivity to show its prompt. Inspection mode makes it pass straight through.
    CompositionLocalProvider(LocalInspectionMode provides true) {
      ScreenLockSettingsScreen(state = state, onEvent = {})
    }
  }
}
