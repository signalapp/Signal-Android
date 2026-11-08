/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class LicenseLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `loading`() = assertPassesLayoutChecks {
    LicenseScreen(state = LicenseState(), onEvent = {})
  }

  @Test
  fun `populated`() = assertPassesLayoutChecks {
    LicenseScreen(
      state = LicenseState(
        licenseLines = listOf(
          "The following dependencies are licensed under Apache License, Version 2.0:",
          "",
          "* Android Lifecycle ViewModel with SavedState (https://developer.android.com/jetpack/androidx/releases/lifecycle#2.6.1)",
          "* Android Support Library Annotations (http://developer.android.com/tools/extras/support-library.html)",
          "",
          "Commercial distributors of software may accept certain responsibilities with respect to end users, business partners and the like."
        )
      ),
      onEvent = {}
    )
  }
}
