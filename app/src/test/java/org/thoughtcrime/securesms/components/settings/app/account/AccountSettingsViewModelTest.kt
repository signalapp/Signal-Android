/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
import org.signal.appsettings.account.AccountSettingsAction
import org.signal.appsettings.account.AccountSettingsEvent
import org.signal.appsettings.account.AccountSettingsState.Dialog
import org.signal.appsettings.account.AccountSettingsState.LoadState
import org.signal.appsettings.account.PasskeyCreationParameters
import org.signal.appsettings.account.TwoFactorMethod
import org.signal.core.models.MasterKey
import org.signal.libsignal.net.MfaKeyKind
import org.signal.libsignal.net.RequestResult
import org.signal.network.service.TwoFactorMethodService
import org.signal.passwordmanager.PasskeyCreationResult
import org.thoughtcrime.securesms.components.settings.app.account.AccountSettingsRepository.FinishPasskeyRegistrationResult
import org.thoughtcrime.securesms.components.settings.app.account.AccountSettingsRepository.StartPasskeyRegistrationResult
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import java.io.IOException
import java.time.Instant
import org.signal.network.service.TwoFactorMethodService.TwoFactorMethod as ServiceTwoFactorMethod

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsViewModelTest {

  companion object {
    private const val CORRECT_PIN = "1234"
    private const val INCORRECT_PIN = "9999"

    private val MASTER_KEY = MasterKey(ByteArray(32) { (it + 100).toByte() })

    private val TOTP_APP = TwoFactorMethod(id = 1, kind = TwoFactorMethod.Kind.AUTHENTICATOR_APP, name = "Bitwarden Authenticator", createdAt = 0)
    private val OTHER_TOTP_APP = TwoFactorMethod(id = 2, kind = TwoFactorMethod.Kind.AUTHENTICATOR_APP, name = "Twilio Authy", createdAt = 0)
    private val PASSKEY = TwoFactorMethod(id = 1, kind = TwoFactorMethod.Kind.PASSKEY, name = "Pixel Phone", createdAt = 0)

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
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val repository = mockk<AccountSettingsRepository>(relaxUnitFun = true)
  private val twoFactorMethodService = mockk<TwoFactorMethodService>(relaxUnitFun = true)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { repository.hasPin() } returns true
    every { repository.hasRestoredAep() } returns false
    every { repository.arePinRemindersEnabled() } returns true
    every { repository.isRegistrationLockEnabled() } returns false
    every { repository.isUserUnregistered() } returns false
    every { repository.isClientDeprecated() } returns false
    every { repository.getPinKeyboardType() } returns PinKeyboardType.NUMERIC
    every { repository.isPhoneNumberless() } returns false
    every { repository.masterKey() } returns MASTER_KEY
    every { repository.getMaxTotpApps() } returns 2
    every { repository.getMaxTwoFactorMethods() } returns 10
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods()
    coEvery { twoFactorMethodService.removeMethod(any()) } returns RequestResult.Success(Unit)
    coEvery { repository.startPasskeyRegistration() } returns StartPasskeyRegistrationResult.Success(CREATION_PARAMETERS)
    coEvery { repository.finishPasskeyRegistration(any(), any()) } returns FinishPasskeyRegistrationResult.Success(NEW_PASSKEY_ID, NEW_PASSKEY_CREATED_AT)
    every { repository.verifyLocalPin(any()) } answers { firstArg<String>() == CORRECT_PIN }
    coEvery { repository.setRegistrationLockEnabled(any()) } returns true
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state is read out of the repository`() = runTest(testDispatcher) {
    every { repository.isRegistrationLockEnabled() } returns true

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.hasPin).isTrue()
    assertThat(viewModel.state.value.pinRemindersEnabled).isTrue()
    assertThat(viewModel.state.value.registrationLockEnabled).isTrue()
  }

  @Test
  fun `ModifyPinClicked launches the change flow when the user has a PIN`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.ModifyPinClicked)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.LaunchChangePinFlow)
  }

  @Test
  fun `ModifyPinClicked launches the create flow when the user has no PIN`() = runTest(testDispatcher) {
    every { repository.hasPin() } returns false

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.ModifyPinClicked)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.LaunchCreatePinFlow)
  }

  @Test
  fun `turning PIN reminders on writes straight through`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.PinRemindersToggled(true))

    verify { repository.setPinRemindersEnabled(true) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `turning PIN reminders off asks for the PIN first`() = runTest(testDispatcher) {
    every { repository.getPinKeyboardType() } returns PinKeyboardType.ALPHA_NUMERIC

    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.PinRemindersToggled(false))

    verify(exactly = 0) { repository.setPinRemindersEnabled(any()) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmPinToDisableReminders(isAlphanumericKeyboard = true))
  }

  @Test
  fun `a correct PIN turns reminders off and closes the dialog`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.PinRemindersToggled(false))
    viewModel.onEvent(AccountSettingsEvent.PinEntryChanged(CORRECT_PIN))
    viewModel.onEvent(AccountSettingsEvent.DisablePinRemindersConfirmed)

    verify { repository.setPinRemindersEnabled(false) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `canSubmit only turns on once the PIN is long enough`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.PinRemindersToggled(false))

    viewModel.onEvent(AccountSettingsEvent.PinEntryChanged("123"))
    assertThat((viewModel.state.value.dialog as Dialog.ConfirmPinToDisableReminders).canSubmit).isFalse()

    viewModel.onEvent(AccountSettingsEvent.PinEntryChanged("1234"))
    assertThat((viewModel.state.value.dialog as Dialog.ConfirmPinToDisableReminders).canSubmit).isTrue()
  }

  @Test
  fun `an incorrect PIN leaves the dialog up with an error`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.PinRemindersToggled(false))
    viewModel.onEvent(AccountSettingsEvent.PinEntryChanged(INCORRECT_PIN))
    viewModel.onEvent(AccountSettingsEvent.DisablePinRemindersConfirmed)

    verify(exactly = 0) { repository.setPinRemindersEnabled(any()) }

    val dialog = viewModel.state.value.dialog
    assertThat(dialog).isInstanceOf(Dialog.ConfirmPinToDisableReminders::class)
    assertThat((dialog as Dialog.ConfirmPinToDisableReminders).incorrectPin).isTrue()
  }

  @Test
  fun `typing again clears the incorrect PIN error`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.PinRemindersToggled(false))
    viewModel.onEvent(AccountSettingsEvent.PinEntryChanged(INCORRECT_PIN))
    viewModel.onEvent(AccountSettingsEvent.DisablePinRemindersConfirmed)
    viewModel.onEvent(AccountSettingsEvent.PinEntryChanged("1"))

    val dialog = viewModel.state.value.dialog as Dialog.ConfirmPinToDisableReminders
    assertThat(dialog.incorrectPin).isFalse()
    assertThat(dialog.pin).isEqualTo("1")
    assertThat(dialog.canSubmit).isFalse()
  }

  @Test
  fun `toggling the keyboard swaps the type and clears what was typed`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.PinRemindersToggled(false))
    viewModel.onEvent(AccountSettingsEvent.PinEntryChanged(CORRECT_PIN))
    viewModel.onEvent(AccountSettingsEvent.PinKeyboardToggled)

    val dialog = viewModel.state.value.dialog as Dialog.ConfirmPinToDisableReminders
    assertThat(dialog.isAlphanumericKeyboard).isTrue()
    assertThat(dialog.pin).isEqualTo("")
    assertThat(dialog.canSubmit).isFalse()
  }

  @Test
  fun `RegistrationLockToggled asks for confirmation before touching the service`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.RegistrationLockToggled(true))

    coVerify(exactly = 0) { repository.setRegistrationLockEnabled(any()) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmRegistrationLock(enable = true))
  }

  @Test
  fun `confirming registration lock enables it and closes the dialog`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.RegistrationLockToggled(true))
    viewModel.onEvent(AccountSettingsEvent.RegistrationLockConfirmed)

    coVerify { repository.setRegistrationLockEnabled(true) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions).isEqualTo(emptyList<AccountSettingsAction>())
  }

  @Test
  fun `a failed registration lock change reports the failure`() = runTest(testDispatcher) {
    coEvery { repository.setRegistrationLockEnabled(any()) } returns false

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.RegistrationLockToggled(false))
    viewModel.onEvent(AccountSettingsEvent.RegistrationLockConfirmed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions.last()).isEqualTo(AccountSettingsAction.ShowRegistrationLockDisableFailed)
  }

  @Test
  fun `DeleteAllDataConfirmed closes the dialog and asks the fragment to wipe`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.DeleteAllDataClicked)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmDeleteAllData)

    viewModel.onEvent(AccountSettingsEvent.DeleteAllDataConfirmed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions.last()).isEqualTo(AccountSettingsAction.WipeAllData)
  }

  @Test
  fun `DataWipeFailed reports the failure`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.DataWipeFailed)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.ShowDataWipeFailed)
  }

  @Test
  fun `DialogDismissed clears whatever dialog is up`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.DeleteAllDataClicked)
    viewModel.onEvent(AccountSettingsEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `ScreenResumed re-reads state without disturbing the dialog`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.DeleteAllDataClicked)

    every { repository.isClientDeprecated() } returns true
    viewModel.onEvent(AccountSettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.clientDeprecated).isTrue()
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmDeleteAllData)
  }

  @Test
  fun `PinCreated refreshes state and confirms to the user`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.PinCreated)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.ShowPinCreatedConfirmation)
  }

  @Test
  fun `the Signal Login section is left out when the account has a phone number`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.isPhoneNumberless).isFalse()
    assertThat(viewModel.state.value.signalLogin).isNull()
  }

  @Test
  fun `the Signal Login section is filled in when the account is phone-numberless`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP, PASSKEY)

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.isPhoneNumberless).isTrue()
    assertThat(viewModel.state.value.signalLogin!!.twoFactorMethods).containsExactly(TOTP_APP, PASSKEY)
    assertThat(viewModel.state.value.signalLogin?.loadState).isEqualTo(LoadState.LOADED)
    assertThat(viewModel.state.value.signalLogin?.maxTotpApps).isEqualTo(2)
    assertThat(viewModel.state.value.signalLogin?.maxTwoFactorMethods).isEqualTo(10)
  }

  /** An empty list says nothing on its own, so the screen leans on the load state to know we haven't heard back yet. */
  @Test
  fun `the two-factor list is LOADING until we've heard back about the account`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } coAnswers { awaitCancellation() }

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.signalLogin?.loadState).isEqualTo(LoadState.LOADING)
    assertThat(viewModel.state.value.signalLogin!!.twoFactorMethods).isEmpty()
  }

  /** An account we couldn't ask about is not an account with no second factors. */
  @Test
  fun `a service we couldn't reach clears the two-factor list and says so`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns RequestResult.RetryableNetworkError(IOException("offline"))

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.signalLogin?.loadState).isEqualTo(LoadState.NETWORK_FAILURE)
    assertThat(viewModel.state.value.signalLogin!!.twoFactorMethods).isEmpty()
  }

  @Test
  fun `ScreenResumed picks up second factors added elsewhere`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true

    val viewModel = createViewModel()

    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)
    viewModel.onEvent(AccountSettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.signalLogin!!.twoFactorMethods).containsExactly(TOTP_APP)
  }

  @Test
  fun `AddTotpAppClicked opens setup when there's room for another app`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.AddTotpAppClicked)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.NavigateToTotpSetup)
  }

  /** The app limit is the more specific of the two, so it's what an account with room to spare overall is told about. */
  @Test
  fun `AddTotpAppClicked explains the limit when there's no room for another app`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP, OTHER_TOTP_APP, PASSKEY)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.AddTotpAppClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.MaxTotpAppsReached)
    assertThat(actions).isEmpty()
  }

  /** Every second factor counts against one overall limit, so a passkey can be what leaves no room for another app. */
  @Test
  fun `AddTotpAppClicked explains the overall limit when there's no room for another second factor`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    every { repository.getMaxTwoFactorMethods() } returns 2
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP, PASSKEY)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.AddTotpAppClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.MaxTwoFactorMethodsReached)
    assertThat(actions).isEmpty()
  }

  @Test
  fun `AddPasskeyClicked hands the ceremony parameters out to be run`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.AddPasskeyClicked)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.CreatePasskey(CREATION_PARAMETERS))
  }

  /** The service leg of the ceremony happens before the provider's sheet, so the tap needs something to show for it. */
  @Test
  fun `AddPasskeyClicked shows progress until the ceremony is over`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)

    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.AddPasskeyClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.PasskeyInProgress)

    viewModel.onEvent(AccountSettingsEvent.PasskeyCeremonyCompleted(PasskeyCreationResult.Success(ATTESTATION_OBJECT, CLIENT_DATA_JSON)))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `a canceled ceremony takes the progress dialog back down`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)

    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.AddPasskeyClicked)
    viewModel.onEvent(AccountSettingsEvent.PasskeyCeremonyCompleted(PasskeyCreationResult.UserCanceled))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  /** Passkeys share the one overall limit with apps, and have no tighter limit of their own. */
  @Test
  fun `AddPasskeyClicked explains the overall limit rather than starting a ceremony`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    every { repository.getMaxTwoFactorMethods() } returns 2
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP, PASSKEY)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.AddPasskeyClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.MaxTwoFactorMethodsReached)
    assertThat(actions).isEmpty()
    coVerify(exactly = 0) { repository.startPasskeyRegistration() }
  }

  /** Another device can fill the account up between the limit check here and the service hearing about it. */
  @Test
  fun `AddPasskeyClicked explains the limit the service reports`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)
    coEvery { repository.startPasskeyRegistration() } returns StartPasskeyRegistrationResult.TooManyMethods

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.AddPasskeyClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.MaxTwoFactorMethodsReached)
    assertThat(actions).isEmpty()
  }

  /** A ceremony that never starts has to take the spinner back down, or the user is stuck looking at it. */
  @Test
  fun `AddPasskeyClicked that cannot reach the service takes the progress dialog back down`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)
    coEvery { repository.startPasskeyRegistration() } returns StartPasskeyRegistrationResult.NetworkFailure

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.AddPasskeyClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions.last()).isEqualTo(AccountSettingsAction.ShowPasskeyCreationFailed)
  }

  /** The account can fill up while the provider's sheet is open, leaving a credential that has nowhere to land. */
  @Test
  fun `PasskeyCeremonyCompleted the service has no room for explains the limit`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)
    coEvery { repository.finishPasskeyRegistration(any(), any()) } returns FinishPasskeyRegistrationResult.TooManyMethods

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.PasskeyCeremonyCompleted(PasskeyCreationResult.Success(ATTESTATION_OBJECT, CLIENT_DATA_JSON)))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.MaxTwoFactorMethodsReached)
    assertThat(actions).isEmpty()
  }

  @Test
  fun `PasskeyCeremonyCompleted registers the credential and sends the user on to name it`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.PasskeyCeremonyCompleted(PasskeyCreationResult.Success(ATTESTATION_OBJECT, CLIENT_DATA_JSON)))

    coVerify { repository.finishPasskeyRegistration(ATTESTATION_OBJECT, CLIENT_DATA_JSON) }
    assertThat(actions.last()).isEqualTo(AccountSettingsAction.NavigateToNameNewPasskey(NEW_PASSKEY))
  }

  /** The new passkey has a default name until the naming screen replaces it, so the list has to show it either way. */
  @Test
  fun `PasskeyCeremonyCompleted re-reads the list`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)

    val viewModel = createViewModel()

    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP, PASSKEY)
    viewModel.onEvent(AccountSettingsEvent.PasskeyCeremonyCompleted(PasskeyCreationResult.Success(ATTESTATION_OBJECT, CLIENT_DATA_JSON)))

    assertThat(viewModel.state.value.signalLogin!!.twoFactorMethods).containsExactly(TOTP_APP, PASSKEY)
  }

  @Test
  fun `a ceremony the service rejects says so rather than sending the user to name nothing`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)
    coEvery { repository.finishPasskeyRegistration(any(), any()) } returns FinishPasskeyRegistrationResult.CeremonyRejected

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.PasskeyCeremonyCompleted(PasskeyCreationResult.Success(ATTESTATION_OBJECT, CLIENT_DATA_JSON)))

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.ShowPasskeyCreationFailed)
  }

  /** Backing out of the provider's sheet is a choice, not a failure, so there is nothing to tell the user about. */
  @Test
  fun `a canceled ceremony says nothing`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.PasskeyCeremonyCompleted(PasskeyCreationResult.UserCanceled))

    assertThat(actions).isEmpty()
  }

  @Test
  fun `a device with no passkey provider is told as much`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.PasskeyCeremonyCompleted(PasskeyCreationResult.NoProviderAvailable))

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.ShowNoPasskeyProvider)
  }

  @Test
  fun `RenameMethodClicked opens the naming screen for that method`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.RenameMethodClicked(TOTP_APP))

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.NavigateToRenameMethod(TOTP_APP))
  }

  /** Ids only mean anything within a kind, so a passkey sharing an id with an app must carry its kind through. */
  @Test
  fun `RenameMethodClicked opens the naming screen for a passkey`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP, PASSKEY)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.RenameMethodClicked(PASSKEY))

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.NavigateToRenameMethod(PASSKEY))
  }

  /** Removing a second factor is guarded by the screen lock, so nothing happens until the user gets past it. */
  @Test
  fun `RemoveMethodClicked asks for the screen lock first`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.RemoveMethodClicked(TOTP_APP))

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.AuthenticateToRemoveMethod(TOTP_APP))
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `MethodRemovalAuthenticated asks the user to confirm before removing`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)

    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.MethodRemovalAuthenticated(TOTP_APP))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmRemoveMethod(TOTP_APP))
  }

  @Test
  fun `AuthenticationFailed says so and removes nothing`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.RemoveMethodClicked(TOTP_APP))
    viewModel.onEvent(AccountSettingsEvent.AuthenticationFailed)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.ShowAuthenticationFailed)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    coVerify(exactly = 0) { twoFactorMethodService.removeMethod(any()) }
  }

  @Test
  fun `MethodRemovalAuthenticated asks the user to confirm removing a passkey too`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(PASSKEY)

    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.MethodRemovalAuthenticated(PASSKEY))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmRemoveMethod(PASSKEY))
  }

  @Test
  fun `RemoveMethodConfirmed removes the app and says so`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.MethodRemovalAuthenticated(TOTP_APP))
    viewModel.onEvent(AccountSettingsEvent.RemoveMethodConfirmed(TOTP_APP))

    coVerify { twoFactorMethodService.removeMethod(TOTP_APP.id) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions.last()).isEqualTo(AccountSettingsAction.ShowMethodRemoved(TwoFactorMethod.Kind.AUTHENTICATOR_APP))
  }

  /** The dialog dismisses itself before it confirms, so the removal has to survive the dismissal that lands first. */
  @Test
  fun `RemoveMethodConfirmed removes the app even though the dialog dismissed itself first`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.MethodRemovalAuthenticated(TOTP_APP))
    viewModel.onEvent(AccountSettingsEvent.DialogDismissed)
    viewModel.onEvent(AccountSettingsEvent.RemoveMethodConfirmed(TOTP_APP))

    coVerify { twoFactorMethodService.removeMethod(TOTP_APP.id) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions.last()).isEqualTo(AccountSettingsAction.ShowMethodRemoved(TwoFactorMethod.Kind.AUTHENTICATOR_APP))
  }

  /** The list is what tells the user the app is gone, so it has to be read again rather than assumed. */
  @Test
  fun `a removal re-reads the list`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)

    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.MethodRemovalAuthenticated(TOTP_APP))

    coEvery { twoFactorMethodService.getMethods(any()) } returns methods()
    viewModel.onEvent(AccountSettingsEvent.RemoveMethodConfirmed(TOTP_APP))

    assertThat(viewModel.state.value.signalLogin!!.twoFactorMethods).isEmpty()
  }

  @Test
  fun `a removal that didn't go through says so rather than pretending the app is gone`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { twoFactorMethodService.getMethods(any()) } returns methods(TOTP_APP)
    coEvery { twoFactorMethodService.removeMethod(any()) } returns RequestResult.RetryableNetworkError(IOException("offline"))

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.MethodRemovalAuthenticated(TOTP_APP))
    viewModel.onEvent(AccountSettingsEvent.RemoveMethodConfirmed(TOTP_APP))

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.ShowMethodRemovalFailed(TwoFactorMethod.Kind.AUTHENTICATOR_APP))
    assertThat(viewModel.state.value.signalLogin!!.twoFactorMethods).containsExactly(TOTP_APP)
  }

  @Test
  fun `LearnMoreClicked opens the support article`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.LearnMoreClicked("https://support.signal.org/hc/articles/11228705649690"))

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.OpenSupportArticle("https://support.signal.org/hc/articles/11228705649690"))
  }

  @Test
  fun `AccountAndRecoveryClicked asks for the screen lock first`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.AccountAndRecoveryClicked)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.AuthenticateToViewSignalLoginDetails)
  }

  @Test
  fun `SignalLoginDetailsAuthenticated opens the Signal Login details screen`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.SignalLoginDetailsAuthenticated)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.NavigateToSignalLoginDetails)
  }

  @Test
  fun `DeleteAccountClicked asks for the screen lock first`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.DeleteAccountClicked)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.AuthenticateToDeleteAccount)
  }

  @Test
  fun `DeleteAccountAuthenticated opens the delete account screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.DeleteAccountAuthenticated)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.NavigateToDeleteAccount)
  }

  /** The service's view of [methods], which is what the view model actually has to map. */
  private fun methods(vararg methods: TwoFactorMethod) = RequestResult.Success(
    methods.map { method ->
      ServiceTwoFactorMethod(
        id = method.id,
        kind = when (method.kind) {
          TwoFactorMethod.Kind.AUTHENTICATOR_APP -> MfaKeyKind.TOTP
          TwoFactorMethod.Kind.PASSKEY -> MfaKeyKind.WEB_AUTHN
          TwoFactorMethod.Kind.OTHER -> MfaKeyKind.UNKNOWN
        },
        name = method.name,
        createdAt = method.createdAt?.let { Instant.ofEpochMilli(it) }
      )
    }
  )

  private fun createViewModel(): AccountSettingsViewModel = AccountSettingsViewModel(repository, arePasskeysSupported = true, twoFactorMethodService = twoFactorMethodService)

  private fun TestScope.collectActions(actions: Flow<AccountSettingsAction>): List<AccountSettingsAction> {
    val collected = mutableListOf<AccountSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
