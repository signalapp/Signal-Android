/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.editprofile

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class AvatarPreviewScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<AvatarPreviewEvent>()

  private fun setContent(toolbarVisible: Boolean = true) {
    composeTestRule.setContent {
      AvatarPreviewScreen(title = "Peter Parker", toolbarVisible = toolbarVisible, onEvent = { events += it })
    }
  }

  @Test
  fun `the back button navigates back`() {
    setContent()

    composeTestRule.onNodeWithTag(AvatarPreviewTestTags.BUTTON_BACK).performClick()

    assertThat(events).containsExactly(AvatarPreviewEvent.NavigateBackClicked)
  }

  @Test
  fun `tapping the avatar toggles the toolbar`() {
    setContent()

    composeTestRule.onNodeWithTag(AvatarPreviewTestTags.AVATAR).performClick()
    composeTestRule.mainClock.advanceTimeBy(500)

    assertThat(events).containsExactly(AvatarPreviewEvent.AvatarClicked)
  }

  @Test
  fun `dragging the avatar away dismisses it`() {
    setContent()

    composeTestRule.onNodeWithTag(AvatarPreviewTestTags.AVATAR).performTouchInput { swipeDown(startY = centerY, endY = bottom, durationMillis = 300) }
    composeTestRule.waitForIdle()

    assertThat(events).containsExactly(AvatarPreviewEvent.AvatarDraggedAway)
  }

  @Test
  fun `the toolbar shows the user's name`() {
    setContent()

    composeTestRule.onNodeWithTag(AvatarPreviewTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun `a hidden toolbar has no back button`() {
    setContent(toolbarVisible = false)

    composeTestRule.onNodeWithTag(AvatarPreviewTestTags.BUTTON_BACK).assertDoesNotExist()
  }
}
