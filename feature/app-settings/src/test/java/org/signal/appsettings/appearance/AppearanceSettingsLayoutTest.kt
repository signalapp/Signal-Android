/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appearance

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class AppearanceSettingsLayoutTest : SignalLayoutStressTest() {

  private val populated = AppearanceSettingsState(
    language = "pt_BR",
    theme = AppearanceSettingsState.Theme.DARK,
    messageFontSize = 28,
    isCompactNavigationBar = true
  )

  @Test
  fun `default`() = assertPassesLayoutChecks {
    AppearanceSettingsScreen(state = AppearanceSettingsState(), onEvent = {})
  }

  @Test
  fun `populated`() = assertPassesLayoutChecks {
    AppearanceSettingsScreen(state = populated, onEvent = {})
  }

  @Test
  fun `without system theme or app icon`() = assertPassesLayoutChecks {
    AppearanceSettingsScreen(state = populated.copy(isSystemThemeAvailable = false, isAppIconSupported = false), onEvent = {})
  }

  @Test
  fun `confirming a language change`() = assertPassesLayoutChecks {
    AppearanceSettingsScreen(state = populated.copy(dialog = AppearanceSettingsState.Dialog.ConfirmLanguageChange("de")), onEvent = {})
  }

  @Test
  fun `picking a navigation bar size`() = assertPassesLayoutChecks {
    AppearanceSettingsScreen(state = populated.copy(dialog = AppearanceSettingsState.Dialog.NavigationBarSize(isCompact = true)), onEvent = {})
  }
}
