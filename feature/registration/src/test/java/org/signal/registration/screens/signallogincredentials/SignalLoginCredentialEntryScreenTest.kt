/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogincredentials

import android.app.Application
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.passwordmanager.SignalCredentialManager
import org.signal.passwordmanager.UsernamePasswordCredential
import org.signal.registration.screens.aepentry.AepInput
import org.signal.registration.screens.shared.AccountIdError
import org.signal.registration.test.TestTags
import org.signal.signallogin.SignalLoginTestTags

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SignalLoginCredentialEntryScreenTest {

  companion object {
    private const val VALID_ACCOUNT_ID = "a6b284822e3283d07f2391360a4c2b91"
    private const val VALID_RECOVERY_KEY = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t"
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<SignalLoginCredentialEntryScreenEvents>()

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `when the screen is displayed, the title is labelled beta`() {
    setContent(SignalLoginCredentialEntryState())

    composeTestRule.onNodeWithTag(SignalLoginTestTags.BETA_TAG).assertIsDisplayed()
  }

  @Test
  fun `when an empty field is tapped, the password manager is prompted and the picked credential is emitted`() {
    stubPasswordManager()
    setContent(SignalLoginCredentialEntryState())

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_ACCOUNT_ID_FIELD).performClick()
    composeTestRule.waitForIdle()

    assertThat(events).contains(SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected(accountId = VALID_ACCOUNT_ID, recoveryKey = VALID_RECOVERY_KEY))
  }

  @Test
  fun `when a field is tapped with a login already entered, the password manager is not prompted`() {
    stubPasswordManager()
    setContent(completeState())

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_ACCOUNT_ID_FIELD).performClick()
    composeTestRule.waitForIdle()

    coVerify(exactly = 0) { SignalCredentialManager.getCredential(any()) }
  }

  @Test
  fun `when a field is tapped with only a prefilled account ID, the password manager is still prompted`() {
    stubPasswordManager()
    setContent(SignalLoginCredentialEntryState(accountId = VALID_ACCOUNT_ID, isAccountIdPrefilled = true))

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_ACCOUNT_ID_FIELD).performClick()
    composeTestRule.waitForIdle()

    assertThat(events).contains(SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected(accountId = VALID_ACCOUNT_ID, recoveryKey = VALID_RECOVERY_KEY))
  }

  @Test
  fun `when a field is tapped with a user-typed account ID, the password manager is not prompted`() {
    stubPasswordManager()
    setContent(SignalLoginCredentialEntryState(accountId = VALID_ACCOUNT_ID))

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_ACCOUNT_ID_FIELD).performClick()
    composeTestRule.waitForIdle()

    coVerify(exactly = 0) { SignalCredentialManager.getCredential(any()) }
  }

  @Test
  fun `the account ID field is tagged for autofill as the username`() {
    setContent(SignalLoginCredentialEntryState())

    assertThat(contentTypeOf(TestTags.SIGNAL_LOGIN_CREDENTIAL_ACCOUNT_ID_FIELD)).isEqualTo(ContentType.Username)
  }

  @Test
  fun `the recovery key field is tagged for autofill as the password`() {
    setContent(SignalLoginCredentialEntryState())

    assertThat(contentTypeOf(TestTags.SIGNAL_LOGIN_CREDENTIAL_RECOVERY_KEY_FIELD)).isEqualTo(ContentType.Password)
  }

  @Test
  fun `when text is typed into the account ID field, AccountIdChanged is emitted`() {
    setContent(SignalLoginCredentialEntryState())

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_ACCOUNT_ID_FIELD).performTextInput("a6b2")

    assertThat(events).contains(SignalLoginCredentialEntryScreenEvents.AccountIdChanged("a6b2"))
  }

  @Test
  fun `when text is typed into the recovery key field, RecoveryKeyChanged is emitted`() {
    setContent(SignalLoginCredentialEntryState())

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_RECOVERY_KEY_FIELD).performTextInput("uy38")

    assertThat(events).contains(SignalLoginCredentialEntryScreenEvents.RecoveryKeyChanged("uy38"))
  }

  @Test
  fun `when the eye button is clicked, RecoveryKeyVisibilityToggled is emitted`() {
    setContent(SignalLoginCredentialEntryState())

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_REVEAL_RECOVERY_KEY_BUTTON, useUnmergedTree = true).performScrollTo().performClick()

    assertThat(events).contains(SignalLoginCredentialEntryScreenEvents.RecoveryKeyVisibilityToggled)
  }

  @Test
  fun `when Need help is clicked, NeedHelpClicked is emitted`() {
    setContent(SignalLoginCredentialEntryState())

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_NEED_HELP_BUTTON).performClick()

    assertThat(events).contains(SignalLoginCredentialEntryScreenEvents.NeedHelpClicked)
  }

  @Test
  fun `in confirm-saved mode, Show login info again takes the place of Need help`() {
    setContent(SignalLoginCredentialEntryState(mode = SignalLoginCredentialEntryState.Mode.ConfirmSaved))

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_NEED_HELP_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_SHOW_LOGIN_INFO_AGAIN_BUTTON).performClick()

    assertThat(events).contains(SignalLoginCredentialEntryScreenEvents.ShowLoginInfoAgainClicked)
  }

  @Test
  fun `when Next is clicked with a complete login, NextClicked is emitted`() {
    setContent(completeState())

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_NEXT_BUTTON).performClick()

    assertThat(events).contains(SignalLoginCredentialEntryScreenEvents.NextClicked)
  }

  @Test
  fun `given a complete login, Next is enabled`() {
    setContent(completeState())

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_NEXT_BUTTON).assertIsEnabled()
  }

  @Test
  fun `given an incomplete account ID, Next is disabled`() {
    setContent(completeState().copy(accountId = "a6b28482"))

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_NEXT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun `given an incomplete recovery key, Next is disabled`() {
    setContent(completeState().copy(recoveryKey = AepInput.from("uy38jh27")))

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_NEXT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun `given an account ID with an invalid character, Next is disabled`() {
    setContent(completeState().copy(accountIdError = AccountIdError.Invalid))

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_NEXT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun `given a rejected login, Next stays disabled until something is edited`() {
    setContent(completeState().copy(areCredentialsIncorrect = true))

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_NEXT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun `while logging in, Next is disabled`() {
    setContent(completeState().copy(isLoggingIn = true))

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_NEXT_BUTTON).assertIsNotEnabled()
  }

  private fun contentTypeOf(tag: String): ContentType? {
    return composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentType)
  }

  private fun stubPasswordManager() {
    mockkObject(SignalCredentialManager)
    every { SignalCredentialManager.isSupported(any()) } returns true
    coEvery { SignalCredentialManager.getCredential(any()) } returns UsernamePasswordCredential(username = VALID_ACCOUNT_ID, password = VALID_RECOVERY_KEY)
  }

  private fun completeState(): SignalLoginCredentialEntryState {
    return SignalLoginCredentialEntryState(
      accountId = VALID_ACCOUNT_ID,
      recoveryKey = AepInput.from(VALID_RECOVERY_KEY)
    )
  }

  private fun setContent(state: SignalLoginCredentialEntryState) {
    composeTestRule.setContent {
      SignalTheme {
        SignalLoginCredentialEntryScreen(
          state = state,
          onEvent = { events += it }
        )
      }
    }
  }
}
