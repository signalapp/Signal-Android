/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.screens.util.MockMultiplePermissionsState
import org.signal.registration.screens.util.MockPermissionsState
import org.signal.registration.test.TestTags

/**
 * Tests for registration navigation flow using Navigation 3.
 * Tests navigation by verifying UI state changes rather than using NavController.
 */
@OptIn(ExperimentalPermissionsApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RegistrationNavigationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var viewModel: RegistrationViewModel
  private lateinit var mockRepository: RegistrationRepository

  @Before
  fun setup() {
    mockRepository = mockk<RegistrationRepository>(relaxed = true)
    viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
  }

  @Test
  fun `navigation starts at Welcome screen`() {
    // Given
    val permissionsState = createMockPermissionsState()

    composeTestRule.setContent {
      SignalTheme(incognitoKeyboardEnabled = false) {
        RegistrationNavHost(
          registrationRepository = mockRepository,
          registrationViewModel = viewModel,
          permissionsState = permissionsState
        )
      }
    }

    // Then - verify Welcome screen is displayed
    composeTestRule.onNodeWithText("Welcome to Signal").assertIsDisplayed()
  }

  @Test
  fun `clicking Get Started navigates from Welcome to Permissions`() {
    // Given
    val permissionsState = createMockPermissionsState()

    composeTestRule.setContent {
      SignalTheme {
        RegistrationNavHost(
          registrationRepository = mockRepository,
          registrationViewModel = viewModel,
          permissionsState = permissionsState
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.WELCOME_GET_STARTED_BUTTON).performClick()

    // Then - verify Permissions screen is displayed
    composeTestRule.onNodeWithText("Permissions").assertIsDisplayed()
  }

  @Test
  fun `clicking Next on Permissions navigates to PhoneNumber`() {
    // Given
    val permissionsState = createMockPermissionsState()

    composeTestRule.setContent {
      SignalTheme {
        RegistrationNavHost(
          registrationRepository = mockRepository,
          registrationViewModel = viewModel,
          permissionsState = permissionsState
        )
      }
    }

    // Navigate to Permissions screen first
    composeTestRule.onNodeWithTag(TestTags.WELCOME_GET_STARTED_BUTTON).performClick()

    // When
    composeTestRule.onNodeWithTag(TestTags.PERMISSIONS_NEXT_BUTTON).performClick()

    // Then - verify PhoneNumber screen is displayed
    composeTestRule.onNodeWithText("You will receive a verification code").assertIsDisplayed()
  }

  @Test
  fun `clicking Not now on Permissions navigates to PhoneNumber`() {
    // Given
    val permissionsState = createMockPermissionsState()

    composeTestRule.setContent {
      SignalTheme {
        RegistrationNavHost(
          registrationRepository = mockRepository,
          registrationViewModel = viewModel,
          permissionsState = permissionsState
        )
      }
    }

    // Navigate to Permissions screen first
    composeTestRule.onNodeWithTag(TestTags.WELCOME_GET_STARTED_BUTTON).performClick()

    // When
    composeTestRule.onNodeWithTag(TestTags.PERMISSIONS_NOT_NOW_BUTTON).performClick()

    // Then - verify PhoneNumber screen is displayed
    composeTestRule.onNodeWithText("You will receive a verification code").assertIsDisplayed()
  }

  // Note: Back navigation testing in Navigation 3 requires testing through
  // actual back button presses at the Activity level, which is better suited
  // for instrumentation tests. The back stack is managed internally by Nav3
  // and not directly accessible in unit tests.

  @Test
  fun `clicking I have my old phone navigates to Permissions for restore`() {
    // Given
    val permissionsState = createMockPermissionsState()

    composeTestRule.setContent {
      SignalTheme {
        RegistrationNavHost(
          registrationRepository = mockRepository,
          registrationViewModel = viewModel,
          permissionsState = permissionsState
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_OR_TRANSFER_BUTTON).performClick()
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_HAS_OLD_PHONE_BUTTON).performClick()

    // Then - verify Permissions screen is displayed
    // (After permissions, user would go to RestoreViaQr screen)
    composeTestRule.onNodeWithText("Permissions").assertIsDisplayed()
  }

  @Test
  fun `clicking I don't have my old phone navigates to Restore`() {
    // Given
    val permissionsState = createMockPermissionsState()

    composeTestRule.setContent {
      SignalTheme {
        RegistrationNavHost(
          registrationRepository = mockRepository,
          registrationViewModel = viewModel,
          permissionsState = permissionsState
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_OR_TRANSFER_BUTTON).performClick()
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_NO_OLD_PHONE_BUTTON).performClick()

    // Then - verify Restore screen is displayed (or its expected content)
    // Note: Update this assertion based on actual Restore screen content when implemented
  }

  /**
   * Creates a mock permissions state for testing.
   * Since we're in JUnit tests, we can't use the real rememberMultiplePermissionsState.
   */
  private fun createMockPermissionsState(): MockMultiplePermissionsState {
    return MockMultiplePermissionsState(
      permissions = viewModel.getRequiredPermissions().map { MockPermissionsState(it) }
    )
  }
}
