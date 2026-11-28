/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernamelinks

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class UsernameLinkShareSheetContentLayoutTest : SignalLayoutStressTest() {

  /** The link is one long unbroken token, so it can't help being split across lines. */
  private val link = setOf(UsernameLinkShareSheetTestTags.LINK)

  @Test
  fun `link`() = assertPassesLayoutChecks(ignoreTags = link) {
    UsernameLinkShareSheetContent(
      usernameLink = "https://signal.me#eufzLWmFFUYAOqnVJ4Zlt0KqXf87r59FC1hZ3r7WipjKvgzMBg7DBlY5DB5hQTjsw0",
      onEvent = {}
    )
  }
}
