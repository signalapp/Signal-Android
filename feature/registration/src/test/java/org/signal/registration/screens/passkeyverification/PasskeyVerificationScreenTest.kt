/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.passkeyverification

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
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
class PasskeyVerificationScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<PasskeyVerificationScreenEvents>()

  @Test
  fun `screen displays title and subtitle`() {
    setContent(PasskeyVerificationState())

    composeTestRule.onNodeWithText("Two-factor authentication").assertIsDisplayed()
    composeTestRule.onNodeWithText("To continue, verify your account with your passkey.").assertIsDisplayed()
  }

  @Test
  fun `clicking continue emits ContinueClicked`() {
    setContent(PasskeyVerificationState())

    composeTestRule.onNodeWithTag(TestTags.PASSKEY_VERIFICATION_CONTINUE_BUTTON).performClick()

    assertThat(events).contains(PasskeyVerificationScreenEvents.ContinueClicked)
  }

  @Test
  fun `clicking cancel emits CancelClicked`() {
    setContent(PasskeyVerificationState())

    composeTestRule.onNodeWithTag(TestTags.PASSKEY_VERIFICATION_CANCEL_BUTTON).performClick()

    assertThat(events).contains(PasskeyVerificationScreenEvents.CancelClicked)
  }

  @Test
  fun `simply rendering the screen emits no events`() {
    setContent(PasskeyVerificationState())

    assertThat(events).isEmpty()
  }

  @Test
  fun `the passkey error dialog is shown when the state asks for it`() {
    setContent(PasskeyVerificationState(showPasskeyError = true))

    composeTestRule.onNodeWithText("Something went wrong with your passkey. Please try again.").assertIsDisplayed()
  }

  @Test
  fun `dismissing the passkey error dialog emits PasskeyErrorDismissed`() {
    setContent(PasskeyVerificationState(showPasskeyError = true))

    composeTestRule.onNodeWithText("OK").performClick()

    assertThat(events).contains(PasskeyVerificationScreenEvents.PasskeyErrorDismissed)
  }

  @Test
  fun `no passkey error dialog is shown by default`() {
    setContent(PasskeyVerificationState())

    composeTestRule.onNodeWithTag(TestTags.PASSKEY_VERIFICATION_ERROR_DIALOG).assertDoesNotExist()
  }

  private fun setContent(state: PasskeyVerificationState) {
    composeTestRule.setContent {
      SignalTheme {
        PasskeyVerificationScreen(
          state = state,
          onEvent = { events += it }
        )
      }
    }
  }
}
