/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appearance.appicon

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
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
import org.signal.appsettings.R

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppIconSelectionScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val applicationLabelRule = ApplicationLabelRule(R.string.preferences__app_icon)

  private val events = mutableListOf<AppIconSelectionEvent>()

  @Test
  fun `clicking an icon emits IconClicked`() {
    setContent(AppIconSelectionState())

    scrollTo(AppIconSelectionTestTags.icon(AppIconPreset.WAVES))
    composeTestRule.onNodeWithContentDescription("Waves").performClick()

    assertThat(events).containsExactly(AppIconSelectionEvent.IconClicked(AppIconPreset.WAVES))
  }

  @Test
  fun `clicking the caveat emits LearnMoreClicked`() {
    setContent(AppIconSelectionState())

    composeTestRule.onNodeWithTag(AppIconSelectionTestTags.LEARN_MORE).performClick()

    assertThat(events).containsExactly(AppIconSelectionEvent.LearnMoreClicked)
  }

  @Test
  fun `clicking back emits NavigateBackClicked`() {
    setContent(AppIconSelectionState())

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(events).containsExactly(AppIconSelectionEvent.NavigateBackClicked)
  }

  @Test
  fun `every preset is shown`() {
    setContent(AppIconSelectionState())

    AppIconPreset.entries.forEach { preset ->
      scrollTo(AppIconSelectionTestTags.icon(preset))
      composeTestRule.onNodeWithTag(AppIconSelectionTestTags.icon(preset)).assertIsDisplayed()
    }
  }

  @Test
  fun `confirming the dialog emits ChangeConfirmed`() {
    setContent(AppIconSelectionState(dialog = AppIconSelectionState.Dialog.ConfirmIconChange(AppIconPreset.WEATHER)))

    composeTestRule.onNodeWithTag(AppIconSelectionTestTags.DIALOG_CONFIRM).performClick()

    assertThat(events).containsExactly(AppIconSelectionEvent.ChangeConfirmed)
  }

  @Test
  fun `cancelling the dialog emits DialogDismissed`() {
    setContent(AppIconSelectionState(dialog = AppIconSelectionState.Dialog.ConfirmIconChange(AppIconPreset.WEATHER)))

    composeTestRule.onNodeWithTag(AppIconSelectionTestTags.DIALOG_CANCEL).performClick()

    assertThat(events).containsExactly(AppIconSelectionEvent.DialogDismissed)
  }

  private fun setContent(state: AppIconSelectionState) {
    composeTestRule.setContent {
      AppIconSelectionScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(AppIconSelectionTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }
}
