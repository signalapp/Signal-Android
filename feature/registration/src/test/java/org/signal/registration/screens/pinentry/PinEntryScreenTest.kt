/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pinentry

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
 * Tests for PinEntryScreen that validate event emissions.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PinEntryScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `screen displays input and continue button`() {
    composeTestRule.setContent {
      SignalTheme {
        PinEntryScreen(
          state = PinEntryState(),
          onEvent = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_INPUT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_CONTINUE_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when toggle keyboard is clicked, ToggleKeyboard event is emitted`() {
    var emittedEvent: PinEntryScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        PinEntryScreen(
          state = PinEntryState(),
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_TOGGLE_KEYBOARD_BUTTON).performClick()

    assert(emittedEvent == PinEntryScreenEvents.ToggleKeyboard)
  }

  @Test
  fun `continue is disabled when pin is empty`() {
    composeTestRule.setContent {
      SignalTheme {
        PinEntryScreen(
          state = PinEntryState(),
          onEvent = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_CONTINUE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun `when a pin is entered and continue is clicked, PinEntered event is emitted`() {
    var emittedEvent: PinEntryScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        PinEntryScreen(
          state = PinEntryState(),
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_INPUT).performTextInput("1234")
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_CONTINUE_BUTTON).performClick()

    assert(emittedEvent is PinEntryScreenEvents.PinEntered)
  }

  @Test
  fun `skip button is displayed in SvrRestore mode`() {
    composeTestRule.setContent {
      SignalTheme {
        PinEntryScreen(
          state = PinEntryState(),
          onEvent = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_SKIP_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when skip is confirmed in the dialog, Skip event is emitted`() {
    var emittedEvent: PinEntryScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        PinEntryScreen(
          state = PinEntryState(),
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_SKIP_BUTTON).performClick()
    composeTestRule.onNodeWithText("Create New PIN").performClick()

    assert(emittedEvent == PinEntryScreenEvents.Skip)
  }
}
