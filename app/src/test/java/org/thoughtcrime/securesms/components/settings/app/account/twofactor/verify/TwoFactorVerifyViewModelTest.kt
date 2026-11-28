/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.twofactor.verify

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.appsettings.twofactorverify.PasskeyAssertionParameters
import org.signal.appsettings.twofactorverify.TwoFactorVerifyAction
import org.signal.appsettings.twofactorverify.TwoFactorVerifyEvent
import org.signal.appsettings.twofactorverify.TwoFactorVerifyMethod
import org.signal.appsettings.twofactorverify.TwoFactorVerifyState.CodeError
import org.signal.appsettings.twofactorverify.TwoFactorVerifyState.Dialog
import org.signal.appsettings.twofactorverify.TwoFactorVerifyState.Step
import org.signal.passwordmanager.PasskeyAssertionResult
import org.signal.uicomponents.codeentryfield.CodeEntryFieldEvents
import org.thoughtcrime.securesms.components.settings.app.account.twofactor.verify.TwoFactorVerifyRepository.StartResult
import org.thoughtcrime.securesms.components.settings.app.account.twofactor.verify.TwoFactorVerifyRepository.VerifyResult

@OptIn(ExperimentalCoroutinesApi::class)
class TwoFactorVerifyViewModelTest {

  companion object {
    private const val CODE = "418372"
    private const val ASSERTION_JSON = "{\"type\":\"webauthn.get\"}"

    private val PASSKEY = PasskeyAssertionParameters(
      relyingPartyId = "login.signal.org",
      challenge = byteArrayOf(1, 2, 3),
      timeoutSeconds = 60,
      allowedCredentialIds = listOf(byteArrayOf(4, 5, 6))
    )

    private val BOTH_METHODS = StartResult.Success(hasAuthenticatorApp = true, passkey = PASSKEY)
    private val AUTHENTICATOR_APP_ONLY = StartResult.Success(hasAuthenticatorApp = true, passkey = null)
    private val PASSKEY_ONLY = StartResult.Success(hasAuthenticatorApp = false, passkey = PASSKEY)
    private val NO_METHODS = StartResult.Success(hasAuthenticatorApp = false, passkey = null)
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  private val repository = mockk<TwoFactorVerifyRepository>(relaxUnitFun = true)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    coEvery { repository.startVerification() } returns BOTH_METHODS
    coEvery { repository.verifyCode(any()) } returns VerifyResult.Success
    coEvery { repository.verifyPasskey(any()) } returns VerifyResult.Success
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `an account with both methods starts on the method choice`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.step).isEqualTo(Step.ChooseMethod(hasAuthenticatorApp = true))
  }

  @Test
  fun `an account with only authenticator apps skips straight to code entry`() = runTest(testDispatcher) {
    coEvery { repository.startVerification() } returns AUTHENTICATOR_APP_ONLY

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.step).isEqualTo(Step.EnterCode(canReturnToMethodChoice = false))
  }

  @Test
  fun `an account with only a passkey still gets the method choice`() = runTest(testDispatcher) {
    coEvery { repository.startVerification() } returns PASSKEY_ONLY

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.step).isEqualTo(Step.ChooseMethod(hasAuthenticatorApp = false))
  }

  @Test
  fun `a passkey is left out when the device cannot run a ceremony`() = runTest(testDispatcher) {
    val viewModel = createViewModel(arePasskeysSupported = false)

    assertThat(viewModel.state.value.step).isEqualTo(Step.EnterCode(canReturnToMethodChoice = false))
  }

  @Test
  fun `an account with only a passkey closes when the device cannot run a ceremony`() = runTest(testDispatcher) {
    coEvery { repository.startVerification() } returns PASSKEY_ONLY

    val viewModel = createViewModel(arePasskeysSupported = false)
    val actions = collectActions(viewModel.actions)

    assertThat(actions).containsExactly(TwoFactorVerifyAction.Close)
    verify(exactly = 0) { repository.markNoMethods() }
  }

  @Test
  fun `an account with no methods is recorded and closes`() = runTest(testDispatcher) {
    coEvery { repository.startVerification() } returns NO_METHODS

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    verify { repository.markNoMethods() }
    assertThat(actions).containsExactly(TwoFactorVerifyAction.Close)
  }

  @Test
  fun `a network failure while loading shows a dialog that closes the flow`() = runTest(testDispatcher) {
    coEvery { repository.startVerification() } returns StartResult.NetworkFailure

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.NetworkFailure)
    assertThat(actions).isEmpty()

    viewModel.onEvent(TwoFactorVerifyEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions).containsExactly(TwoFactorVerifyAction.Close)
  }

  @Test
  fun `AuthenticatorAppSelected moves to code entry with a way back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(TwoFactorVerifyEvent.AuthenticatorAppSelected)

    assertThat(viewModel.state.value.step).isEqualTo(Step.EnterCode(canReturnToMethodChoice = true))
  }

  @Test
  fun `BackClicked returns to the method choice from code entry`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorVerifyEvent.AuthenticatorAppSelected)
    viewModel.onEvent(TwoFactorVerifyEvent.CodeEntryEvent(CodeEntryFieldEvents.CodeChanged("418")))
    viewModel.onEvent(TwoFactorVerifyEvent.BackClicked)

    assertThat(viewModel.state.value.step).isEqualTo(Step.ChooseMethod(hasAuthenticatorApp = true))
    assertThat(viewModel.state.value.codeEntry.code).isEqualTo("")
    assertThat(actions).isEmpty()
  }

  @Test
  fun `BackClicked closes when there is nothing to go back to`() = runTest(testDispatcher) {
    coEvery { repository.startVerification() } returns AUTHENTICATOR_APP_ONLY

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorVerifyEvent.BackClicked)

    assertThat(actions).containsExactly(TwoFactorVerifyAction.Close)
  }

  @Test
  fun `CancelClicked closes the flow`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorVerifyEvent.CancelClicked)

    assertThat(actions).containsExactly(TwoFactorVerifyAction.Close)
  }

  @Test
  fun `a complete code is checked and a good one finishes the flow`() = runTest(testDispatcher) {
    coEvery { repository.startVerification() } returns AUTHENTICATOR_APP_ONLY

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorVerifyEvent.CodeEntryEvent(CodeEntryFieldEvents.CodeChanged(CODE)))

    coVerify { repository.verifyCode(CODE) }
    verify { repository.markVerified() }
    assertThat(viewModel.state.value.submitting).isTrue()
    assertThat(actions).containsExactly(TwoFactorVerifyAction.Verified(TwoFactorVerifyMethod.AUTHENTICATOR_APP))
  }

  @Test
  fun `a rejected code shows an error and clears the field`() = runTest(testDispatcher) {
    coEvery { repository.startVerification() } returns AUTHENTICATOR_APP_ONLY
    coEvery { repository.verifyCode(any()) } returns VerifyResult.NotVerified

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorVerifyEvent.CodeEntryEvent(CodeEntryFieldEvents.CodeChanged(CODE)))

    assertThat(viewModel.state.value.codeError).isEqualTo(CodeError.IncorrectCode)
    assertThat(viewModel.state.value.submitting).isFalse()
    assertThat(viewModel.state.value.codeEntry.code).isEqualTo("")
    assertThat(actions).isEmpty()
    verify(exactly = 0) { repository.markVerified() }
  }

  @Test
  fun `a network failure while checking a code shows an error and keeps the code`() = runTest(testDispatcher) {
    coEvery { repository.startVerification() } returns AUTHENTICATOR_APP_ONLY
    coEvery { repository.verifyCode(any()) } returns VerifyResult.NetworkFailure

    val viewModel = createViewModel()

    viewModel.onEvent(TwoFactorVerifyEvent.CodeEntryEvent(CodeEntryFieldEvents.CodeChanged(CODE)))

    assertThat(viewModel.state.value.codeError).isEqualTo(CodeError.NetworkFailure)
    assertThat(viewModel.state.value.codeEntry.code).isEqualTo(CODE)
  }

  @Test
  fun `typing clears the code error`() = runTest(testDispatcher) {
    coEvery { repository.startVerification() } returns AUTHENTICATOR_APP_ONLY
    coEvery { repository.verifyCode(any()) } returns VerifyResult.NotVerified

    val viewModel = createViewModel()

    viewModel.onEvent(TwoFactorVerifyEvent.CodeEntryEvent(CodeEntryFieldEvents.CodeChanged(CODE)))
    viewModel.onEvent(TwoFactorVerifyEvent.CodeEntryEvent(CodeEntryFieldEvents.CodeChanged("4")))

    assertThat(viewModel.state.value.codeError).isEqualTo(CodeError.None)
  }

  @Test
  fun `the field clearing itself after a rejected code keeps the code error`() = runTest(testDispatcher) {
    coEvery { repository.startVerification() } returns AUTHENTICATOR_APP_ONLY
    coEvery { repository.verifyCode(any()) } returns VerifyResult.NotVerified

    val viewModel = createViewModel()

    viewModel.onEvent(TwoFactorVerifyEvent.CodeEntryEvent(CodeEntryFieldEvents.CodeChanged(CODE)))
    viewModel.onEvent(TwoFactorVerifyEvent.CodeEntryEvent(CodeEntryFieldEvents.CodeChanged("")))
    viewModel.onEvent(TwoFactorVerifyEvent.CodeEntryEvent(CodeEntryFieldEvents.OverwriteApplied))

    assertThat(viewModel.state.value.codeError).isEqualTo(CodeError.IncorrectCode)
  }

  @Test
  fun `PasskeySelected fetches a fresh challenge and hands it to the host`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorVerifyEvent.PasskeySelected)

    coVerify(exactly = 2) { repository.startVerification() }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.PasskeyInProgress)
    assertThat(actions).containsExactly(TwoFactorVerifyAction.AuthenticateWithPasskey(PASSKEY))
  }

  @Test
  fun `PasskeySelected fails when the challenge cannot be fetched`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    coEvery { repository.startVerification() } returns StartResult.NetworkFailure
    viewModel.onEvent(TwoFactorVerifyEvent.PasskeySelected)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.PasskeyFailed)
    assertThat(actions).isEmpty()
  }

  @Test
  fun `a successful ceremony is checked and finishes the flow`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorVerifyEvent.PasskeyCeremonyCompleted(PasskeyAssertionResult.Success(ASSERTION_JSON)))

    coVerify { repository.verifyPasskey(ASSERTION_JSON) }
    verify { repository.markVerified() }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions).containsExactly(TwoFactorVerifyAction.Verified(TwoFactorVerifyMethod.PASSKEY))
  }

  @Test
  fun `an assertion the service rejects shows the passkey error`() = runTest(testDispatcher) {
    coEvery { repository.verifyPasskey(any()) } returns VerifyResult.NotVerified

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorVerifyEvent.PasskeyCeremonyCompleted(PasskeyAssertionResult.Success(ASSERTION_JSON)))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.PasskeyFailed)
    assertThat(actions).isEmpty()
    verify(exactly = 0) { repository.markVerified() }
  }

  @Test
  fun `a canceled ceremony just dismisses the progress dialog`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorVerifyEvent.PasskeySelected)
    viewModel.onEvent(TwoFactorVerifyEvent.PasskeyCeremonyCompleted(PasskeyAssertionResult.UserCanceled))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(viewModel.state.value.step).isEqualTo(Step.ChooseMethod(hasAuthenticatorApp = true))
    assertThat(actions).containsExactly(TwoFactorVerifyAction.AuthenticateWithPasskey(PASSKEY))
  }

  @Test
  fun `a failed ceremony shows the passkey error`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(TwoFactorVerifyEvent.PasskeyCeremonyCompleted(PasskeyAssertionResult.CeremonyFailed))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.PasskeyFailed)
  }

  @Test
  fun `dismissing the passkey error leaves the user on the method choice`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorVerifyEvent.PasskeyCeremonyCompleted(PasskeyAssertionResult.NoCredentialAvailable))
    viewModel.onEvent(TwoFactorVerifyEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(viewModel.state.value.step).isEqualTo(Step.ChooseMethod(hasAuthenticatorApp = true))
    assertThat(actions).isEmpty()
  }

  @Test
  fun `state never logs the code`() = runTest(testDispatcher) {
    coEvery { repository.startVerification() } returns AUTHENTICATOR_APP_ONLY
    coEvery { repository.verifyCode(any()) } returns VerifyResult.NetworkFailure

    val viewModel = createViewModel()

    viewModel.onEvent(TwoFactorVerifyEvent.CodeEntryEvent(CodeEntryFieldEvents.CodeChanged(CODE)))

    assertThat(viewModel.state.value.toString().contains(CODE)).isFalse()
    assertThat(viewModel.state.value.codeEntry.isComplete).isTrue()
  }

  private fun createViewModel(arePasskeysSupported: Boolean = true): TwoFactorVerifyViewModel {
    return TwoFactorVerifyViewModel(arePasskeysSupported = arePasskeysSupported, repository = repository)
  }

  private fun TestScope.collectActions(actions: Flow<TwoFactorVerifyAction>): List<TwoFactorVerifyAction> {
    val collected = mutableListOf<TwoFactorVerifyAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
