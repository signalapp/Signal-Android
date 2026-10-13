/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.shared

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediasend.test.TestTags

/**
 * Covers the button both capture screens and the picker share: what it reads, and what it raises.
 *
 * How it is laid out is left to the snapshots. Nothing here measures the button, since a measurement only proves a
 * number survived the layout, not that the button looks right.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w400dp-h800dp")
class NextButtonTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private var clicks = 0

  @Test
  fun `Given a selection, when displayed, then the count says how much is waiting`() {
    setContent(selectedMediaCount = 3)

    composeTestRule.onNodeWithTag(TestTags.MEDIA_SEND_NEXT_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.MEDIA_SEND_MEDIA_COUNT).assertTextEquals("3")
  }

  @Test
  fun `when the button is clicked, then moving on is asked for`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.MEDIA_SEND_NEXT_BUTTON).performClick()

    assertThat(clicks).isEqualTo(1)
  }

  private fun setContent(selectedMediaCount: Int = 1) {
    composeTestRule.setContent {
      SignalTheme {
        NextButton(
          selectedMediaCount = selectedMediaCount,
          onClick = { clicks++ }
        )
      }
    }

    composeTestRule.waitForIdle()
  }
}
