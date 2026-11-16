/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.expiretimer

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
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
import org.signal.core.ui.compose.Dialogs
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExpireTimerSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<ExpireTimerSettingsEvent>()

  @Test
  fun `clicking a preset emits TimerSelected`() {
    setContent(ExpireTimerSettingsState())

    scrollTo(ExpireTimerSettingsTestTags.preset(1.days))
    composeTestRule.onNodeWithTag(ExpireTimerSettingsTestTags.preset(1.days)).performClick()

    assertThat(events).containsExactly(ExpireTimerSettingsEvent.TimerSelected(1.days))
  }

  @Test
  fun `clicking custom time emits CustomTimeClicked`() {
    setContent(ExpireTimerSettingsState())

    scrollTo(ExpireTimerSettingsTestTags.ROW_CUSTOM_TIME)
    composeTestRule.onNodeWithTag(ExpireTimerSettingsTestTags.ROW_CUSTOM_TIME).performClick()

    assertThat(events).containsExactly(ExpireTimerSettingsEvent.CustomTimeClicked)
  }

  @Test
  fun `a custom timer selects the custom row and shows its value`() {
    setContent(ExpireTimerSettingsState(initialTimer = 3.hours))

    scrollTo(ExpireTimerSettingsTestTags.ROW_CUSTOM_TIME)
    composeTestRule.onNodeWithText("3 hours").assertIsDisplayed()
  }

  @Test
  fun `clicking save emits SaveClicked`() {
    setContent(ExpireTimerSettingsState())

    composeTestRule.onNodeWithTag(ExpireTimerSettingsTestTags.BUTTON_SAVE).performClick()

    assertThat(events).containsExactly(ExpireTimerSettingsEvent.SaveClicked)
  }

  @Test
  fun `save is replaced by progress while saving`() {
    setContent(ExpireTimerSettingsState(isSaving = true))

    composeTestRule.onNodeWithTag(ExpireTimerSettingsTestTags.BUTTON_SAVE).assertDoesNotExist()
  }

  @Test
  fun `cancelling the custom time dialog emits DialogDismissed`() {
    setContent(ExpireTimerSettingsState(dialog = ExpireTimerSettingsState.Dialog.CustomTimer))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON).performClick()

    assertThat(events).containsExactly(ExpireTimerSettingsEvent.DialogDismissed)
  }

  @Test
  fun `confirming the custom time dialog emits CustomTimerSelected`() {
    setContent(ExpireTimerSettingsState(initialTimer = 3.hours, dialog = ExpireTimerSettingsState.Dialog.CustomTimer))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(events).containsExactly(ExpireTimerSettingsEvent.CustomTimerSelected(3.hours))
  }

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    setContent(ExpireTimerSettingsState())

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(events).containsExactly(ExpireTimerSettingsEvent.NavigateBackClicked)
  }

  @Test
  fun `a chat's timer is described as applying to this chat`() {
    setContent(ExpireTimerSettingsState(mode = ExpireTimerSettingsState.Mode.RECIPIENT))

    composeTestRule.onNodeWithText("When enabled, new messages sent and received in this chat will disappear after they have been seen.").assertIsDisplayed()
  }

  private fun setContent(state: ExpireTimerSettingsState) {
    composeTestRule.setContent {
      ExpireTimerSettingsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(ExpireTimerSettingsTestTags.SCROLLER).performScrollToNode(hasTestTag(testTag))
  }
}
