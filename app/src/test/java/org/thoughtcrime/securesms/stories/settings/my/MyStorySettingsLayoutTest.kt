/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stories.settings.my

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode

class MyStorySettingsLayoutTest : SignalLayoutStressTest() {

  private val populated = MyStorySettingsState(
    myStoryPrivacyState = MyStoryPrivacyState(
      privacyMode = DistributionListPrivacyMode.ALL_EXCEPT,
      connectionCount = 12_345
    ),
    areRepliesAndReactionsEnabled = true,
    allSignalConnectionsCount = 123_456,
    hasUserPerformedManualSelection = true
  )

  @Test
  fun `empty`() = assertPassesLayoutChecks {
    MyStorySettingsScreen(state = MyStorySettingsState(), onEvent = {})
  }

  @Test
  fun `all except selected`() = assertPassesLayoutChecks {
    MyStorySettingsScreen(state = populated, onEvent = {})
  }

  @Test
  fun `only share with selected`() = assertPassesLayoutChecks {
    MyStorySettingsScreen(
      state = populated.copy(myStoryPrivacyState = populated.myStoryPrivacyState.copy(privacyMode = DistributionListPrivacyMode.ONLY_WITH)),
      onEvent = {}
    )
  }

  @Test
  fun `no manual selection`() = assertPassesLayoutChecks {
    MyStorySettingsScreen(state = populated.copy(hasUserPerformedManualSelection = false), onEvent = {})
  }
}
