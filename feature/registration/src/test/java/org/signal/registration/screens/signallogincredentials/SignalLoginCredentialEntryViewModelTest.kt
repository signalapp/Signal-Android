/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogincredentials

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId.ACI
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.RegistrationApiV2.RegisterAccountError
import org.signal.network.api.RegistrationApiV2.RegisterAccountResponse
import org.signal.network.api.RegistrationApiV2.RegistrationLockResponse
import org.signal.network.api.RegistrationApiV2.SvrCredentials
import org.signal.registration.KeyMaterial
import org.signal.registration.NetworkController
import org.signal.registration.RegisteredAccountData
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.RestoreDecision
import org.signal.registration.screens.aepentry.AepInput
import org.signal.registration.screens.restoreselection.ArchiveRestoreOption
import org.signal.registration.screens.restoreselection.RegisteredState
import org.signal.registration.screens.shared.AccountIdError
import org.signal.registration.screens.twofactorselection.TwoFactorMethod
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class SignalLoginCredentialEntryViewModelTest {

  companion object {
    private const val VALID_ACCOUNT_ID = "a6b284822e3283d07f2391360a4c2b91"
    private const val VALID_AEP = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t"
    private val VALID_ACI = ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91"))
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var viewModel: SignalLoginCredentialEntryViewModel
  private lateinit var mockRepository: RegistrationRepository
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var emittedStates: MutableList<SignalLoginCredentialEntryState>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var stateEmitter: (SignalLoginCredentialEntryState) -> Unit

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
    emittedParentEvents = mutableListOf()
    emittedStates = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    stateEmitter = { state -> emittedStates.add(state) }
    viewModel = SignalLoginCredentialEntryViewModel(repository = mockRepository, parentEventEmitter = parentEventEmitter)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `a prefilled account ID starts the screen with the ID already in the field`() = runTest(testDispatcher) {
    val prefilled = SignalLoginCredentialEntryViewModel(repository = mockRepository, parentEventEmitter = parentEventEmitter, prefilledAccountId = VALID_ACCOUNT_ID)

    assertThat(prefilled.state.value.accountId).isEqualTo(VALID_ACCOUNT_ID)
  }

  @Test
  fun `a prefilled account ID does not count as user input, so the password manager can still be offered`() = runTest(testDispatcher) {
    val prefilled = SignalLoginCredentialEntryViewModel(repository = mockRepository, parentEventEmitter = parentEventEmitter, prefilledAccountId = VALID_ACCOUNT_ID)

    assertThat(prefilled.state.value.isAccountIdPrefilled).isTrue()
    assertThat(prefilled.state.value.canPromptPasswordManager).isTrue()
  }

  @Test
  fun `without a prefilled account ID the screen starts empty and the password manager can be offered`() = runTest(testDispatcher) {
    assertThat(viewModel.state.value.isAccountIdPrefilled).isFalse()
    assertThat(viewModel.state.value.canPromptPasswordManager).isTrue()
  }

  @Test
  fun `an account ID the user typed themselves stops the password manager from being offered`() = runTest(testDispatcher) {
    applyEvent(
      SignalLoginCredentialEntryState(accountId = VALID_ACCOUNT_ID, isAccountIdPrefilled = true),
      SignalLoginCredentialEntryScreenEvents.AccountIdChanged("a6b28482")
    )

    assertThat(emittedStates.last().isAccountIdPrefilled).isFalse()
    assertThat(emittedStates.last().canPromptPasswordManager).isFalse()
  }

  @Test
  fun `an entered recovery key stops the password manager from being offered`() = runTest(testDispatcher) {
    applyEvent(
      SignalLoginCredentialEntryState(accountId = VALID_ACCOUNT_ID, isAccountIdPrefilled = true),
      SignalLoginCredentialEntryScreenEvents.RecoveryKeyChanged("uy38")
    )

    assertThat(emittedStates.last().canPromptPasswordManager).isFalse()
  }

  @Test
  fun `BackClicked navigates back`() = runTest(testDispatcher) {
    applyEvent(SignalLoginCredentialEntryState(), SignalLoginCredentialEntryScreenEvents.BackClicked)

    assertThat(emittedParentEvents).containsExactly(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `AccountIdChanged strips formatting and lowercases the entered ID`() = runTest(testDispatcher) {
    val state = applyAccountId("A6B28482-2E32-83D0-7F23 91360A4C2B91")

    assertThat(state.accountId).isEqualTo(VALID_ACCOUNT_ID)
    assertThat(state.accountIdError).isNull()
  }

  @Test
  fun `AccountIdChanged does not report an error for a partially typed ID`() = runTest(testDispatcher) {
    val state = applyAccountId("a6b28482")

    assertThat(state.accountIdError).isNull()
    assertThat(state.isNextEnabled).isFalse()
  }

  @Test
  fun `AccountIdChanged reports non-hex characters as invalid`() = runTest(testDispatcher) {
    val state = applyAccountId(VALID_ACCOUNT_ID.dropLast(1) + "z")

    assertThat(state.accountIdError).isEqualTo(AccountIdError.Invalid)
    assertThat(state.isNextEnabled).isFalse()
  }

  @Test
  fun `AccountIdChanged reports an over-long ID as too long`() = runTest(testDispatcher) {
    val state = applyAccountId(VALID_ACCOUNT_ID + "ab")

    assertThat(state.accountIdError).isEqualTo(AccountIdError.TooLong(34))
    assertThat(state.isNextEnabled).isFalse()
  }

  @Test
  fun `RecoveryKeyChanged normalizes the entered key`() = runTest(testDispatcher) {
    applyEvent(SignalLoginCredentialEntryState(), SignalLoginCredentialEntryScreenEvents.RecoveryKeyChanged(VALID_AEP.uppercase()))

    assertThat(emittedStates.last().recoveryKey.normalized).isEqualTo(VALID_AEP)
    assertThat(emittedStates.last().recoveryKey.isValid).isTrue()
  }

  @Test
  fun `editing either half clears a rejected login`() = runTest(testDispatcher) {
    val rejected = completeState().copy(areCredentialsIncorrect = true)

    applyEvent(rejected, SignalLoginCredentialEntryScreenEvents.AccountIdChanged(VALID_ACCOUNT_ID.dropLast(1)))
    assertThat(emittedStates.last().areCredentialsIncorrect).isFalse()

    applyEvent(rejected, SignalLoginCredentialEntryScreenEvents.RecoveryKeyChanged(VALID_AEP.dropLast(1)))
    assertThat(emittedStates.last().areCredentialsIncorrect).isFalse()
  }

  @Test
  fun `PasswordManagerCredentialSelected with a complete credential fills both fields and submits the login`() = runTest(testDispatcher) {
    val aep = AccountEntropyPool(VALID_AEP)
    stubSuccessfulLogin(aep)

    applyEvent(
      SignalLoginCredentialEntryState(),
      SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected(
        accountId = "A6B28482-2E32-83D0-7F23-91360A4C2B91",
        recoveryKey = VALID_AEP.uppercase()
      )
    )

    assertThat(emittedStates.first().accountId).isEqualTo(VALID_ACCOUNT_ID)
    assertThat(emittedStates.first().recoveryKey.normalized).isEqualTo(VALID_AEP)

    coVerify {
      mockRepository.reRegisterAccountWithoutPhoneNumber(
        aci = VALID_ACI,
        recoveryPassword = aep.deriveMasterKey().deriveRegistrationRecoveryPassword(),
        aep = match { it.value == VALID_AEP },
        registrationLock = null
      )
    }
  }

  @Test
  fun `PasswordManagerCredentialSelected with an unusable credential fills the fields without submitting`() = runTest(testDispatcher) {
    applyEvent(
      SignalLoginCredentialEntryState(),
      SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected(accountId = "not-an-account-id", recoveryKey = "short")
    )

    assertThat(emittedParentEvents).isEmpty()
    assertThat(emittedStates.last().isNextEnabled).isFalse()
    coVerify(exactly = 0) { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `PasswordManagerCredentialSelected keeps a prefilled account ID when the credential has no username`() = runTest(testDispatcher) {
    stubSuccessfulLogin(AccountEntropyPool(VALID_AEP))

    applyEvent(
      SignalLoginCredentialEntryState(accountId = VALID_ACCOUNT_ID, isAccountIdPrefilled = true),
      SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected(accountId = "", recoveryKey = VALID_AEP)
    )

    assertThat(emittedStates.first().accountId).isEqualTo(VALID_ACCOUNT_ID)
    coVerify { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `PasswordManagerCredentialSelected clears a previously rejected login`() = runTest(testDispatcher) {
    stubSuccessfulLogin(AccountEntropyPool(VALID_AEP))

    applyEvent(
      completeState().copy(areCredentialsIncorrect = true),
      SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected(accountId = VALID_ACCOUNT_ID, recoveryKey = VALID_AEP)
    )

    assertThat(emittedStates.first().areCredentialsIncorrect).isFalse()
    coVerify { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `RecoveryKeyVisibilityToggled flips whether the key is spelled out`() = runTest(testDispatcher) {
    applyEvent(SignalLoginCredentialEntryState(), SignalLoginCredentialEntryScreenEvents.RecoveryKeyVisibilityToggled)

    assertThat(emittedStates.last().isRecoveryKeyRevealed).isTrue()
  }

  @Test
  fun `NeedHelpClicked opens the help article`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginCredentialEntryScreenActions>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    applyEvent(SignalLoginCredentialEntryState(), SignalLoginCredentialEntryScreenEvents.NeedHelpClicked)

    assertThat(actions).containsExactly(SignalLoginCredentialEntryScreenActions.OpenNeedHelpArticle)
  }

  @Test
  fun `DismissError clears the login error`() = runTest(testDispatcher) {
    applyEvent(completeState().copy(loginError = SignalLoginError.NetworkError), SignalLoginCredentialEntryScreenEvents.DismissError)

    assertThat(emittedStates.last().loginError).isNull()
  }

  @Test
  fun `NextClicked with an account ID that is not a valid ACI reports it as invalid`() = runTest(testDispatcher) {
    applyEvent(completeState().copy(accountId = "a6b28482"), SignalLoginCredentialEntryScreenEvents.NextClicked)

    assertThat(emittedParentEvents).isEmpty()
    assertThat(emittedStates.last().accountIdError).isEqualTo(AccountIdError.Invalid)
  }

  @Test
  fun `NextClicked logs in with the entered account ID and the recovery password derived from the entered key`() = runTest(testDispatcher) {
    val aep = AccountEntropyPool(VALID_AEP)
    stubSuccessfulLogin(aep)

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.NextClicked)

    coVerify {
      mockRepository.reRegisterAccountWithoutPhoneNumber(
        aci = VALID_ACI,
        recoveryPassword = aep.deriveMasterKey().deriveRegistrationRecoveryPassword(),
        aep = match { it.value == VALID_AEP },
        registrationLock = null
      )
    }
  }

  @Test
  fun `NextClicked reclaiming an existing account emits UserSuppliedAepSubmitted, Registered, and navigates to the restore selection`() = runTest(testDispatcher) {
    val aep = AccountEntropyPool(VALID_AEP)
    stubSuccessfulLogin(aep, reregistration = true)
    stubRemoteBackup(exists = true)

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.NextClicked)

    assertThat(emittedParentEvents).hasSize(3)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.UserSuppliedAepSubmitted>()
    assertThat(emittedParentEvents[1])
      .isInstanceOf<RegistrationFlowEvent.Registered>()
      .prop(RegistrationFlowEvent.Registered::phoneNumberless)
      .isEqualTo(true)

    val route = assertThat(emittedParentEvents[2])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.ArchiveRestoreSelection>()

    route.prop(RegistrationRoute.ArchiveRestoreSelection::aep).isNotNull().prop(AccountEntropyPool::value).isEqualTo(aep.value)
    route.prop(RegistrationRoute.ArchiveRestoreSelection::registeredState).isEqualTo(RegisteredState.RegisteredAndPinKnown)
    route.prop(RegistrationRoute.ArchiveRestoreSelection::restoreOptions)
      .containsExactly(ArchiveRestoreOption.SignalSecureBackup, ArchiveRestoreOption.LocalBackup, ArchiveRestoreOption.None)
  }

  @Test
  fun `NextClicked reclaiming an account with no remote backup does not offer a remote restore`() = runTest(testDispatcher) {
    stubSuccessfulLogin(AccountEntropyPool(VALID_AEP), reregistration = true)
    stubRemoteBackup(exists = false)

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.NextClicked)

    assertThat(emittedParentEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.ArchiveRestoreSelection>()
      .prop(RegistrationRoute.ArchiveRestoreSelection::restoreOptions)
      .containsExactly(ArchiveRestoreOption.LocalBackup, ArchiveRestoreOption.None)
  }

  @Test
  fun `NextClicked reclaiming an account still offers a remote restore when the backup check fails`() = runTest(testDispatcher) {
    stubSuccessfulLogin(AccountEntropyPool(VALID_AEP), reregistration = true)
    coEvery { mockRepository.getAndMaybeHealRemoteBackupInfo(any()) } returns RequestResult.RetryableNetworkError(IOException("Network error"))

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.NextClicked)

    assertThat(emittedParentEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.ArchiveRestoreSelection>()
      .prop(RegistrationRoute.ArchiveRestoreSelection::restoreOptions)
      .containsExactly(ArchiveRestoreOption.SignalSecureBackup, ArchiveRestoreOption.LocalBackup, ArchiveRestoreOption.None)
  }

  @Test
  fun `NextClicked registering a brand new account restores the account record and completes registration`() = runTest(testDispatcher) {
    stubSuccessfulLogin(AccountEntropyPool(VALID_AEP), reregistration = false)

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.NextClicked)

    coVerify { mockRepository.setRestoreDecision(RestoreDecision.NEW_ACCOUNT) }
    coVerify { mockRepository.restoreAccountRecord() }
    assertThat(emittedParentEvents.last()).isEqualTo(RegistrationFlowEvent.RegistrationComplete)
  }

  @Test
  fun `NextClicked shows a spinner while the login is in flight`() = runTest(testDispatcher) {
    stubSuccessfulLogin(AccountEntropyPool(VALID_AEP))

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.NextClicked)

    assertThat(emittedStates).hasSize(2)
    assertThat(emittedStates[0].isLoggingIn).isEqualTo(true)
    assertThat(emittedStates[1].isLoggingIn).isEqualTo(false)
  }

  @Test
  fun `NextClicked with an incorrect recovery password flags the entered login`() = runTest(testDispatcher) {
    coEvery { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(RegisterAccountError.RegistrationRecoveryPasswordIncorrect("Incorrect"))

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.NextClicked)

    assertThat(emittedStates.last().areCredentialsIncorrect).isTrue()
    assertThat(emittedStates.last().isLoggingIn).isEqualTo(false)
    assertThat(emittedStates.last().isNextEnabled).isFalse()
  }

  @Test
  fun `NextClicked with RegistrationLock retries with the reglock token derived from the recovery key`() = runTest(testDispatcher) {
    val aep = AccountEntropyPool(VALID_AEP)
    val reglock = aep.deriveMasterKey().deriveRegistrationLock()

    coEvery { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), registrationLock = null, any()) } returns
      RequestResult.NonSuccess(RegisterAccountError.RegistrationLock(registrationLockResponse()))
    coEvery { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), registrationLock = reglock, any()) } returns
      successfulResult(aep)

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.NextClicked)

    coVerify { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), registrationLock = reglock, any()) }
    assertThat(emittedParentEvents[2])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.ArchiveRestoreSelection>()
  }

  @Test
  fun `NextClicked still registration locked after providing the reglock token falls back to PIN entry`() = runTest(testDispatcher) {
    coEvery { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(RegisterAccountError.RegistrationLock(registrationLockResponse()))

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.NextClicked)

    assertThat(emittedStates.last().isLoggingIn).isEqualTo(false)
    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinEntryForRegistrationLock>()
  }

  @Test
  fun `NextClicked with RateLimited sets loginError to RateLimited`() = runTest(testDispatcher) {
    coEvery { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(RegisterAccountError.RateLimited(Duration.parse("1m")))

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.NextClicked)

    assertThat(emittedStates.last().loginError).isEqualTo(SignalLoginError.RateLimited)
  }

  @Test
  fun `NextClicked with InvalidRequest sets loginError to UnknownError`() = runTest(testDispatcher) {
    coEvery { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(RegisterAccountError.InvalidRequest("Bad request"))

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.NextClicked)

    assertThat(emittedStates.last().loginError).isEqualTo(SignalLoginError.UnknownError)
  }

  @Test
  fun `NextClicked with a network error sets loginError to NetworkError`() = runTest(testDispatcher) {
    coEvery { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), any(), any()) } returns
      RequestResult.RetryableNetworkError(IOException("Network error"))

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.NextClicked)

    assertThat(emittedStates.last().loginError).isEqualTo(SignalLoginError.NetworkError)
  }

  @Test
  fun `NextClicked with an application error sets loginError to UnknownError`() = runTest(testDispatcher) {
    coEvery { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), any(), any()) } returns
      RequestResult.ApplicationError(RuntimeException("Unexpected"))

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.NextClicked)

    assertThat(emittedStates.last().loginError).isEqualTo(SignalLoginError.UnknownError)
  }

  @Test
  fun `NextClicked requiring a two-factor code navigates to two-factor selection offering only the authenticator app`() = runTest(testDispatcher) {
    coEvery { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(RegisterAccountError.TotpMissingOrIncorrect)

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.NextClicked)

    assertThat(emittedStates.last().isLoggingIn).isEqualTo(false)
    assertThat(emittedParentEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.TwoFactorSelection>()
      .prop(RegistrationRoute.TwoFactorSelection::methods)
      .containsExactly(TwoFactorMethod.AuthenticatorApp)
  }

  @Test
  fun `TwoFactorCodeEntered retries the login with the entered code`() = runTest(testDispatcher) {
    val aep = AccountEntropyPool(VALID_AEP)
    stubSuccessfulLogin(aep)

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.TwoFactorCodeEntered("123456"))

    coVerify {
      mockRepository.reRegisterAccountWithoutPhoneNumber(
        aci = VALID_ACI,
        recoveryPassword = aep.deriveMasterKey().deriveRegistrationRecoveryPassword(),
        aep = match { it.value == VALID_AEP },
        registrationLock = null,
        totp = 123456
      )
    }
  }

  @Test
  fun `TwoFactorCodeEntered for a screen whose recovery key is gone does not attempt a login`() = runTest(testDispatcher) {
    val state = SignalLoginCredentialEntryState(accountId = VALID_ACCOUNT_ID)

    applyEvent(state, SignalLoginCredentialEntryScreenEvents.TwoFactorCodeEntered("123456"))

    assertThat(emittedStates).isEmpty()
    assertThat(emittedParentEvents).isEmpty()
    coVerify(exactly = 0) { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `TwoFactorCodeEntered while the login is already in flight does not attempt a second login`() = runTest(testDispatcher) {
    applyEvent(completeState().copy(isLoggingIn = true), SignalLoginCredentialEntryScreenEvents.TwoFactorCodeEntered("123456"))

    coVerify(exactly = 0) { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), any(), any(), any()) }
  }

  @Test(expected = IllegalStateException::class)
  fun `NextClicked with SessionNotFoundOrNotVerified throws`() = runTest(testDispatcher) {
    coEvery { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(RegisterAccountError.SessionNotFoundOrNotVerified("Not found"))

    applyEvent(completeState(), SignalLoginCredentialEntryScreenEvents.NextClicked)
  }

  private suspend fun applyEvent(state: SignalLoginCredentialEntryState, event: SignalLoginCredentialEntryScreenEvents) {
    viewModel.applyEvent(state, event, parentEventEmitter, stateEmitter)
  }

  private suspend fun applyAccountId(value: String): SignalLoginCredentialEntryState {
    applyEvent(SignalLoginCredentialEntryState(), SignalLoginCredentialEntryScreenEvents.AccountIdChanged(value))
    return emittedStates.last()
  }

  private fun completeState(): SignalLoginCredentialEntryState {
    return SignalLoginCredentialEntryState(
      accountId = VALID_ACCOUNT_ID,
      recoveryKey = AepInput.from(VALID_AEP)
    )
  }

  private fun registrationLockResponse(): RegistrationLockResponse {
    return RegistrationLockResponse(
      timeRemaining = 86400000L,
      svr2Credentials = SvrCredentials(username = "test-username", password = "test-password")
    )
  }

  private fun successfulResult(aep: AccountEntropyPool, reregistration: Boolean = true): RequestResult.Success<RegisteredAccountData> {
    val keyMaterial = mockk<KeyMaterial>(relaxed = true) {
      every { accountEntropyPool } returns aep
    }
    val response = mockk<RegisterAccountResponse>(relaxed = true) {
      every { this@mockk.reregistration } returns reregistration
      every { e164 } returns null
      every { pni } returns null
    }
    return RequestResult.Success(RegisteredAccountData(response, keyMaterial, VALID_ACI))
  }

  private fun stubSuccessfulLogin(aep: AccountEntropyPool, reregistration: Boolean = true) {
    coEvery { mockRepository.reRegisterAccountWithoutPhoneNumber(any(), any(), any(), any(), any(), any()) } returns successfulResult(aep, reregistration)
  }

  private fun stubRemoteBackup(exists: Boolean) {
    coEvery { mockRepository.getAndMaybeHealRemoteBackupInfo(any()) } returns if (exists) {
      RequestResult.Success(NetworkController.GetBackupInfoResponse(cdn = 3, backupDir = "dir", mediaDir = "media", backupName = "backup", usedSpace = 1024))
    } else {
      RequestResult.NonSuccess(NetworkController.GetBackupInfoError.NoBackup)
    }
  }
}
