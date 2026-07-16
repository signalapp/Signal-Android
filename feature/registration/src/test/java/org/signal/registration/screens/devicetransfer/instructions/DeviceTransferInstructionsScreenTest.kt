/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.instructions

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
 * Tests for DeviceTransferInstructionsScreen that validate event emissions.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DeviceTransferInstructionsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `screen displays continue button`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        DeviceTransferInstructionsScreen(
          state = DeviceTransferInstructionsState(),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.DEVICE_TRANSFER_INSTRUCTIONS_CONTINUE_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when Continue is clicked, ContinueClicked event is emitted`() {
    // Given
    var emittedEvent: DeviceTransferInstructionsScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        DeviceTransferInstructionsScreen(
          state = DeviceTransferInstructionsState(),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.DEVICE_TRANSFER_INSTRUCTIONS_CONTINUE_BUTTON).performClick()

    // Then
    assert(emittedEvent == DeviceTransferInstructionsScreenEvents.ContinueClicked)
  }
}
