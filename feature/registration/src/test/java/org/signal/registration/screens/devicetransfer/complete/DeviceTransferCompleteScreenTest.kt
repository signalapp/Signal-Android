/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.complete

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
 * Tests for DeviceTransferCompleteScreen that validate event emissions.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DeviceTransferCompleteScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `screen displays continue button`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        DeviceTransferCompleteScreen(
          state = DeviceTransferCompleteState(),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.DEVICE_TRANSFER_COMPLETE_CONTINUE_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when Continue is clicked, ContinueClicked event is emitted`() {
    // Given
    var emittedEvent: DeviceTransferCompleteScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        DeviceTransferCompleteScreen(
          state = DeviceTransferCompleteState(),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.DEVICE_TRANSFER_COMPLETE_CONTINUE_BUTTON).performClick()

    // Then
    assert(emittedEvent == DeviceTransferCompleteScreenEvents.ContinueClicked)
  }
}
