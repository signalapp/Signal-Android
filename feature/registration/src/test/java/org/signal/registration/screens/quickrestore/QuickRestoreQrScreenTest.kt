/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.quickrestore

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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class QuickRestoreQrScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `screen displays cancel button`() {
    composeTestRule.setContent {
      SignalTheme {
        QuickRestoreQrScreen(
          state = QuickRestoreQrState(qrState = QrState.Loading),
          onEvent = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.QUICK_RESTORE_QR_CANCEL_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when Cancel is clicked, Cancel event is emitted`() {
    var emittedEvent: QuickRestoreQrEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        QuickRestoreQrScreen(
          state = QuickRestoreQrState(qrState = QrState.Loading),
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.QUICK_RESTORE_QR_CANCEL_BUTTON).performClick()

    assert(emittedEvent == QuickRestoreQrEvents.Cancel)
  }

  @Test
  fun `when qr state is Failed, Retry button is displayed and clicking it emits RetryQrCode`() {
    var emittedEvent: QuickRestoreQrEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        QuickRestoreQrScreen(
          state = QuickRestoreQrState(qrState = QrState.Failed),
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.QUICK_RESTORE_QR_RETRY_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.QUICK_RESTORE_QR_RETRY_BUTTON).performClick()

    assert(emittedEvent == QuickRestoreQrEvents.RetryQrCode)
  }
}
