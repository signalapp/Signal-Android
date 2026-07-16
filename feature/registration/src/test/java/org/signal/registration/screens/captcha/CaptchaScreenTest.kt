/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.captcha

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
 * Tests for CaptchaScreen that validate event emissions.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CaptchaScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `screen displays cancel button`() {
    // Given
    composeTestRule.setContent {
      SignalTheme {
        CaptchaScreen(
          state = CaptchaState(captchaUrl = "https://example.com"),
          onEvent = {}
        )
      }
    }

    // Then
    composeTestRule.onNodeWithTag(TestTags.CAPTCHA_CANCEL_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when Cancel is clicked, Cancel event is emitted`() {
    // Given
    var emittedEvent: CaptchaScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        CaptchaScreen(
          state = CaptchaState(captchaUrl = "https://example.com"),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(TestTags.CAPTCHA_CANCEL_BUTTON).performClick()

    // Then
    assert(emittedEvent == CaptchaScreenEvents.Cancel)
  }
}
