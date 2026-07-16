/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.createprofile

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
 * Tests for CreateProfileScreen that validate event emissions.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CreateProfileScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `screen displays given name and family name fields`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        CreateProfileScreen(
          state = CreateProfileState(isLoading = false),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.CREATE_PROFILE_GIVEN_NAME_FIELD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.CREATE_PROFILE_FAMILY_NAME_FIELD).assertIsDisplayed()
  }

  @Test
  fun `when typing in given name field, GivenNameChanged event is emitted`() {
    // Given
    var emittedEvent: CreateProfileScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        CreateProfileScreen(
          state = CreateProfileState(isLoading = false),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.CREATE_PROFILE_GIVEN_NAME_FIELD).performTextInput("Alice")

    // Then
    assert(emittedEvent is CreateProfileScreenEvents.GivenNameChanged)
  }

  @Config(qualifiers = "w360dp-h1200dp")
  @Test
  fun `when who can find me row is clicked, WhoCanFindMeClicked event is emitted`() {
    // Given
    var emittedEvent: CreateProfileScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        CreateProfileScreen(
          state = CreateProfileState(isLoading = false),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.CREATE_PROFILE_WHO_CAN_FIND_ME_ROW).performClick()

    // Then
    assert(emittedEvent == CreateProfileScreenEvents.WhoCanFindMeClicked)
  }

  @Test
  fun `when given name is blank, Next button is disabled`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        CreateProfileScreen(
          state = CreateProfileState(isLoading = false),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.CREATE_PROFILE_NEXT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun `when given name is present, Next button is enabled and emits NextClicked`() {
    // Given
    var emittedEvent: CreateProfileScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        CreateProfileScreen(
          state = CreateProfileState(givenName = "Alice", isLoading = false),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.CREATE_PROFILE_NEXT_BUTTON).assertIsEnabled().performClick()

    // Then
    assert(emittedEvent == CreateProfileScreenEvents.NextClicked)
  }
}
