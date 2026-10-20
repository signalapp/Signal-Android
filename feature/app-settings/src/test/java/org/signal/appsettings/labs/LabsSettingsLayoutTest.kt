/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.labs

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class LabsSettingsLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `all off`() = assertPassesLayoutChecks {
    LabsSettingsScreen(state = LabsSettingsState(), onEvent = {})
  }

  @Test
  fun `all on`() = assertPassesLayoutChecks {
    LabsSettingsScreen(
      state = LabsSettingsState(
        individualChatPlaintextExport = true,
        storyArchive = true,
        incognito = true,
        betterSearch = true,
        starredMessages = true,
        stickerReplies = true,
        improvedMessageDeletion = true
      ),
      onEvent = {}
    )
  }
}
