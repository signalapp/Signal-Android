/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TabbedPagerTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `the first tab and page show to begin with`() {
    setContent()

    composeTestRule.onNodeWithTag(TabbedPagerTestTags.tab(0)).assertIsSelected()
    composeTestRule.onNodeWithText("Page 0").assertExists()
  }

  @Test
  fun `tapping a tab selects it and shows its page`() {
    setContent()

    composeTestRule.onNodeWithTag(TabbedPagerTestTags.tab(2)).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(TabbedPagerTestTags.tab(2)).assertIsSelected()
    composeTestRule.onNodeWithText("Page 2").assertExists()
  }

  private fun setContent() {
    composeTestRule.setContent {
      TabbedPager(tabTitles = listOf("A", "B", "C")) { page ->
        Text(text = "Page $page")
      }
    }
  }
}
