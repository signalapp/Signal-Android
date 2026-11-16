/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.mutednotifications

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class MutedNotificationsLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `defaults for every muted chat`() = assertPassesLayoutChecks {
    MutedNotificationsScreen(state = MutedNotificationsState(isGlobal = true, allowCalls = true, allowReplies = true), onEvent = {})
  }

  @Test
  fun `a group's own settings`() = assertPassesLayoutChecks {
    MutedNotificationsScreen(state = MutedNotificationsState(isGlobal = false, allowMentions = true), onEvent = {})
  }

  @Test
  fun `a one-to-one chat's own settings`() = assertPassesLayoutChecks {
    MutedNotificationsScreen(state = MutedNotificationsState(isGlobal = false, showMentions = false, showReplies = false), onEvent = {})
  }
}
