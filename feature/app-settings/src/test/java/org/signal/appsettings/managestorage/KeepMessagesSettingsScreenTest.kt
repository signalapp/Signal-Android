/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.storage.KeepMessagesDuration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class KeepMessagesSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<KeepMessagesSettingsEvent>()

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    setContent(KeepMessagesSettingsState())

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(events).containsExactly(KeepMessagesSettingsEvent.NavigateBackClicked)
  }

  @Test
  fun `picking a duration emits DurationSelected`() {
    setContent(KeepMessagesSettingsState())

    composeTestRule.onNodeWithTag(KeepMessagesSettingsTestTags.ROW_DURATION_PREFIX + KeepMessagesDuration.THIRTY_DAYS.name).performClick()

    assertThat(events).containsExactly(KeepMessagesSettingsEvent.DurationSelected(KeepMessagesDuration.THIRTY_DAYS))
  }

  @Test
  fun `confirming the duration emits only DurationConfirmed`() {
    setContent(KeepMessagesSettingsState(dialog = KeepMessagesSettingsState.Dialog.ConfirmDuration(KeepMessagesDuration.THIRTY_DAYS)))

    composeTestRule.onNodeWithText("older than 30 days", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Delete").performClick()

    assertThat(events).containsExactly(KeepMessagesSettingsEvent.DurationConfirmed)
  }

  @Test
  fun `cancelling the confirmation emits DialogDismissed`() {
    setContent(KeepMessagesSettingsState(dialog = KeepMessagesSettingsState.Dialog.ConfirmDuration(KeepMessagesDuration.THIRTY_DAYS)))

    composeTestRule.onNodeWithText("Cancel").performClick()

    assertThat(events).containsExactly(KeepMessagesSettingsEvent.DialogDismissed)
  }

  private fun setContent(state: KeepMessagesSettingsState) {
    composeTestRule.setContent {
      KeepMessagesSettingsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }
}
