/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pincreation

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
 * Tests for PinCreationScreen that validate event emissions.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PinCreationScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `screen displays input and next button`() {
    composeTestRule.setContent {
      SignalTheme {
        PinCreationScreen(
          state = PinCreationState(),
          onEvent = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_INPUT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_NEXT_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when toggle keyboard is clicked, ToggleKeyboard event is emitted`() {
    var emittedEvent: PinCreationScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        PinCreationScreen(
          state = PinCreationState(),
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_TOGGLE_KEYBOARD_BUTTON).performClick()

    assert(emittedEvent == PinCreationScreenEvents.ToggleKeyboard)
  }

  @Test
  fun `next is disabled when pin is empty`() {
    composeTestRule.setContent {
      SignalTheme {
        PinCreationScreen(
          state = PinCreationState(),
          onEvent = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_NEXT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun `when a pin is entered and next is clicked, PinSubmitted event is emitted`() {
    var emittedEvent: PinCreationScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        PinCreationScreen(
          state = PinCreationState(),
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_INPUT).performTextInput("1234")
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_NEXT_BUTTON).performClick()

    assert(emittedEvent is PinCreationScreenEvents.PinSubmitted)
  }
}
