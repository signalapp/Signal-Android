/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LicenseScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<LicenseEvent>()

  @Test
  fun `every license line is rendered`() {
    val lines = (1..100).map { "License line $it" }
    setContent(LicenseState(licenseLines = lines))

    composeTestRule.onNodeWithText("License line 1").assertIsDisplayed()
    composeTestRule.onNodeWithTag(LicenseTestTags.SCROLLER).performScrollToNode(hasText("License line 100"))
    composeTestRule.onNodeWithText("License line 100").assertIsDisplayed()
  }

  @Test
  fun `clicking back emits NavigateBackClicked`() {
    setContent(LicenseState())

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(events).containsExactly(LicenseEvent.NavigateBackClicked)
  }

  private fun setContent(state: LicenseState) {
    composeTestRule.setContent {
      LicenseScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }
}
