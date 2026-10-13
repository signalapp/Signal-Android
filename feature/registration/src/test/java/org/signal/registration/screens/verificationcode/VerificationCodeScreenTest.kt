/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import org.signal.uicomponents.codeentryfield.CodeEntryFieldEvents
import org.signal.uicomponents.codeentryfield.CodeEntryFieldState
import org.signal.uicomponents.codeentryfield.CodeEntryFieldTestTags

/**
 * Tests for VerificationCodeScreen that validate event emissions and UI behavior.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VerificationCodeScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

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
    composeTestRule.onNodeWithText("Verification code").assertIsDisplayed()
  }

  @Test
  fun `screen displays the code field`() {
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
    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.ROOT).assertIsDisplayed()
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
  fun `typing forwards code field events`() {
    // Given
    val emittedEvents = mutableListOf<VerificationCodeScreenEvents>()

    composeTestRule.setContent {
      SignalTheme {
        VerificationCodeScreen(
          state = VerificationCodeState(),
          onEvent = { emittedEvents.add(it) }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.ROOT).performTextInput("1")
    composeTestRule.waitForIdle()

    // Then
    assertThat(emittedEvents).contains(VerificationCodeScreenEvents.CodeEntryEvent(CodeEntryFieldEvents.CodeChanged("1")))
  }

  @Test
  fun `the code field is disabled while submitting`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        VerificationCodeScreen(
          state = VerificationCodeState(isSubmittingCode = true),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.ROOT).assertIsNotEnabled()
  }

  @Test
  fun `an auto-filled code is placed in the code field`() {
    // Given
    val emittedEvents = mutableListOf<VerificationCodeScreenEvents>()

    composeTestRule.setContent {
      SignalTheme {
        VerificationCodeScreen(
          state = VerificationCodeState(codeEntry = CodeEntryFieldState(code = "123456", pendingOverwrite = "123456")),
          onEvent = { emittedEvents.add(it) }
        )
      }
    }

    // When
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      emittedEvents.contains(VerificationCodeScreenEvents.CodeEntryEvent(CodeEntryFieldEvents.OverwriteApplied))
    }
    composeTestRule.waitForIdle()

    // Then
    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.digit(0), useUnmergedTree = true).assertTextEquals("1")
    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.digit(5), useUnmergedTree = true).assertTextEquals("6")
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
    composeTestRule.onNodeWithText("Resend Code").assertIsDisplayed()
    composeTestRule.onNodeWithText("Call me instead").assertIsDisplayed()
  }
}
