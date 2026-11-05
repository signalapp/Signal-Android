/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.advancedprivacy

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class AdvancedPrivacySettingsLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `defaults`() = assertPassesLayoutChecks {
    AdvancedPrivacySettingsScreen(state = AdvancedPrivacySettingsState(), onEvent = {})
  }

  @Test
  fun `primary device with everything on`() = assertPassesLayoutChecks {
    AdvancedPrivacySettingsScreen(
      state = AdvancedPrivacySettingsState(
        alwaysRelayCalls = true,
        censorshipCircumventionState = CensorshipCircumventionState.AVAILABLE_AUTOMATICALLY_ENABLED,
        censorshipCircumventionEnabled = true,
        showSealedSenderStatusIcon = true,
        allowSealedSenderFromAnyone = true,
        allowAutomaticKeyVerification = true,
        isPrimaryDevice = true
      ),
      onEvent = {}
    )
  }

  @Test
  fun `no internet`() = assertPassesLayoutChecks {
    AdvancedPrivacySettingsScreen(
      state = AdvancedPrivacySettingsState(censorshipCircumventionState = CensorshipCircumventionState.UNAVAILABLE_NO_INTERNET),
      onEvent = {}
    )
  }
}
