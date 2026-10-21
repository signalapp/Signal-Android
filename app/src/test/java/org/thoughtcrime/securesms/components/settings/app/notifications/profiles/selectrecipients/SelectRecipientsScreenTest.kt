/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.selectrecipients

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import org.signal.core.models.recipients.RecipientId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SelectRecipientsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<SelectRecipientsEvent>()

  @Test
  fun `the screen is titled for allowed notifications`() {
    setContent(SelectRecipientsState())
    composeTestRule.onNodeWithText("Allowed notifications").assertExists()
  }

  @Test
  fun `add is disabled with nobody picked`() {
    setContent(SelectRecipientsState())
    composeTestRule.onNodeWithTag(SelectRecipientsTestTags.BUTTON_ADD).assertIsNotEnabled()
  }

  @Test
  fun `add gives way to a spinner while saving`() {
    setContent(SelectRecipientsState(selection = setOf(RecipientId.from(1L)), isSaving = true))
    composeTestRule.onNodeWithTag(SelectRecipientsTestTags.BUTTON_ADD).assertDoesNotExist()
  }

  @Test
  fun `add is enabled when the profile already has members`() {
    setContent(SelectRecipientsState(currentMembers = setOf(RecipientId.from(1L))))
    composeTestRule.onNodeWithTag(SelectRecipientsTestTags.BUTTON_ADD).assertIsEnabled()
  }

  @Test
  fun `clicking add emits AddClicked`() {
    setContent(SelectRecipientsState(selection = setOf(RecipientId.from(1L))))

    composeTestRule.onNodeWithTag(SelectRecipientsTestTags.BUTTON_ADD).assertIsEnabled().performClick()

    assertThat(events).containsExactly(SelectRecipientsEvent.AddClicked)
  }

  @Test
  fun `the contact picker fills the screen`() {
    setContent(SelectRecipientsState())
    composeTestRule.onNodeWithTag(SelectRecipientsTestTags.PICKER).assertExists()
  }

  @Test
  fun `clicking back emits NavigateBackClicked`() {
    setContent(SelectRecipientsState())

    composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

    assertThat(events).containsExactly(SelectRecipientsEvent.NavigateBackClicked)
  }

  private fun setContent(state: SelectRecipientsState) {
    composeTestRule.setContent {
      SelectRecipientsScreen(
        state = state,
        onEvent = { events += it },
        contactPicker = { _, modifier -> Box(modifier = modifier) }
      )
    }
  }
}
