/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.notificationprofilecreated

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class NotificationProfileCreatedLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `loading`() = assertPassesLayoutChecks {
    NotificationProfileCreatedScreen(state = NotificationProfileCreatedState(), onEvent = {})
  }

  @Test
  fun `with schedule`() = assertPassesLayoutChecks {
    NotificationProfileCreatedScreen(state = NotificationProfileCreatedState(hasSchedule = true), onEvent = {})
  }

  @Test
  fun `without schedule`() = assertPassesLayoutChecks {
    NotificationProfileCreatedScreen(state = NotificationProfileCreatedState(hasSchedule = false), onEvent = {})
  }
}
