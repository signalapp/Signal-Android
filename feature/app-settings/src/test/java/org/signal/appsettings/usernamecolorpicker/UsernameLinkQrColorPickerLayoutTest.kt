/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernamecolorpicker

import org.junit.Test
import org.signal.appsettings.usernamelinks.QrCodeBadgeTestTags
import org.signal.appsettings.usernamelinks.QrCodeState
import org.signal.appsettings.usernamelinks.UsernameQrCodeColorScheme
import org.signal.core.ui.SignalLayoutStressTest
import org.signal.core.ui.compose.QrCodeData

class UsernameLinkQrColorPickerLayoutTest : SignalLayoutStressTest() {

  companion object {
    /** The username is user-entered text with no break points, so a long one is allowed to wrap mid-word. */
    private val USER_TEXT = setOf(QrCodeBadgeTestTags.USERNAME)
  }

  @Test
  fun `loading`() = assertPassesLayoutChecks(ignoreTags = USER_TEXT) {
    UsernameLinkQrColorPickerScreen(state = UsernameLinkQrColorPickerState(username = "parker.42"), onEvent = {})
  }

  @Test
  fun `long username`() = assertPassesLayoutChecks(ignoreTags = USER_TEXT) {
    UsernameLinkQrColorPickerScreen(
      state = UsernameLinkQrColorPickerState(
        username = "alexandriamontgomeryfitzge.4567",
        qrCodeData = QrCodeState.Present(QrCodeData.forData("https://signal.me/#eu/asdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdf")),
        selectedColorScheme = UsernameQrCodeColorScheme.Purple
      ),
      onEvent = {}
    )
  }
}
