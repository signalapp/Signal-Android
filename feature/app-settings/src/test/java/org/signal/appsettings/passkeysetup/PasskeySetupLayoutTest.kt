/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.passkeysetup

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class PasskeySetupLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `default`() = assertPassesLayoutChecks {
    PasskeySetupScreen(state = PasskeySetupState(), onEvent = {})
  }
}
