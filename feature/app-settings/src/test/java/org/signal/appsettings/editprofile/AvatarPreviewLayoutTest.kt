/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.editprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class AvatarPreviewLayoutTest : SignalLayoutStressTest() {

  private val userText = setOf(AvatarPreviewTestTags.TITLE)

  @Test
  fun `with the toolbar`() = assertPassesLayoutChecks(ignoreTags = userText) {
    AvatarPreviewScreen(
      title = "Maximiliana-Josephine Wolfeschlegelsteinhausen",
      toolbarVisible = true,
      onEvent = {},
      avatar = { modifier -> Box(modifier = modifier.background(Color.Gray)) }
    )
  }

  @Test
  fun `without the toolbar`() = assertPassesLayoutChecks {
    AvatarPreviewScreen(
      title = "Peter Parker",
      toolbarVisible = false,
      onEvent = {},
      avatar = { modifier -> Box(modifier = modifier.background(Color.Gray)) }
    )
  }
}
