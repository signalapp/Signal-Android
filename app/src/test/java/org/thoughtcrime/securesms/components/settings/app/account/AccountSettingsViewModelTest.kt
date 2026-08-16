/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import assertk.assertThat
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
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsViewModelTest {

  companion object {
    private const val CORRECT_PIN = "1234"
    private const val INCORRECT_PIN = "9999"
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
    every { repository.isPhoneNumberlessRegistrationEnabled() } returns false
    every { repository.hasAuthenticatorApp() } returns false
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
  fun `the Signal Login section is left out when phone-numberless registration is off`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.signalLogin).isNull()
  }

  @Test
  fun `the Signal Login section is filled in when phone-numberless registration is on`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberlessRegistrationEnabled() } returns true
    every { repository.hasAuthenticatorApp() } returns true

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.signalLogin?.hasAuthenticatorApp).isEqualTo(true)
  }

  @Test
  fun `AuthenticatorAppClicked opens the authenticator setup flow`() = runTest(testDispatcher) {
    every { repository.isPhoneNumberlessRegistrationEnabled() } returns true

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AccountSettingsEvent.AuthenticatorAppClicked)

    assertThat(actions.last()).isEqualTo(AccountSettingsAction.NavigateToAuthenticatorAppSetup)
  }

  private fun createViewModel(): AccountSettingsViewModel = AccountSettingsViewModel(repository)

  private fun TestScope.collectActions(actions: Flow<AccountSettingsAction>): List<AccountSettingsAction> {
    val collected = mutableListOf<AccountSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
