/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.accountlocked

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
 * Tests for AccountLockedScreen that validate event emissions.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AccountLockedScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `screen displays screen and next button`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        AccountLockedScreen(
          state = AccountLockedState(),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.ACCOUNT_LOCKED_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.ACCOUNT_LOCKED_NEXT_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when Next is clicked, Next event is emitted`() {
    // Given
    var emittedEvent: AccountLockedScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        AccountLockedScreen(
          state = AccountLockedState(),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.ACCOUNT_LOCKED_NEXT_BUTTON).performClick()

    // Then
    assert(emittedEvent == AccountLockedScreenEvents.Next)
  }

  @Test
  fun `when Learn More is clicked, LearnMore event is emitted`() {
    // Given
    var emittedEvent: AccountLockedScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        AccountLockedScreen(
          state = AccountLockedState(),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.ACCOUNT_LOCKED_LEARN_MORE_BUTTON).performClick()

    // Then
    assert(emittedEvent == AccountLockedScreenEvents.LearnMore)
  }
}
