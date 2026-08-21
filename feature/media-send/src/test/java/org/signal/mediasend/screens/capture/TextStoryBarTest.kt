/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import android.app.Application
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * The add link affordance is an icon-only button, so TalkBack has nothing but its content description to go on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TextStoryBarTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<TextStoryBarEvents>()

  @Test
  fun `Given the text story bar, when the labelled add link button is tapped, then a link is requested`() {
    composeTestRule.setContent {
      SignalTheme {
        TextStoryHorizontalBar(
          background = Brush.linearGradient(listOf(Color.Red, Color.Green)),
          onEvent = { events += it }
        )
      }
    }

    composeTestRule.onNodeWithContentDescription("Add link").performClick()

    assertEquals(TextStoryBarEvents.AddLink, events.single())
  }
}
