/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.advancedpin

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class AdvancedPinSettingsLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `pin on`() = assertPassesLayoutChecks {
    AdvancedPinSettingsScreen(state = AdvancedPinSettingsState(hasOptedOutOfPin = false), onEvent = {})
  }

  @Test
  fun `pin off`() = assertPassesLayoutChecks {
    AdvancedPinSettingsScreen(state = AdvancedPinSettingsState(hasOptedOutOfPin = true), onEvent = {})
  }

  @Test
  fun `rotate key dialog`() = assertPassesLayoutChecks {
    AdvancedPinSettingsScreen(state = AdvancedPinSettingsState(dialog = AdvancedPinSettingsState.Dialog.RotateBackupKey), onEvent = {})
  }
}
