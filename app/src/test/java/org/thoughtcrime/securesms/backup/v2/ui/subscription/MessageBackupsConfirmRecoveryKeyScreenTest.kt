/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.passwordmanager.SignalCredentialManager
import org.signal.passwordmanager.UsernamePasswordCredential
import org.signal.signallogin.SignalLoginTestTags
import java.util.UUID

/**
 * Checks that the screen only advances when the password manager hands back the recovery key this device already has,
 * and that every other path leaves the user a way to enter it manually.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MessageBackupsConfirmRecoveryKeyScreenTest {

  companion object {
    private val ACI = ServiceId.ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91"))
    private val AEP = AccountEntropyPool("uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t")
    private const val OTHER_KEY = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private var confirmed = false
  private var enteredManually = false
  private var viewedDetails = false

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `confirming a matching saved key advances the flow`() {
    stubCredentialManager(AEP.displayValue)
    setContent()

    composeTestRule.onNodeWithTag(MessageBackupsConfirmRecoveryKeyTestTags.CONFIRM_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertThat(confirmed).isTrue()
  }

  @Test
  fun `confirming a key that does not match shows the failure dialog instead of advancing`() {
    stubCredentialManager(OTHER_KEY)
    setContent()

    composeTestRule.onNodeWithTag(MessageBackupsConfirmRecoveryKeyTestTags.CONFIRM_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertThat(confirmed).isFalse()
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `confirming with no saved credential shows the failure dialog instead of advancing`() {
    stubCredentialManager(null)
    setContent()

    composeTestRule.onNodeWithTag(MessageBackupsConfirmRecoveryKeyTestTags.CONFIRM_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertThat(confirmed).isFalse()
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `the failure dialog sends the user to manual entry`() {
    stubCredentialManager(OTHER_KEY)
    setContent()

    composeTestRule.onNodeWithTag(MessageBackupsConfirmRecoveryKeyTestTags.CONFIRM_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(enteredManually).isTrue()
  }

  @Test
  fun `enter manually skips the password manager entirely`() {
    stubCredentialManager(AEP.displayValue)
    setContent()

    composeTestRule.onNodeWithTag(MessageBackupsConfirmRecoveryKeyTestTags.ENTER_MANUALLY_BUTTON).performClick()

    assertThat(enteredManually).isTrue()
    assertThat(confirmed).isFalse()
  }

  @Test
  fun `view details on the card opens the full login`() {
    setContent()

    composeTestRule.onNodeWithTag(SignalLoginTestTags.CARD_VIEW_DETAILS_BUTTON).performScrollTo().performClick()

    assertThat(viewedDetails).isTrue()
  }

  @Test
  fun `the credential is requested under the account key`() {
    stubCredentialManager(AEP.displayValue)
    setContent()

    composeTestRule.onNodeWithTag(MessageBackupsConfirmRecoveryKeyTestTags.CONFIRM_BUTTON).performClick()
    composeTestRule.waitForIdle()

    coVerify { SignalCredentialManager.getCredential(any(), ACI.toString().uppercase()) }
  }

  private fun stubCredentialManager(password: String?) {
    mockkObject(SignalCredentialManager)
    coEvery { SignalCredentialManager.getCredential(any(), any()) } returns password?.let {
      UsernamePasswordCredential(username = ACI.toString().uppercase(), password = it)
    }
  }

  private fun setContent() {
    composeTestRule.setContent {
      SignalTheme {
        MessageBackupsConfirmRecoveryKeyScreen(
          aci = ACI,
          aep = AEP,
          onViewDetailsClick = { viewedDetails = true },
          onConfirmed = { confirmed = true },
          onEnterManuallyClick = { enteredManually = true }
        )
      }
    }
  }
}
