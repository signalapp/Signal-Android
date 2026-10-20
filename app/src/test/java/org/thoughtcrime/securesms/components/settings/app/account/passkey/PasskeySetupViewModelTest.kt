/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.passkey

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.account.TwoFactorMethod
import org.signal.appsettings.passkeysetup.PasskeyCreationParameters
import org.signal.appsettings.passkeysetup.PasskeySetupAction
import org.signal.appsettings.passkeysetup.PasskeySetupEvent
import org.signal.appsettings.passkeysetup.PasskeySetupState.Dialog
import org.signal.passwordmanager.PasskeyCreationResult
import org.thoughtcrime.securesms.components.settings.app.account.passkey.PasskeySetupRepository.FinishPasskeyRegistrationResult
import org.thoughtcrime.securesms.components.settings.app.account.passkey.PasskeySetupRepository.StartPasskeyRegistrationResult
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class PasskeySetupViewModelTest {

  companion object {
    private const val MAX_TWO_FACTOR_METHODS = 10

    private const val NEW_PASSKEY_ID = 9L
    private const val NEW_PASSKEY_CREATED_AT = 1_700_000_000_000L
    private val NEW_PASSKEY = TwoFactorMethod(id = NEW_PASSKEY_ID, kind = TwoFactorMethod.Kind.PASSKEY, name = null, createdAt = NEW_PASSKEY_CREATED_AT)

    private val CREATION_PARAMETERS = PasskeyCreationParameters(
      relyingPartyId = "login.signal.org",
      relyingPartyName = "Signal",
      userHandle = byteArrayOf(1, 2, 3),
      userName = "2026-09-22",
      allowedAlgorithms = listOf(-7),
      excludeCredentialIds = emptyList()
    )

    private val ATTESTATION_OBJECT = byteArrayOf(4, 5, 6)
    private const val CLIENT_DATA_JSON = "{\"type\":\"webauthn.create\"}"
    private val CEREMONY_SUCCESS = PasskeyCreationResult.Success(ATTESTATION_OBJECT, CLIENT_DATA_JSON)
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val repository = mockk<PasskeySetupRepository>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { repository.getMaxTwoFactorMethods() } returns MAX_TWO_FACTOR_METHODS
    coEvery { repository.startPasskeyRegistration() } returns StartPasskeyRegistrationResult.Success(CREATION_PARAMETERS)
    coEvery { repository.finishPasskeyRegistration(any(), any()) } returns FinishPasskeyRegistrationResult.Success(NEW_PASSKEY_ID, NEW_PASSKEY_CREATED_AT)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state shows no dialog`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `NavigateBackClicked leaves the screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PasskeySetupEvent.NavigateBackClicked)

    assertThat(actions.last()).isEqualTo(PasskeySetupAction.NavigateBack)
  }

  @Test
  fun `LearnMoreClicked opens the support article`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PasskeySetupEvent.LearnMoreClicked)

    assertThat(actions.last()).isEqualTo(PasskeySetupAction.OpenSupportArticle("https://support.signal.org/hc/articles/11228705649690"))
  }

  @Test
  fun `SetUpPasskeyClicked hands the ceremony parameters out to be run`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PasskeySetupEvent.SetUpPasskeyClicked)

    assertThat(actions.last()).isEqualTo(PasskeySetupAction.CreatePasskey(CREATION_PARAMETERS))
  }

  /** The service leg of the ceremony happens before the provider's sheet, so the tap needs something to show for it. */
  @Test
  fun `SetUpPasskeyClicked shows progress until the ceremony is over`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PasskeySetupEvent.SetUpPasskeyClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.InProgress)

    viewModel.onEvent(PasskeySetupEvent.PasskeyCeremonyCompleted(CEREMONY_SUCCESS))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  /** Another device can fill the account up between account settings checking the limit and the service hearing about it. */
  @Test
  fun `SetUpPasskeyClicked explains the limit the service reports`() = runTest(testDispatcher) {
    coEvery { repository.startPasskeyRegistration() } returns StartPasskeyRegistrationResult.TooManyMethods

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PasskeySetupEvent.SetUpPasskeyClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.MaxTwoFactorMethodsReached(MAX_TWO_FACTOR_METHODS))
    assertThat(actions).isEmpty()
  }

  /** A ceremony that never starts has to take the spinner back down, or the user is stuck looking at it. */
  @Test
  fun `SetUpPasskeyClicked that cannot reach the service takes the progress dialog back down`() = runTest(testDispatcher) {
    coEvery { repository.startPasskeyRegistration() } returns StartPasskeyRegistrationResult.NetworkFailure

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PasskeySetupEvent.SetUpPasskeyClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions.last()).isEqualTo(PasskeySetupAction.ShowPasskeyCreationFailed)
  }

  @Test
  fun `PasskeyCeremonyCompleted registers the credential and sends the user on to name it`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PasskeySetupEvent.PasskeyCeremonyCompleted(CEREMONY_SUCCESS))

    coVerify { repository.finishPasskeyRegistration(ATTESTATION_OBJECT, CLIENT_DATA_JSON) }
    assertThat(actions.last()).isEqualTo(PasskeySetupAction.NavigateToNameNewPasskey(NEW_PASSKEY))
  }

  /** The account can fill up while the provider's sheet is open, leaving a credential that has nowhere to land. */
  @Test
  fun `PasskeyCeremonyCompleted the service has no room for explains the limit`() = runTest(testDispatcher) {
    coEvery { repository.finishPasskeyRegistration(any(), any()) } returns FinishPasskeyRegistrationResult.TooManyMethods

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PasskeySetupEvent.PasskeyCeremonyCompleted(CEREMONY_SUCCESS))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.MaxTwoFactorMethodsReached(MAX_TWO_FACTOR_METHODS))
    assertThat(actions).isEmpty()
  }

  @Test
  fun `a ceremony the service rejects says so rather than sending the user to name nothing`() = runTest(testDispatcher) {
    coEvery { repository.finishPasskeyRegistration(any(), any()) } returns FinishPasskeyRegistrationResult.CeremonyRejected

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PasskeySetupEvent.PasskeyCeremonyCompleted(CEREMONY_SUCCESS))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions.last()).isEqualTo(PasskeySetupAction.ShowPasskeyCreationFailed)
  }

  @Test
  fun `a finish that cannot reach the service says so`() = runTest(testDispatcher) {
    coEvery { repository.finishPasskeyRegistration(any(), any()) } returns FinishPasskeyRegistrationResult.NetworkFailure

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PasskeySetupEvent.PasskeyCeremonyCompleted(CEREMONY_SUCCESS))

    assertThat(actions.last()).isEqualTo(PasskeySetupAction.ShowPasskeyCreationFailed)
  }

  /** Backing out of the provider's sheet is a choice, not a failure, so there is nothing to tell the user about. */
  @Test
  fun `a canceled ceremony takes the progress dialog down and says nothing`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PasskeySetupEvent.SetUpPasskeyClicked)
    viewModel.onEvent(PasskeySetupEvent.PasskeyCeremonyCompleted(PasskeyCreationResult.UserCanceled))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions).containsExactly(PasskeySetupAction.CreatePasskey(CREATION_PARAMETERS))
  }

  @Test
  fun `a device with no passkey provider is told as much`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PasskeySetupEvent.PasskeyCeremonyCompleted(PasskeyCreationResult.NoProviderAvailable))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions.last()).isEqualTo(PasskeySetupAction.ShowNoPasskeyProvider)
  }

  @Test
  fun `a failed ceremony says so`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PasskeySetupEvent.PasskeyCeremonyCompleted(PasskeyCreationResult.CeremonyFailed))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions.last()).isEqualTo(PasskeySetupAction.ShowPasskeyCreationFailed)
  }

  @Test
  fun `DialogDismissed takes the dialog down`() = runTest(testDispatcher) {
    coEvery { repository.startPasskeyRegistration() } returns StartPasskeyRegistrationResult.TooManyMethods

    val viewModel = createViewModel()

    viewModel.onEvent(PasskeySetupEvent.SetUpPasskeyClicked)
    viewModel.onEvent(PasskeySetupEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  private fun createViewModel(): PasskeySetupViewModel = PasskeySetupViewModel(repository)

  private fun TestScope.collectActions(actions: Flow<PasskeySetupAction>): List<PasskeySetupAction> {
    val collected = mutableListOf<PasskeySetupAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
