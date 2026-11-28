/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

import org.junit.Test
import org.signal.appsettings.managestorage.ChatLengthLimitSettingsState.Dialog
import org.signal.core.ui.SignalLayoutStressTest

class ChatLengthLimitSettingsLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `no limit`() = assertPassesLayoutChecks {
    ChatLengthLimitSettingsScreen(state = ChatLengthLimitSettingsState(), onEvent = {})
  }

  @Test
  fun `preset limit`() = assertPassesLayoutChecks {
    ChatLengthLimitSettingsScreen(state = ChatLengthLimitSettingsState(lengthLimit = 5000), onEvent = {})
  }

  @Test
  fun `custom limit`() = assertPassesLayoutChecks {
    ChatLengthLimitSettingsScreen(state = ChatLengthLimitSettingsState(lengthLimit = 123_456), onEvent = {})
  }

  @Test
  fun `confirming a limit`() = assertPassesLayoutChecks {
    ChatLengthLimitSettingsScreen(state = ChatLengthLimitSettingsState(dialog = Dialog.ConfirmLimit(100)), onEvent = {})
  }

  @Test
  fun `entering a custom limit`() = assertPassesLayoutChecks {
    ChatLengthLimitSettingsScreen(state = ChatLengthLimitSettingsState(lengthLimit = 123_456, dialog = Dialog.CustomLimit(initialLimit = 123_456)), onEvent = {})
  }
}
