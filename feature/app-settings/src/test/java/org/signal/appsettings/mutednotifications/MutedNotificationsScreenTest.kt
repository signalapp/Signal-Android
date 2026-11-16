/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.mutednotifications

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.R

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MutedNotificationsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val context: Application = ApplicationProvider.getApplicationContext()

  private val events = mutableListOf<MutedNotificationsEvent>()

  @Test
  fun `the toolbar's navigation icon emits NavigateBackClicked`() {
    setContent(MutedNotificationsState())

    composeTestRule.onNodeWithContentDescription(context.getString(org.signal.core.ui.R.string.CloseButton__content_description)).performClick()

    assertThat(events).containsExactly(MutedNotificationsEvent.NavigateBackClicked)
  }

  @Test
  fun `flipping calls emits CallsToggled`() {
    setContent(MutedNotificationsState(allowCalls = false))

    composeTestRule.onNodeWithTag(MutedNotificationsTestTags.ROW_CALLS).performClick()

    assertThat(events).containsExactly(MutedNotificationsEvent.CallsToggled(true))
  }

  @Test
  fun `flipping mentions emits MentionsToggled`() {
    setContent(MutedNotificationsState(allowMentions = true))

    composeTestRule.onNodeWithTag(MutedNotificationsTestTags.ROW_MENTIONS).performClick()

    assertThat(events).containsExactly(MutedNotificationsEvent.MentionsToggled(false))
  }

  @Test
  fun `flipping replies emits RepliesToggled`() {
    setContent(MutedNotificationsState(allowReplies = false))

    composeTestRule.onNodeWithTag(MutedNotificationsTestTags.ROW_REPLIES).performClick()

    assertThat(events).containsExactly(MutedNotificationsEvent.RepliesToggled(true))
  }

  @Test
  fun `mentions and replies are hidden where they don't apply`() {
    setContent(MutedNotificationsState(isGlobal = false, showMentions = false, showReplies = false))

    composeTestRule.onNodeWithTag(MutedNotificationsTestTags.ROW_CALLS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MutedNotificationsTestTags.ROW_MENTIONS).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MutedNotificationsTestTags.ROW_REPLIES).assertDoesNotExist()
  }

  @Test
  fun `the defaults describe every muted chat`() {
    setContent(MutedNotificationsState(isGlobal = true))

    composeTestRule.onNodeWithText(context.getString(R.string.MutedNotificationsFragment__calls_body_global)).assertIsDisplayed()
  }

  @Test
  fun `a chat's own settings describe that chat`() {
    setContent(MutedNotificationsState(isGlobal = false))

    composeTestRule.onNodeWithText(context.getString(R.string.MutedNotificationsFragment__calls_body)).assertIsDisplayed()
  }

  private fun setContent(state: MutedNotificationsState) {
    composeTestRule.setContent {
      MutedNotificationsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }
}
