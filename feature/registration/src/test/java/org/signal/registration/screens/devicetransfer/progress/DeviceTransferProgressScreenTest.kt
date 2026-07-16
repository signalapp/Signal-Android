/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.progress

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
 * Tests for DeviceTransferProgressScreen that validate event emissions.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DeviceTransferProgressScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `default state displays cancel button`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        DeviceTransferProgressScreen(
          state = DeviceTransferProgressState(),
          showCancelDialog = false,
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.DEVICE_TRANSFER_PROGRESS_CANCEL_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when Cancel is clicked, CancelClicked event is emitted`() {
    // Given
    var emittedEvent: DeviceTransferProgressScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        DeviceTransferProgressScreen(
          state = DeviceTransferProgressState(),
          showCancelDialog = false,
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.DEVICE_TRANSFER_PROGRESS_CANCEL_BUTTON).performClick()

    // Then
    assert(emittedEvent == DeviceTransferProgressScreenEvents.CancelClicked)
  }

  @Test
  fun `failed state displays try again button`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        DeviceTransferProgressScreen(
          state = DeviceTransferProgressState(status = DeviceTransferProgressState.Status.FAILED),
          showCancelDialog = false,
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.DEVICE_TRANSFER_PROGRESS_TRY_AGAIN_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when Try Again is clicked, TryAgainClicked event is emitted`() {
    // Given
    var emittedEvent: DeviceTransferProgressScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        DeviceTransferProgressScreen(
          state = DeviceTransferProgressState(status = DeviceTransferProgressState.Status.FAILED),
          showCancelDialog = false,
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.DEVICE_TRANSFER_PROGRESS_TRY_AGAIN_BUTTON).performClick()

    // Then
    assert(emittedEvent == DeviceTransferProgressScreenEvents.TryAgainClicked)
  }
}
