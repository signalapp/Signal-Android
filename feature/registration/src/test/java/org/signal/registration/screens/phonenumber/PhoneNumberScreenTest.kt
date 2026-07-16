/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
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
 * Tests for PhoneNumberScreen that validate user interactions and event emissions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PhoneNumberScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `Next button is disabled when fields are empty`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        PhoneNumberScreen(
          state = PhoneNumberEntryState(),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_NEXT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun `Next button is enabled when nationalNumber is present in state`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        PhoneNumberScreen(
          state = PhoneNumberEntryState(
            countryCode = "1",
            nationalNumber = "5551234567",
            formattedNumber = "(555) 123-4567",
            isNumberPossible = true
          ),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_NEXT_BUTTON).assertIsEnabled()
  }

  @Test
  fun `when Next is clicked, PhoneNumberEntered event is emitted`() {
    // Given
    var emittedEvent: PhoneNumberEntryScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        PhoneNumberScreen(
          state = PhoneNumberEntryState(
            countryCode = "1",
            nationalNumber = "5551234567",
            formattedNumber = "(555) 123-4567",
            isNumberPossible = true
          ),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When - click Next
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_NEXT_BUTTON).performClick()

    // Then
    assert(emittedEvent is PhoneNumberEntryScreenEvents.NextClicked) {
      "Expected PhoneNumberEntered event but got $emittedEvent"
    }
  }

  @Test
  fun `pressing done does not emit NextClicked when number is not valid`() {
    // Given
    var emittedEvent: PhoneNumberEntryScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        PhoneNumberScreen(
          state = PhoneNumberEntryState(
            countryCode = "1",
            nationalNumber = "555",
            formattedNumber = "555"
          ),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When - press the IME done action on the phone number field
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_PHONE_FIELD).performImeAction()

    // Then
    assert(emittedEvent !is PhoneNumberEntryScreenEvents.NextClicked) {
      "Expected no NextClicked event for an invalid number but got $emittedEvent"
    }
  }

  @Test
  fun `pressing done emits NextClicked when number is valid`() {
    // Given
    var emittedEvent: PhoneNumberEntryScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        PhoneNumberScreen(
          state = PhoneNumberEntryState(
            countryCode = "1",
            nationalNumber = "5551234567",
            formattedNumber = "(555) 123-4567",
            isNumberPossible = true
          ),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When - press the IME done action on the phone number field
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_PHONE_FIELD).performImeAction()

    // Then
    assert(emittedEvent is PhoneNumberEntryScreenEvents.NextClicked) {
      "Expected NextClicked event for a valid number but got $emittedEvent"
    }
  }

  @Test
  fun `clicking country picker emits CountryPicker event`() {
    // Given
    var emittedEvent: PhoneNumberEntryScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        PhoneNumberScreen(
          state = PhoneNumberEntryState(),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_COUNTRY_PICKER).performClick()

    // Then
    assert(emittedEvent is PhoneNumberEntryScreenEvents.CountryPicker) {
      "Expected CountryPicker event but got $emittedEvent"
    }
  }
}
