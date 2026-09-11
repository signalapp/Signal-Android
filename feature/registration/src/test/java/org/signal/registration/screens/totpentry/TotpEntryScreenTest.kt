/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.totpentry

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
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
import org.signal.uicomponents.codeentryfield.CodeEntryFieldEvents
import org.signal.uicomponents.codeentryfield.CodeEntryFieldTestTags

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
  fun `screen displays the code field`() {
    setContent(TotpEntryState())

    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.ROOT).assertIsDisplayed()
  }

  @Test
  fun `entering a digit forwards a code field event`() {
    setContent(TotpEntryState())

    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.digit(0)).performTextInput("4")
    composeTestRule.waitForIdle()

    assertThat(events).contains(TotpEntryScreenEvents.CodeEntryEvent(CodeEntryFieldEvents.DigitChanged(0, "4")))
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
