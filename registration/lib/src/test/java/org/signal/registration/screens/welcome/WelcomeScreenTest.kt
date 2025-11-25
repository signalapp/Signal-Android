/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.welcome

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.test.TestTags

/**
 * Tests for WelcomeScreen that validate event emissions.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WelcomeScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `when Get Started is clicked, Continue event is emitted`() {
    // Given
    var emittedEvent: WelcomeScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        WelcomeScreen(
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.WELCOME_GET_STARTED_BUTTON).performClick()

    // Then
    assert(emittedEvent == WelcomeScreenEvents.Continue)
  }

  @Test
  fun `when Restore or transfer is clicked, bottom sheet is shown`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        WelcomeScreen(onEvent = {})
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_OR_TRANSFER_BUTTON).performClick()

    // Then - bottom sheet options should be visible
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_HAS_OLD_PHONE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_NO_OLD_PHONE_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when I have my old phone is clicked, HasOldPhone event is emitted`() {
    // Given
    var emittedEvent: WelcomeScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        WelcomeScreen(
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_OR_TRANSFER_BUTTON).performClick()
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_HAS_OLD_PHONE_BUTTON).performClick()

    // Then
    assert(emittedEvent == WelcomeScreenEvents.HasOldPhone)
  }

  @Test
  fun `when I don't have my old phone is clicked, DoesNotHaveOldPhone event is emitted`() {
    // Given
    var emittedEvent: WelcomeScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        WelcomeScreen(
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_OR_TRANSFER_BUTTON).performClick()
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_NO_OLD_PHONE_BUTTON).performClick()

    // Then
    assert(emittedEvent == WelcomeScreenEvents.DoesNotHaveOldPhone)
  }

  @Test
  fun `screen displays welcome message`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        WelcomeScreen(onEvent = {})
      }
    }

    // Then
    composeTestRule.onNodeWithText("Welcome to Signal").assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.WELCOME_GET_STARTED_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_OR_TRANSFER_BUTTON).assertIsDisplayed()
  }
}
