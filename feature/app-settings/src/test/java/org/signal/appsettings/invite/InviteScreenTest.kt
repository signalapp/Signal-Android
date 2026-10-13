/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.invite

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class InviteScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<InviteEvent>()

  @Test
  fun `screen displays the invite text from state`() {
    setContent(createState(inviteText = "Come chat with me on Signal"))

    composeTestRule.onNodeWithTag(InviteTestTags.INPUT_INVITE_TEXT)
      .assertIsDisplayed()
      .assertTextContains("Come chat with me on Signal")
  }

  @Test
  fun `screen displays the share row`() {
    setContent(createState())

    composeTestRule.onNodeWithTag(InviteTestTags.ROW_SHARE).assertIsDisplayed()
    composeTestRule.onNodeWithText("Share").assertIsDisplayed()
  }

  @Test
  fun `editing the invite text emits InviteTextChanged`() {
    setContent(createState())

    composeTestRule.onNodeWithTag(InviteTestTags.INPUT_INVITE_TEXT).performTextReplacement("Come chat with me on Signal")

    assertThat(events).contains(InviteEvent.InviteTextChanged("Come chat with me on Signal"))
  }

  @Test
  fun `typing into the invite text appends to the end`() {
    setContent(createState(inviteText = "Let's switch to Signal"))

    composeTestRule.onNodeWithTag(InviteTestTags.INPUT_INVITE_TEXT).performTextInput("!")

    assertThat(events).contains(InviteEvent.InviteTextChanged("Let's switch to Signal!"))
  }

  @Test
  fun `clicking share emits ShareClicked`() {
    setContent(createState())

    composeTestRule.onNodeWithTag(InviteTestTags.ROW_SHARE).performClick()

    assertThat(events).contains(InviteEvent.ShareClicked)
  }

  @Test
  fun `simply rendering the screen emits no events`() {
    setContent(createState())

    assertThat(events).isEmpty()
  }

  private fun setContent(state: InviteState) {
    composeTestRule.setContent {
      InviteScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun createState(
    inviteText: String = "Let's switch to Signal: https://signal.org/install"
  ): InviteState {
    return InviteState(inviteText = inviteText)
  }
}
