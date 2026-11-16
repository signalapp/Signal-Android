/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

import org.junit.Test
import org.signal.core.models.storage.KeepMessagesDuration
import org.signal.core.ui.SignalLayoutStressTest

class KeepMessagesSettingsLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `forever`() = assertPassesLayoutChecks {
    KeepMessagesSettingsScreen(state = KeepMessagesSettingsState(), onEvent = {})
  }

  @Test
  fun `six months`() = assertPassesLayoutChecks {
    KeepMessagesSettingsScreen(state = KeepMessagesSettingsState(selection = KeepMessagesDuration.SIX_MONTHS), onEvent = {})
  }

  @Test
  fun `confirming a shorter duration`() = assertPassesLayoutChecks {
    KeepMessagesSettingsScreen(
      state = KeepMessagesSettingsState(dialog = KeepMessagesSettingsState.Dialog.ConfirmDuration(KeepMessagesDuration.THIRTY_DAYS)),
      onEvent = {}
    )
  }
}
