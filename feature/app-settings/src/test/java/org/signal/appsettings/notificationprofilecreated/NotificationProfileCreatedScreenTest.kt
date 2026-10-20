/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.notificationprofilecreated

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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
class NotificationProfileCreatedScreenTest {

  companion object {
    private const val SCHEDULED = "Your profile will turn on and off automatically according to your schedule."
    private const val MANUAL = "You can turn your profile on or off manually via the menu on the chat list."
    private const val ADD_SCHEDULE = "Add a schedule in settings to automate your profile."
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<NotificationProfileCreatedEvent>()

  @Test
  fun `clicking done emits DoneClicked`() {
    setContent(NotificationProfileCreatedState(hasSchedule = true))

    composeTestRule.onNodeWithTag(NotificationProfileCreatedTestTags.BUTTON_DONE).performClick()

    assertThat(events).containsExactly(NotificationProfileCreatedEvent.DoneClicked)
  }

  @Test
  fun `a scheduled profile explains the schedule, then the manual menu`() {
    setContent(NotificationProfileCreatedState(hasSchedule = true))

    assertShown(SCHEDULED)
    assertShown(MANUAL)
    composeTestRule.onNodeWithText(ADD_SCHEDULE).assertDoesNotExist()
  }

  @Test
  fun `an unscheduled profile explains the manual menu, then how to add a schedule`() {
    setContent(NotificationProfileCreatedState(hasSchedule = false))

    assertShown(MANUAL)
    assertShown(ADD_SCHEDULE)
    composeTestRule.onNodeWithText(SCHEDULED).assertDoesNotExist()
  }

  @Test
  fun `no tips are shown until the profile has been read`() {
    setContent(NotificationProfileCreatedState())

    composeTestRule.onNodeWithText(MANUAL).assertDoesNotExist()
    composeTestRule.onNodeWithText(SCHEDULED).assertDoesNotExist()
    composeTestRule.onNodeWithText(ADD_SCHEDULE).assertDoesNotExist()
  }

  private fun assertShown(text: String) {
    composeTestRule.onNodeWithTag(NotificationProfileCreatedTestTags.SCROLLER).performScrollToNode(hasText(text))
    composeTestRule.onNodeWithText(text).assertIsDisplayed()
  }

  private fun setContent(state: NotificationProfileCreatedState) {
    composeTestRule.setContent {
      NotificationProfileCreatedScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }
}
