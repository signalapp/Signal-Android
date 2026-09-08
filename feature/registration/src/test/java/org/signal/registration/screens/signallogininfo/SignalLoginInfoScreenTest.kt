/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogininfo

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId.ACI
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.test.TestTags
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SignalLoginInfoScreenTest {

  companion object {
    private val ACI_VALUE = ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91"))
    private val AEP = AccountEntropyPool("uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t")
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<SignalLoginInfoScreenEvents>()

  @Test
  fun `when save to password manager is clicked, SaveToPasswordManagerClicked is emitted`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_INFO_SAVE_TO_PASSWORD_MANAGER_BUTTON).performClick()

    assertThat(events).contains(SignalLoginInfoScreenEvents.SaveToPasswordManagerClicked)
  }

  @Test
  fun `when save manually is clicked, SaveManuallyClicked is emitted`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_INFO_SAVE_MANUALLY_BUTTON).performClick()

    assertThat(events).contains(SignalLoginInfoScreenEvents.SaveManuallyClicked)
  }

  @Test
  fun `when confirm on the confirm sheet is clicked, ConfirmSavedContinueClicked is emitted`() {
    setContent(showConfirmSavedSheet = true)

    composeTestRule.onNodeWithTag(TestTags.CONFIRM_LOGIN_SAVED_TO_PASSWORD_MANAGER_CONFIRM_BUTTON).performClick()

    assertThat(events).contains(SignalLoginInfoScreenEvents.ConfirmSavedContinueClicked)
  }

  @Test
  fun `when see login info again on the confirm sheet is clicked, SeeLoginInfoAgainClicked is emitted`() {
    setContent(showConfirmSavedSheet = true)

    composeTestRule.onNodeWithTag(TestTags.CONFIRM_LOGIN_SAVED_TO_PASSWORD_MANAGER_SEE_LOGIN_INFO_AGAIN_BUTTON).performClick()

    assertThat(events).contains(SignalLoginInfoScreenEvents.SeeLoginInfoAgainClicked)
  }

  private fun setContent(showConfirmSavedSheet: Boolean = false) {
    composeTestRule.setContent {
      SignalTheme {
        SignalLoginInfoScreen(
          state = SignalLoginInfoState(
            aci = ACI_VALUE,
            aep = AEP,
            isPasswordManagerAvailable = true,
            showConfirmSavedSheet = showConfirmSavedSheet
          ),
          onEvent = { events += it }
        )
      }
    }
  }
}
