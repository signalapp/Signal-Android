/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.totpentry

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.test.TestTags

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TotpEntryScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<TotpEntryScreenEvents>()

  @Test
  fun `screen displays title and subtitle`() {
    setContent(TotpEntryState())

    composeTestRule.onNodeWithText("Two-factor authentication").assertIsDisplayed()
    composeTestRule.onNodeWithText("To continue, enter the 6-digit code from your authenticator app.").assertIsDisplayed()
  }

  @Test
  fun `screen displays all six digit fields`() {
    setContent(TotpEntryState())

    composeTestRule.onNodeWithTag(TestTags.TOTP_ENTRY_DIGIT_0).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.TOTP_ENTRY_DIGIT_1).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.TOTP_ENTRY_DIGIT_2).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.TOTP_ENTRY_DIGIT_3).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.TOTP_ENTRY_DIGIT_4).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.TOTP_ENTRY_DIGIT_5).assertIsDisplayed()
  }

  @Test
  fun `entering a digit emits DigitChanged for that field`() {
    setContent(TotpEntryState())

    composeTestRule.onNodeWithTag(TestTags.TOTP_ENTRY_DIGIT_0).performTextInput("4")
    composeTestRule.onNodeWithTag(TestTags.TOTP_ENTRY_DIGIT_1).performTextInput("1")
    composeTestRule.waitForIdle()

    assertThat(events).contains(TotpEntryScreenEvents.DigitChanged(0, "4"))
    assertThat(events).contains(TotpEntryScreenEvents.DigitChanged(1, "1"))
  }

  @Test
  fun `pasting into a field emits DigitChanged with the raw text`() {
    setContent(TotpEntryState())

    composeTestRule.onNodeWithTag(TestTags.TOTP_ENTRY_DIGIT_0).performTextInput("418-372")
    composeTestRule.waitForIdle()

    assertThat(events).contains(TotpEntryScreenEvents.DigitChanged(0, "418-372"))
  }

  @Test
  fun `screen renders the digits from state`() {
    setContent(TotpEntryState(digits = listOf("4", "1", "8", "3", "7", "2")))

    composeTestRule.onNodeWithTag(TestTags.TOTP_ENTRY_DIGIT_0).assertTextEquals("4")
    composeTestRule.onNodeWithTag(TestTags.TOTP_ENTRY_DIGIT_5).assertTextEquals("2")
  }

  @Test
  fun `clicking cancel emits CancelClicked`() {
    setContent(TotpEntryState())

    composeTestRule.onNodeWithTag(TestTags.TOTP_ENTRY_CANCEL_BUTTON).performClick()

    assertThat(events).contains(TotpEntryScreenEvents.CancelClicked)
  }

  private fun setContent(state: TotpEntryState) {
    composeTestRule.setContent {
      SignalTheme {
        TotpEntryScreen(
          state = state,
          onEvent = { events += it }
        )
      }
    }
  }
}
