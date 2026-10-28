/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.managestorage.ChatLengthLimitSettingsState.Dialog

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChatLengthLimitSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<ChatLengthLimitSettingsEvent>()

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    setContent(ChatLengthLimitSettingsState())

    composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

    assertThat(events).containsExactly(ChatLengthLimitSettingsEvent.NavigateBackClicked)
  }

  @Test
  fun `picking a preset emits LimitSelected`() {
    setContent(ChatLengthLimitSettingsState())

    composeTestRule.onNodeWithTag(ChatLengthLimitSettingsTestTags.ROW_PRESET_PREFIX + 500).performClick()

    assertThat(events).containsExactly(ChatLengthLimitSettingsEvent.LimitSelected(500))
  }

  @Test
  fun `a limit that is not a preset is shown on the custom row`() {
    setContent(ChatLengthLimitSettingsState(lengthLimit = 1234))

    composeTestRule.onNode(hasText("1,234 messages") and hasAnyAncestor(hasTestTag(ChatLengthLimitSettingsTestTags.ROW_CUSTOM)), useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun `a preset limit leaves the custom row without a label`() {
    setContent(ChatLengthLimitSettingsState(lengthLimit = 1000))

    composeTestRule.onNode(hasText("1,000 messages") and hasAnyAncestor(hasTestTag(ChatLengthLimitSettingsTestTags.ROW_CUSTOM)), useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `clicking custom emits CustomClicked`() {
    setContent(ChatLengthLimitSettingsState())

    composeTestRule.onNodeWithTag(ChatLengthLimitSettingsTestTags.ROW_CUSTOM).performClick()

    assertThat(events).containsExactly(ChatLengthLimitSettingsEvent.CustomClicked)
  }

  @Test
  fun `the custom limit dialog only accepts a number`() {
    setContent(ChatLengthLimitSettingsState(dialog = Dialog.CustomLimit(initialLimit = 123)))

    composeTestRule.onNodeWithTag(ChatLengthLimitSettingsTestTags.FIELD_CUSTOM_LIMIT).performTextClearance()
    composeTestRule.onNodeWithTag(ChatLengthLimitSettingsTestTags.BUTTON_CUSTOM_LIMIT_OK).assertIsNotEnabled()

    composeTestRule.onNodeWithTag(ChatLengthLimitSettingsTestTags.FIELD_CUSTOM_LIMIT).performTextInput("4a2")
    composeTestRule.onNodeWithTag(ChatLengthLimitSettingsTestTags.BUTTON_CUSTOM_LIMIT_OK).performClick()

    assertThat(events).containsExactly(ChatLengthLimitSettingsEvent.CustomLimitEntered(42))
  }

  @Test
  fun `cancelling the custom limit dialog emits DialogDismissed`() {
    setContent(ChatLengthLimitSettingsState(dialog = Dialog.CustomLimit(initialLimit = null)))

    composeTestRule.onNodeWithText("Cancel").performClick()

    assertThat(events).containsExactly(ChatLengthLimitSettingsEvent.DialogDismissed)
  }

  @Test
  fun `confirming a limit emits only LimitConfirmed`() {
    setContent(ChatLengthLimitSettingsState(dialog = Dialog.ConfirmLimit(500)))

    composeTestRule.onNodeWithText("500 most recent messages", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Delete").performClick()

    assertThat(events).containsExactly(ChatLengthLimitSettingsEvent.LimitConfirmed)
  }

  private fun setContent(state: ChatLengthLimitSettingsState) {
    composeTestRule.setContent {
      ChatLengthLimitSettingsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }
}
