/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appearance.appicon

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class AppIconTutorialLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `tutorial`() = assertPassesLayoutChecks {
    AppIconTutorialScreen(onEvent = {})
  }
}
