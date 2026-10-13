/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginmanualsave

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.test.TestTags
import org.signal.signallogin.SignalLoginTestTags

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SignalLoginViewDetailsForManualSaveScreenTest {

  companion object {
    private const val ACCOUNT_ID = "A6B28482-2E32-83D0-7F23-91360A4C2B91"
    private const val RECOVERY_KEY = "UY38JH2778HJJHJ8LK19GA61S672JSJ089R023S6A57809BAP92J2YH5T326VV7T"
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<SignalLoginViewDetailsForManualSaveScreenEvents>()

  @Test
  fun `when the account ID copy button is clicked, CopyAccountIdClicked is emitted`() {
    setContent()

    composeTestRule.onNodeWithTag(SignalLoginTestTags.KEY_DETAILS_ACCOUNT_ID_COPY_BUTTON).performScrollTo().performClick()

    assertThat(events).contains(SignalLoginViewDetailsForManualSaveScreenEvents.CopyAccountIdClicked(ACCOUNT_ID))
  }

  @Test
  fun `when the recovery key copy button is clicked, CopyRecoveryKeyClicked is emitted`() {
    setContent()

    composeTestRule.onNodeWithTag(SignalLoginTestTags.KEY_DETAILS_RECOVERY_KEY_COPY_BUTTON).performScrollTo().performClick()

    assertThat(events).contains(SignalLoginViewDetailsForManualSaveScreenEvents.CopyRecoveryKeyClicked(RECOVERY_KEY))
  }

  @Test
  fun `when save as PDF is clicked, SaveAsPdfClicked is emitted`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_MANUAL_SAVE_SAVE_AS_PDF_BUTTON).performClick()

    assertThat(events).contains(SignalLoginViewDetailsForManualSaveScreenEvents.SaveAsPdfClicked)
  }

  @Test
  fun `when continue is clicked, ContinueClicked is emitted`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_MANUAL_SAVE_CONTINUE_BUTTON).performClick()

    assertThat(events).contains(SignalLoginViewDetailsForManualSaveScreenEvents.ContinueClicked)
  }

  @Test
  fun `when continue on the confirm sheet is clicked, ConfirmSavedContinueClicked is emitted`() {
    setContent(showConfirmSavedSheet = true)

    composeTestRule.onNodeWithTag(TestTags.CONFIRM_LOGIN_SAVED_CONTINUE_BUTTON).performClick()

    assertThat(events).contains(SignalLoginViewDetailsForManualSaveScreenEvents.ConfirmSavedContinueClicked)
  }

  @Test
  fun `when show login info again on the confirm sheet is clicked, ShowLoginInfoAgainClicked is emitted`() {
    setContent(showConfirmSavedSheet = true)

    composeTestRule.onNodeWithTag(TestTags.CONFIRM_LOGIN_SAVED_SHOW_LOGIN_INFO_AGAIN_BUTTON).performClick()

    assertThat(events).contains(SignalLoginViewDetailsForManualSaveScreenEvents.ShowLoginInfoAgainClicked)
  }

  private fun setContent(showConfirmSavedSheet: Boolean = false) {
    composeTestRule.setContent {
      SignalTheme {
        SignalLoginViewDetailsForManualSaveScreen(
          state = SignalLoginViewDetailsForManualSaveState(
            accountId = ACCOUNT_ID,
            recoveryKey = RECOVERY_KEY,
            showConfirmSavedSheet = showConfirmSavedSheet
          ),
          onEvent = { events += it }
        )
      }
    }
  }
}
