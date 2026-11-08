/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.twofactorverify

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.twofactorverify.TwoFactorVerifyState.CodeError
import org.signal.appsettings.twofactorverify.TwoFactorVerifyState.Dialog
import org.signal.appsettings.twofactorverify.TwoFactorVerifyState.Step
import org.signal.core.ui.compose.Dialogs
import org.signal.uicomponents.codeentryfield.CodeEntryFieldEvents
import org.signal.uicomponents.codeentryfield.CodeEntryFieldState
import org.signal.uicomponents.codeentryfield.CodeEntryFieldTestTags

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TwoFactorVerifyScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<TwoFactorVerifyEvent>()

  @Test
  fun `loading shows a progress indicator`() {
    setContent(TwoFactorVerifyState(step = Step.Loading))

    composeTestRule.onNodeWithTag(TwoFactorVerifyTestTags.LOADING).assertIsDisplayed()
  }

  @Test
  fun `both options are shown when the account has both methods`() {
    setContent(TwoFactorVerifyState(step = Step.ChooseMethod(hasAuthenticatorApp = true)))

    composeTestRule.onNodeWithTag(TwoFactorVerifyTestTags.PASSKEY_OPTION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TwoFactorVerifyTestTags.AUTHENTICATOR_APP_OPTION).assertIsDisplayed()
  }

  @Test
  fun `the authenticator app option is hidden when the account only has a passkey`() {
    setContent(TwoFactorVerifyState(step = Step.ChooseMethod(hasAuthenticatorApp = false)))

    composeTestRule.onNodeWithTag(TwoFactorVerifyTestTags.PASSKEY_OPTION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TwoFactorVerifyTestTags.AUTHENTICATOR_APP_OPTION).assertDoesNotExist()
  }

  @Test
  fun `clicking the passkey option emits PasskeySelected`() {
    setContent(TwoFactorVerifyState(step = Step.ChooseMethod(hasAuthenticatorApp = true)))

    composeTestRule.onNodeWithTag(TwoFactorVerifyTestTags.PASSKEY_OPTION).performClick()

    assertThat(events).contains(TwoFactorVerifyEvent.PasskeySelected)
  }

  @Test
  fun `clicking the authenticator app option emits AuthenticatorAppSelected`() {
    setContent(TwoFactorVerifyState(step = Step.ChooseMethod(hasAuthenticatorApp = true)))

    composeTestRule.onNodeWithTag(TwoFactorVerifyTestTags.AUTHENTICATOR_APP_OPTION).performClick()

    assertThat(events).contains(TwoFactorVerifyEvent.AuthenticatorAppSelected)
  }

  @Test
  fun `clicking cancel emits CancelClicked`() {
    setContent(TwoFactorVerifyState(step = Step.ChooseMethod(hasAuthenticatorApp = true)))

    composeTestRule.onNodeWithTag(TwoFactorVerifyTestTags.BUTTON_CANCEL).performClick()

    assertThat(events).contains(TwoFactorVerifyEvent.CancelClicked)
  }

  @Test
  fun `typing in the code field forwards a CodeEntryEvent`() {
    setContent(TwoFactorVerifyState(step = Step.EnterCode(canReturnToMethodChoice = false)))

    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.ROOT).performTextInput("1")
    composeTestRule.waitForIdle()

    assertThat(events).contains(TwoFactorVerifyEvent.CodeEntryEvent(CodeEntryFieldEvents.CodeChanged("1")))
  }

  @Test
  fun `an incorrect code shows an error`() {
    setContent(
      TwoFactorVerifyState(
        step = Step.EnterCode(canReturnToMethodChoice = false),
        codeEntry = CodeEntryFieldState(code = "123456"),
        codeError = CodeError.IncorrectCode
      )
    )

    composeTestRule.onNodeWithTag(TwoFactorVerifyTestTags.CODE_ERROR).assertIsDisplayed()
  }

  @Test
  fun `the code field is disabled while a code is being submitted`() {
    setContent(
      TwoFactorVerifyState(
        step = Step.EnterCode(canReturnToMethodChoice = false),
        codeEntry = CodeEntryFieldState(code = "123456"),
        submitting = true
      )
    )

    composeTestRule.onNodeWithTag(CodeEntryFieldTestTags.ROOT).assertIsNotEnabled()
  }

  @Test
  fun `dismissing the passkey failed dialog emits DialogDismissed`() {
    setContent(TwoFactorVerifyState(step = Step.ChooseMethod(hasAuthenticatorApp = true), dialog = Dialog.PasskeyFailed))

    composeTestRule.onNodeWithTag(TwoFactorVerifyTestTags.DIALOG_PASSKEY_FAILED).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_MESSAGE_DIALOG_DISMISS_BUTTON).performClick()

    assertThat(events).contains(TwoFactorVerifyEvent.DialogDismissed)
  }

  @Test
  fun `the network failure dialog is shown`() {
    setContent(TwoFactorVerifyState(step = Step.Loading, dialog = Dialog.NetworkFailure))

    composeTestRule.onNodeWithTag(TwoFactorVerifyTestTags.DIALOG_NETWORK_FAILURE).assertIsDisplayed()
  }

  private fun setContent(state: TwoFactorVerifyState) {
    composeTestRule.setContent {
      TwoFactorVerifyScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }
}
