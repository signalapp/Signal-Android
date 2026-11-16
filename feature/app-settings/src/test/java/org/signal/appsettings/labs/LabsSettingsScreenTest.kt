/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.labs

import android.app.Application
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
class LabsSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<LabsSettingsEvent>()

  @Test
  fun `clicking back emits NavigateBackClicked`() {
    setContent(LabsSettingsState())

    composeTestRule.onNodeWithContentDescription("Go back").performClick()

    assertThat(events).containsExactly(LabsSettingsEvent.NavigateBackClicked)
  }

  @Test
  fun `each row emits its toggle event with the flipped value`() {
    val expected = mapOf(
      LabsSettingsTestTags.ROW_INDIVIDUAL_CHAT_PLAINTEXT_EXPORT to LabsSettingsEvent.IndividualChatPlaintextExportToggled(true),
      LabsSettingsTestTags.ROW_STORY_ARCHIVE to LabsSettingsEvent.StoryArchiveToggled(true),
      LabsSettingsTestTags.ROW_INCOGNITO to LabsSettingsEvent.IncognitoToggled(true),
      LabsSettingsTestTags.ROW_BETTER_SEARCH to LabsSettingsEvent.BetterSearchToggled(true),
      LabsSettingsTestTags.ROW_STARRED_MESSAGES to LabsSettingsEvent.StarredMessagesToggled(true),
      LabsSettingsTestTags.ROW_IMPROVED_MESSAGE_DELETION to LabsSettingsEvent.ImprovedMessageDeletionToggled(true)
    )

    setContent(LabsSettingsState())

    expected.forEach { (tag, event) ->
      events.clear()
      scrollTo(tag)
      composeTestRule.onNodeWithTag(tag).performClick()

      assertThat(events).containsExactly(event)
    }
  }

  @Test
  fun `toggled-on flags render as on`() {
    setContent(LabsSettingsState(storyArchive = true))

    switchIn(LabsSettingsTestTags.ROW_STORY_ARCHIVE).assertIsOn()
    switchIn(LabsSettingsTestTags.ROW_INCOGNITO).assertIsOff()
  }

  private fun switchIn(rowTag: String) = composeTestRule.onNode(isToggleable() and hasAnyAncestor(hasTestTag(rowTag)))

  private fun setContent(state: LabsSettingsState) {
    composeTestRule.setContent {
      LabsSettingsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(LabsSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }
}
