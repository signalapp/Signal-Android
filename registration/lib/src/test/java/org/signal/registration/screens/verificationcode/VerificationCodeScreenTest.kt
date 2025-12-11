/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.test.TestTags

/**
 * Tests for VerificationCodeScreen that validate event emissions and UI behavior.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VerificationCodeScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `screen displays title`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        VerificationCodeScreen(
          state = VerificationCodeState(),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithText("Enter verification code").assertIsDisplayed()
  }

  @Test
  fun `screen displays all six digit fields`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        VerificationCodeScreen(
          state = VerificationCodeState(),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_DIGIT_0).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_DIGIT_1).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_DIGIT_2).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_DIGIT_3).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_DIGIT_4).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_DIGIT_5).assertIsDisplayed()
  }

  @Test
  fun `clicking wrong number emits WrongNumber event`() {
    // Given
    var emittedEvent: VerificationCodeScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        VerificationCodeScreen(
          state = VerificationCodeState(),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_WRONG_NUMBER_BUTTON).performClick()

    // Then
    assert(emittedEvent == VerificationCodeScreenEvents.WrongNumber)
  }

  @Test
  fun `clicking resend SMS emits ResendSms event`() {
    // Given
    var emittedEvent: VerificationCodeScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        VerificationCodeScreen(
          state = VerificationCodeState(),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_RESEND_SMS_BUTTON).performClick()

    // Then
    assert(emittedEvent == VerificationCodeScreenEvents.ResendSms)
  }

  @Test
  fun `clicking call me emits CallMe event`() {
    // Given
    var emittedEvent: VerificationCodeScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        VerificationCodeScreen(
          state = VerificationCodeState(),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_CALL_ME_BUTTON).performClick()

    // Then
    assert(emittedEvent == VerificationCodeScreenEvents.CallMe)
  }

  @Test
  fun `entering complete code emits CodeEntered event`() {
    // Given
    var emittedEvent: VerificationCodeScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        VerificationCodeScreen(
          state = VerificationCodeState(),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When - enter all 6 digits
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_DIGIT_0).performTextInput("1")
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_DIGIT_1).performTextInput("2")
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_DIGIT_2).performTextInput("3")
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_DIGIT_3).performTextInput("4")
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_DIGIT_4).performTextInput("5")
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_DIGIT_5).performTextInput("6")

    composeTestRule.waitForIdle()

    // Then
    assert(emittedEvent is VerificationCodeScreenEvents.CodeEntered) {
      "Expected CodeEntered event but got $emittedEvent"
    }
    assert((emittedEvent as VerificationCodeScreenEvents.CodeEntered).code == "123456") {
      "Expected code '123456' but got ${(emittedEvent as VerificationCodeScreenEvents.CodeEntered).code}"
    }
  }

  @Test
  fun `screen displays all action buttons`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        VerificationCodeScreen(
          state = VerificationCodeState(),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithText("Wrong number?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Resend SMS").assertIsDisplayed()
    composeTestRule.onNodeWithText("Call me instead").assertIsDisplayed()
  }
}
