/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.setup

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
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
 * Tests for DeviceTransferSetupScreen that validate event emissions.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DeviceTransferSetupScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `verify step displays match and do not match buttons`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        DeviceTransferSetupScreen(
          state = DeviceTransferSetupState(step = SetupStep.VERIFY, authenticationCode = 1234567),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.DEVICE_TRANSFER_SETUP_NUMBERS_MATCH_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.DEVICE_TRANSFER_SETUP_NUMBERS_DO_NOT_MATCH_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when numbers match is clicked, UserVerifiedCode event is emitted`() {
    // Given
    var emittedEvent: DeviceTransferSetupScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        DeviceTransferSetupScreen(
          state = DeviceTransferSetupState(step = SetupStep.VERIFY, authenticationCode = 1234567),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.DEVICE_TRANSFER_SETUP_NUMBERS_MATCH_BUTTON).performClick()

    // Then
    assert(emittedEvent == DeviceTransferSetupScreenEvents.UserVerifiedCode)
  }

  @Test
  fun `when numbers do not match is clicked, UserRejectedCode event is emitted`() {
    // Given
    var emittedEvent: DeviceTransferSetupScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        DeviceTransferSetupScreen(
          state = DeviceTransferSetupState(step = SetupStep.VERIFY, authenticationCode = 1234567),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.DEVICE_TRANSFER_SETUP_NUMBERS_DO_NOT_MATCH_BUTTON).performClick()

    // Then
    assert(emittedEvent == DeviceTransferSetupScreenEvents.UserRejectedCode)
  }

  @Test
  fun `troubleshooting step displays retry button`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        DeviceTransferSetupScreen(
          state = DeviceTransferSetupState(step = SetupStep.TROUBLESHOOTING),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.DEVICE_TRANSFER_SETUP_TROUBLESHOOTING_RETRY_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when troubleshooting retry is clicked, RetryClicked event is emitted`() {
    // Given
    var emittedEvent: DeviceTransferSetupScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        DeviceTransferSetupScreen(
          state = DeviceTransferSetupState(step = SetupStep.TROUBLESHOOTING),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.DEVICE_TRANSFER_SETUP_TROUBLESHOOTING_RETRY_BUTTON).performClick()

    // Then
    assert(emittedEvent == DeviceTransferSetupScreenEvents.RetryClicked)
  }
}
