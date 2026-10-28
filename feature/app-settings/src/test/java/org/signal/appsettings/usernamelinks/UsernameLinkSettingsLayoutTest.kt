/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernamelinks

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import org.signal.core.ui.compose.QrCodeData

class UsernameLinkSettingsLayoutTest : SignalLayoutStressTest() {

  private val username = setOf(QrCodeBadgeTestTags.USERNAME)

  private val state = UsernameLinkSettingsState(
    username = "parker.42",
    linkState = UsernameLinkSettingsState.LinkState.Present(LINK),
    qrCodeState = QrCodeState.Present(QrCodeData.forData(LINK))
  )

  @Test
  fun `code`() = assertPassesLayoutChecks(ignoreTags = username) {
    UsernameLinkSettingsScreen(state = state, onEvent = {})
  }

  @Test
  fun `code without a link`() = assertPassesLayoutChecks(ignoreTags = username) {
    UsernameLinkSettingsScreen(state = state.copy(linkState = UsernameLinkSettingsState.LinkState.NotSet, qrCodeState = QrCodeState.NotSet), onEvent = {})
  }

  @Test
  fun `scan without the camera`() = assertPassesLayoutChecks(ignoreTags = username) {
    UsernameLinkSettingsScreen(state = state.copy(activeTab = UsernameLinkSettingsState.Tab.SCAN), onEvent = {})
  }

  @Test
  fun `reset confirmation`() = assertPassesLayoutChecks(ignoreTags = username) {
    UsernameLinkSettingsScreen(state = state.copy(dialog = UsernameLinkSettingsState.Dialog.ConfirmReset), onEvent = {})
  }

  companion object {
    private const val LINK = "https://signal.me/#eu/abc"
  }
}
