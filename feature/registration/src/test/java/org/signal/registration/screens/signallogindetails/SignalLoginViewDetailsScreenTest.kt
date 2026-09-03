/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogindetails

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
import org.signal.signallogin.SignalLoginTestTags
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreen
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreenEvents
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsState

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SignalLoginViewDetailsScreenTest {

  companion object {
    private const val ACCOUNT_KEY = "A6B28482-2E32-83D0-7F23-91360A4C2B91"
    private const val RECOVERY_KEY = "UY38JH2778HJJHJ8LK19GA61S672JSJ089R023S6A57809BAP92J2YH5T326VV7T"
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<SignalLoginViewDetailsScreenEvents>()

  @Test
  fun `when the account key is long clicked, AccountIdLongClicked is emitted`() {
    setContent()

    composeTestRule.onNodeWithTag(SignalLoginTestTags.VIEW_DETAILS_ACCOUNT_KEY_BLOCK).performScrollTo().performTouchInput { longClick() }

    assertThat(events).contains(SignalLoginViewDetailsScreenEvents.AccountIdLongClicked(ACCOUNT_KEY))
  }

  @Test
  fun `when the recovery key is long clicked, RecoveryKeyLongClicked is emitted`() {
    setContent()

    composeTestRule.onNodeWithTag(SignalLoginTestTags.VIEW_DETAILS_RECOVERY_KEY_BLOCK).performScrollTo().performTouchInput { longClick() }

    assertThat(events).contains(SignalLoginViewDetailsScreenEvents.RecoveryKeyLongClicked(RECOVERY_KEY))
  }

  private fun setContent() {
    composeTestRule.setContent {
      SignalTheme {
        SignalLoginViewDetailsScreen(
          state = SignalLoginViewDetailsState(
            accountKey = ACCOUNT_KEY,
            recoveryKey = RECOVERY_KEY
          ),
          onEvent = { events += it }
        )
      }
    }
  }
}
