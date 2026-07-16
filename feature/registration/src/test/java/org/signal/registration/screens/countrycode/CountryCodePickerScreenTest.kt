/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.countrycode

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
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
import org.signal.registration.R
import org.signal.registration.test.TestTags

/**
 * Tests for CountryCodePickerScreen that validate event emissions.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CountryCodePickerScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `screen displays search field`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        CountryCodePickerScreen(
          state = CountryCodeState(),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.COUNTRY_CODE_SEARCH_FIELD).assertIsDisplayed()
  }

  @Test
  fun `screen displays title`() {
    // Given
    val title = ApplicationProvider.getApplicationContext<Application>().getString(R.string.CountryCodeSelectScreen__your_country)

    composeTestRule.setContent {
      SignalTheme {
        CountryCodePickerScreen(
          state = CountryCodeState(),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithText(title).assertIsDisplayed()
  }

  @Test
  fun `when close button is clicked, Dismissed event is emitted`() {
    // Given
    var emittedEvent: CountryCodePickerScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        CountryCodePickerScreen(
          state = CountryCodeState(),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.COUNTRY_CODE_CLOSE_BUTTON).performClick()

    // Then
    assert(emittedEvent == CountryCodePickerScreenEvents.Dismissed)
  }

  @Test
  fun `when typing in search field, Search event is emitted`() {
    // Given
    var emittedEvent: CountryCodePickerScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        CountryCodePickerScreen(
          state = CountryCodeState(),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.COUNTRY_CODE_SEARCH_FIELD).performTextInput("US")

    // Then
    assert(emittedEvent is CountryCodePickerScreenEvents.Search)
  }

  @Test
  fun `when a country is selected, CountrySelected event is emitted`() {
    // Given
    var emittedEvent: CountryCodePickerScreenEvents? = null
    val country = Country("🇺🇸", "United States", 1, "US")

    composeTestRule.setContent {
      SignalTheme {
        CountryCodePickerScreen(
          state = CountryCodeState(
            countryList = listOf(country),
            filteredList = listOf(country)
          ),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithText("United States").performClick()

    // Then
    assert(emittedEvent is CountryCodePickerScreenEvents.CountrySelected)
  }
}
