/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediasend.test.TestTags

/**
 * Covers the send affordance's two gestures: a tap advances the flow, while a long press offers the schedule menu when
 * the flow allows scheduling. Also covers when the row falls back to its placeholder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddAMessageRowTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<MediaEditScreenEvents>()
  private var nextClicks = 0

  @Test
  fun `Given scheduling is allowed, when the send button is tapped, then the flow advances without scheduling`() {
    setContent(canScheduleSend = true)

    composeTestRule.onNodeWithTag(TestTags.ADD_A_MESSAGE_NEXT_BUTTON).performClick()

    assertEquals(1, nextClicks)
    assertNull(events.filterIsInstance<MediaEditScreenEvents.ScheduleSendClick>().firstOrNull())
  }

  @Test
  fun `Given scheduling is allowed, when the send button is long pressed, then a time can be scheduled`() {
    setContent(canScheduleSend = true)

    composeTestRule.onNodeWithTag(TestTags.ADD_A_MESSAGE_NEXT_BUTTON).performTouchInput { longClick() }
    composeTestRule.onNodeWithTag(TestTags.SCHEDULE_SEND_PICK_TIME_OPTION).performClick()

    assertEquals(
      MediaEditScreenEvents.ScheduleSendClick(ScheduleSendOption.PickTime),
      events.filterIsInstance<MediaEditScreenEvents.ScheduleSendClick>().single()
    )
    assertEquals(0, nextClicks)
  }

  @Test
  fun `Given scheduling is not allowed, when the send button is long pressed, then nothing is offered`() {
    setContent(canScheduleSend = false)

    composeTestRule.onNodeWithTag(TestTags.ADD_A_MESSAGE_NEXT_BUTTON).performTouchInput { longClick() }

    composeTestRule.onNodeWithTag(TestTags.SCHEDULE_SEND_PICK_TIME_OPTION).assertDoesNotExist()
    assertEquals(emptyList<MediaEditScreenEvents>(), events)
  }

  @Test
  fun `Given a blank message, when the row is displayed, then the placeholder is shown`() {
    setContent(message = "   ")

    composeTestRule.onNodeWithText("Message").assertExists()
  }

  @Test
  fun `Given a message, when the row is displayed, then the message is shown`() {
    setContent(message = "Check this out")

    composeTestRule.onNodeWithText("Check this out").assertExists()
  }

  @Test
  fun `Given a reply flow with no message, when the row is displayed, then the reply placeholder is shown`() {
    setContent(isReply = true)

    composeTestRule.onNodeWithText("Add a reply").assertExists()
  }

  @Test
  fun `Given a reply flow with a message, when the row is displayed, then the message is shown`() {
    setContent(message = "Check this out", isReply = true)

    composeTestRule.onNodeWithText("Check this out").assertExists()
  }

  private fun setContent(canScheduleSend: Boolean = false, message: CharSequence? = null, isReply: Boolean = false) {
    composeTestRule.setContent {
      SignalTheme {
        AddAMessageRow(
          message = message,
          onEvent = { events += it },
          onNextClick = { nextClicks++ },
          canScheduleSend = canScheduleSend,
          isReply = isReply
        )
      }
    }
  }
}
