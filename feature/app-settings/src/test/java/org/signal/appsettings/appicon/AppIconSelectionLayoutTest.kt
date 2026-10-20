/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appicon

import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.R
import org.signal.core.ui.SignalLayoutStressTest

class AppIconSelectionLayoutTest : SignalLayoutStressTest() {

  @get:Rule
  val applicationLabelRule = ApplicationLabelRule(R.string.preferences__app_icon)

  @Test
  fun `default icon`() = assertPassesLayoutChecks {
    AppIconSelectionScreen(state = AppIconSelectionState(), onEvent = {})
  }

  @Test
  fun `confirming a change`() = assertPassesLayoutChecks {
    AppIconSelectionScreen(
      state = AppIconSelectionState(dialog = AppIconSelectionState.Dialog.ConfirmIconChange(AppIconPreset.WEATHER)),
      onEvent = {}
    )
  }
}
