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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
            formattedNumber = "(555) 123-4567"
          ),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_NEXT_BUTTON).assertIsEnabled()
  }

  @Test
  fun `when Next is clicked, PhoneNumberSubmitted event is emitted`() {
    // Given
    var emittedEvent: PhoneNumberEntryScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        PhoneNumberScreen(
          state = PhoneNumberEntryState(
            countryCode = "1",
            nationalNumber = "5551234567",
            formattedNumber = "(555) 123-4567"
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
    assert(emittedEvent is PhoneNumberEntryScreenEvents.PhoneNumberSubmitted) {
      "Expected PhoneNumberSubmitted event but got $emittedEvent"
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
