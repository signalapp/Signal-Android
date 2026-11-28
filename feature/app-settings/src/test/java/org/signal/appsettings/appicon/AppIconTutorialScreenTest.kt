/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appicon

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
class AppIconTutorialScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<AppIconTutorialEvent>()

  @Test
  fun `clicking back emits NavigateBackClicked`() {
    setContent()

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(events).containsExactly(AppIconTutorialEvent.NavigateBackClicked)
  }

  @Test
  fun `both warnings are shown`() {
    setContent()

    composeTestRule.onNodeWithText("App icons and names are visible on the home screen and app drawer.").assertIsDisplayed()

    val notificationWarning = "Notifications will always display the default Signal icon and name."
    composeTestRule.onNodeWithTag(AppIconTutorialTestTags.SCROLLER).performScrollToNode(hasText(notificationWarning))
    composeTestRule.onNodeWithText(notificationWarning).assertIsDisplayed()
  }

  private fun setContent() {
    composeTestRule.setContent {
      AppIconTutorialScreen(onEvent = { events += it })
    }
  }
}
