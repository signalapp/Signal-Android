/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import assertk.assertFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class ScreenerLegacyGraphicsTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `refuses to run without native graphics`() {
    assertFailure {
      composeTestRule.findLayoutProblems { Text(text = "Hello") }
    }.isInstanceOf<IllegalStateException>().messageContains("@GraphicsMode(GraphicsMode.Mode.NATIVE)")
  }
}
