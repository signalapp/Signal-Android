/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class HelpSettingsLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `empty`() = assertPassesLayoutChecks {
    HelpSettingsScreen(state = HelpSettingsState(), onEvent = {})
  }

  @Test
  fun `populated`() = assertPassesLayoutChecks {
    HelpSettingsScreen(state = HelpSettingsState(versionName = "8.33.1-nightly-1767-3 | Oct 19 @ 14:32 UTC"), onEvent = {})
  }
}
