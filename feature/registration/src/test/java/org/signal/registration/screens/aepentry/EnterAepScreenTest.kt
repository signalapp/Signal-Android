/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.test.TestTags

/**
 * Tests for EnterAepScreen that validate event emissions.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EnterAepScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `screen displays screen, input, and next button`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        EnterAepScreen(
          state = EnterAepState(),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.ENTER_AEP_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.ENTER_AEP_INPUT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.ENTER_AEP_NEXT_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when No Recovery Key is clicked, Cancel event is emitted`() {
    // Given
    var emittedEvent: EnterAepEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        EnterAepScreen(
          state = EnterAepState(),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.ENTER_AEP_NO_KEY_BUTTON).performClick()

    // Then
    assert(emittedEvent == EnterAepEvents.Cancel)
  }

  @Test
  fun `Next button is disabled when backup key is not valid`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        EnterAepScreen(
          state = EnterAepState(),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.ENTER_AEP_NEXT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun `when Next is clicked and backup key is valid, Submit event is emitted`() {
    // Given
    var emittedEvent: EnterAepEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        EnterAepScreen(
          state = EnterAepState(
            isBackupKeyValid = true,
            aepValidationError = null,
            isRegistering = false
          ),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.ENTER_AEP_NEXT_BUTTON).performClick()

    // Then
    assert(emittedEvent == EnterAepEvents.Submit)
  }
}
