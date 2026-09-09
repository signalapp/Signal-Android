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
import org.signal.appsettings.account.TwoFactorMethod
import org.signal.appsettings.totp.TotpApp
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsViewModelTest {

  companion object {
    private const val CORRECT_PIN = "1234"
    private const val INCORRECT_PIN = "9999"

    private val TOTP_APP = TwoFactorMethod(id = 1, kind = TwoFactorMethod.Kind.AUTHENTICATOR_APP, name = "Bitwarden Authenticator", createdAt = 0)
    private val OTHER_TOTP_APP = TwoFactorMethod(id = 2, kind = TwoFactorMethod.Kind.AUTHENTICATOR_APP, name = "Twilio Authy", createdAt = 0)
    private val PASSKEY = TwoFactorMethod(id = 1, kind = TwoFactorMethod.Kind.PASSKEY, name = "Pixel Phone", createdAt = 0)
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val repository = mockk<AccountSettingsRepository>(relaxUnitFun = true)

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
    every { repository.getMaxTotpApps() } returns 2
    coEvery { repository.getTwoFactorMethods() } returns AccountSettingsRepository.TwoFactorMethodsResult.Success(emptyList())
    coEvery { repository.removeTotpApp(any()) } returns true
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
    coEvery { repository.getTwoFactorMethods() } returns methods(TOTP_APP, PASSKEY)

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.isPhoneNumberless).isTrue()
    assertThat(viewModel.state.value.signalLogin!!.twoFactorMethods).containsExactly(TOTP_APP, PASSKEY)
    assertThat(viewModel.state.value.signalLogin?.loadState).isEqualTo(LoadState.LOADED)
    assertThat(viewModel.state.value.signalLogin?.maxTotpApps).isEqualTo(2)
  }

  /** An empty list says nothing on its own, so the screen leans on the load state to know we haven't heard back yet. */
  @Test
  fun `the two-factor list is LOADING until we've heard back about the account`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { repository.getTwoFactorMethods() } coAnswers { awaitCancellation() }

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.signalLogin?.loadState).isEqualTo(LoadState.LOADING)
    assertThat(viewModel.state.value.signalLogin!!.twoFactorMethods).isEmpty()
  }

  /** An account we couldn't ask about is not an account with no second factors. */
  @Test
  fun `a service we couldn't reach clears the two-factor list and says so`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { repository.getTwoFactorMethods() } returns AccountSettingsRepository.TwoFactorMethodsResult.NetworkFailure

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.signalLogin?.loadState).isEqualTo(LoadState.NETWORK_FAILURE)
    assertThat(viewModel.state.value.signalLogin!!.twoFactorMethods).isEmpty()
  }

  @Test
  fun `ScreenResumed picks up second factors added elsewhere`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true

    val viewModel = createViewModel()

    coEvery { repository.getTwoFactorMethods() } returns methods(TOTP_APP)
    viewModel.onEvent(AccountSettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.signalLogin!!.twoFactorMethods).containsExactly(TOTP_APP)
  }

  @Test
  fun `AddTotpAppClicked opens setup when there's room for another app`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { repository.getTwoFactorMethods() } returns methods(TOTP_APP)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.AddTotpAppClicked)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.NavigateToTotpSetup)
  }

  /** Passkeys share the list but not the limit, so they can't be what stops another app from being added. */
  @Test
  fun `AddTotpAppClicked explains the limit when there's no room for another app`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { repository.getTwoFactorMethods() } returns methods(TOTP_APP, OTHER_TOTP_APP, PASSKEY)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.AddTotpAppClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.MaxTotpAppsReached)
    assertThat(actions).isEmpty()
  }

  @Test
  fun `RenameMethodClicked opens the naming screen for that app`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { repository.getTwoFactorMethods() } returns methods(TOTP_APP)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.RenameMethodClicked(TOTP_APP))

    val expected = TotpApp(id = TOTP_APP.id, name = TOTP_APP.name, createdAt = TOTP_APP.createdAt)
    assertThat(actions.last()).isEqualTo(AccountSettingsAction.NavigateToRenameTotpApp(expected))
  }

  /** Ids only mean anything within a kind, so a passkey sharing an id with an app must not be mistaken for it. */
  @Test
  fun `RenameMethodClicked for an unsupported passkey does nothing`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { repository.getTwoFactorMethods() } returns methods(TOTP_APP, PASSKEY)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.RenameMethodClicked(PASSKEY))

    assertThat(actions).isEmpty()
  }

  /** Removing a second factor is guarded by the screen lock, so nothing happens until the user gets past it. */
  @Test
  fun `RemoveMethodClicked asks for the screen lock first`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { repository.getTwoFactorMethods() } returns methods(TOTP_APP)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.RemoveMethodClicked(TOTP_APP))

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.AuthenticateToRemoveMethod(TOTP_APP))
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `MethodRemovalAuthenticated asks the user to confirm before removing`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { repository.getTwoFactorMethods() } returns methods(TOTP_APP)

    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.MethodRemovalAuthenticated(TOTP_APP))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmRemoveTotpApp(TOTP_APP.id))
  }

  @Test
  fun `AuthenticationFailed says so and removes nothing`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { repository.getTwoFactorMethods() } returns methods(TOTP_APP)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.RemoveMethodClicked(TOTP_APP))
    viewModel.onEvent(AccountSettingsEvent.AuthenticationFailed)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.ShowAuthenticationFailed)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    coVerify(exactly = 0) { repository.removeTotpApp(any()) }
  }

  @Test
  fun `MethodRemovalAuthenticated for an unsupported passkey does nothing`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { repository.getTwoFactorMethods() } returns methods(PASSKEY)

    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.MethodRemovalAuthenticated(PASSKEY))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `RemoveTotpAppConfirmed removes the app and says so`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { repository.getTwoFactorMethods() } returns methods(TOTP_APP)

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.MethodRemovalAuthenticated(TOTP_APP))
    viewModel.onEvent(AccountSettingsEvent.RemoveTotpAppConfirmed)

    coVerify { repository.removeTotpApp(TOTP_APP.id) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions.last()).isEqualTo(AccountSettingsAction.ShowTotpAppRemoved)
  }

  /** The open dialog is what says which app is being removed, so a confirmation without one has no app to act on. */
  @Test
  fun `RemoveTotpAppConfirmed without the confirmation dialog removes nothing`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { repository.getTwoFactorMethods() } returns methods(TOTP_APP)

    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.RemoveTotpAppConfirmed)

    coVerify(exactly = 0) { repository.removeTotpApp(any()) }
    assertThat(viewModel.state.value.signalLogin!!.twoFactorMethods).containsExactly(TOTP_APP)
  }

  /** The list is what tells the user the app is gone, so it has to be read again rather than assumed. */
  @Test
  fun `a removal re-reads the list`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { repository.getTwoFactorMethods() } returns methods(TOTP_APP)

    val viewModel = createViewModel()

    viewModel.onEvent(AccountSettingsEvent.MethodRemovalAuthenticated(TOTP_APP))

    coEvery { repository.getTwoFactorMethods() } returns methods()
    viewModel.onEvent(AccountSettingsEvent.RemoveTotpAppConfirmed)

    assertThat(viewModel.state.value.signalLogin!!.twoFactorMethods).isEmpty()
  }

  @Test
  fun `a removal that didn't go through says so rather than pretending the app is gone`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true
    coEvery { repository.getTwoFactorMethods() } returns methods(TOTP_APP)
    coEvery { repository.removeTotpApp(any()) } returns false

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.MethodRemovalAuthenticated(TOTP_APP))
    viewModel.onEvent(AccountSettingsEvent.RemoveTotpAppConfirmed)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.ShowTotpAppRemovalFailed)
    assertThat(viewModel.state.value.signalLogin!!.twoFactorMethods).containsExactly(TOTP_APP)
  }

  @Test
  fun `LearnMoreClicked opens the support article`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberless() } returns true

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.LearnMoreClicked)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.OpenLearnMore)
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

  private fun methods(vararg methods: TwoFactorMethod) = AccountSettingsRepository.TwoFactorMethodsResult.Success(methods.toList())

  private fun createViewModel(): AccountSettingsViewModel = AccountSettingsViewModel(repository)

  private fun TestScope.collectActions(actions: Flow<AccountSettingsAction>): List<AccountSettingsAction> {
    val collected = mutableListOf<AccountSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
