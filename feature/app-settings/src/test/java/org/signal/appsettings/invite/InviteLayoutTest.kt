/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.invite

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class InviteLayoutTest : SignalLayoutStressTest() {

  /** The invite text is whatever the user typed, and its link can't help being split once it outgrows the field. */
  private val userText = setOf(InviteTestTags.INPUT_INVITE_TEXT)

  @Test
  fun `default invite text`() = assertPassesLayoutChecks(ignoreTags = userText) {
    InviteScreen(
      state = InviteState(inviteText = "Let's switch to Signal: https://signal.org/install"),
      onEvent = {}
    )
  }

  @Test
  fun `empty invite text`() = assertPassesLayoutChecks(ignoreTags = userText) {
    InviteScreen(state = InviteState(inviteText = ""), onEvent = {})
  }
}
