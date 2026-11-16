/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernamecolorpicker

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
import org.signal.appsettings.usernamelinks.UsernameQrCodeColorScheme

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h1600dp")
class UsernameLinkQrColorPickerScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<UsernameLinkQrColorPickerEvent>()

  @Test
  fun `the username is shown`() {
    setContent(UsernameLinkQrColorPickerState(username = "parker.42"))

    composeTestRule.onNodeWithText("parker.42").assertIsDisplayed()
  }

  @Test
  fun `clicking a color emits ColorSelected`() {
    setContent(UsernameLinkQrColorPickerState())

    composeTestRule.onNodeWithTag(UsernameLinkQrColorPickerTestTags.color(UsernameQrCodeColorScheme.Purple)).performClick()

    assertThat(events).containsExactly(UsernameLinkQrColorPickerEvent.ColorSelected(UsernameQrCodeColorScheme.Purple))
  }

  @Test
  fun `clicking done emits DoneClicked`() {
    setContent(UsernameLinkQrColorPickerState())

    composeTestRule.onNodeWithTag(UsernameLinkQrColorPickerTestTags.BUTTON_DONE).performClick()

    assertThat(events).containsExactly(UsernameLinkQrColorPickerEvent.DoneClicked)
  }

  @Test
  fun `clicking back emits NavigateBackClicked`() {
    setContent(UsernameLinkQrColorPickerState())

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(events).containsExactly(UsernameLinkQrColorPickerEvent.NavigateBackClicked)
  }

  @Test
  fun `every color is offered`() {
    setContent(UsernameLinkQrColorPickerState())

    UsernameQrCodeColorScheme.entries.forEach { colorScheme ->
      composeTestRule.onNodeWithTag(UsernameLinkQrColorPickerTestTags.color(colorScheme)).assertIsDisplayed()
    }
  }

  private fun setContent(state: UsernameLinkQrColorPickerState) {
    composeTestRule.setContent {
      UsernameLinkQrColorPickerScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }
}
