/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.twofactorselection

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
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
class TwoFactorSelectionScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<TwoFactorSelectionScreenEvents>()

  private val passkey = TwoFactorMethod.Passkey(WebAuthnParameters(challenge = byteArrayOf(1, 2, 3), timeoutSeconds = 60, allowedCredentialIds = listOf(byteArrayOf(4, 5, 6))))

  @Test
  fun `screen displays title and subtitle`() {
    setContent(createState())

    composeTestRule.onNodeWithText("Two-factor authentication").assertIsDisplayed()
    composeTestRule.onNodeWithText("Choose a method below to verify your account.").assertIsDisplayed()
  }

  @Test
  fun `screen displays a card for every offered method`() {
    setContent(createState())

    composeTestRule.onNodeWithTag(TestTags.TWO_FACTOR_SELECTION_PASSKEY_OPTION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.TWO_FACTOR_SELECTION_AUTHENTICATOR_APP_OPTION).assertIsDisplayed()
  }

  @Test
  fun `methods that aren't offered are not displayed`() {
    setContent(createState(methods = listOf(TwoFactorMethod.AuthenticatorApp)))

    composeTestRule.onNodeWithTag(TestTags.TWO_FACTOR_SELECTION_AUTHENTICATOR_APP_OPTION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.TWO_FACTOR_SELECTION_PASSKEY_OPTION).assertDoesNotExist()
  }

  @Test
  fun `clicking the passkey card emits MethodSelected`() {
    setContent(createState())

    composeTestRule.onNodeWithTag(TestTags.TWO_FACTOR_SELECTION_PASSKEY_OPTION).performClick()

    assertThat(events).contains(TwoFactorSelectionScreenEvents.MethodSelected(passkey))
  }

  @Test
  fun `clicking the authenticator app card emits MethodSelected`() {
    setContent(createState())

    composeTestRule.onNodeWithTag(TestTags.TWO_FACTOR_SELECTION_AUTHENTICATOR_APP_OPTION).performClick()

    assertThat(events).contains(TwoFactorSelectionScreenEvents.MethodSelected(TwoFactorMethod.AuthenticatorApp))
  }

  @Test
  fun `clicking cancel emits CancelClicked`() {
    setContent(createState())

    composeTestRule.onNodeWithTag(TestTags.TWO_FACTOR_SELECTION_CANCEL_BUTTON).performClick()

    assertThat(events).contains(TwoFactorSelectionScreenEvents.CancelClicked)
  }

  @Test
  fun `simply rendering the screen emits no events`() {
    setContent(createState())

    assertThat(events).isEmpty()
  }

  private fun setContent(state: TwoFactorSelectionState) {
    composeTestRule.setContent {
      SignalTheme {
        TwoFactorSelectionScreen(
          state = state,
          onEvent = { events += it }
        )
      }
    }
  }

  private fun createState(
    methods: List<TwoFactorMethod> = listOf(passkey, TwoFactorMethod.AuthenticatorApp)
  ): TwoFactorSelectionState {
    return TwoFactorSelectionState(methods = methods)
  }
}
