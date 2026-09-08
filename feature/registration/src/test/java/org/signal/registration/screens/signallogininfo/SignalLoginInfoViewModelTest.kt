/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogininfo

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.signal.passwordmanager.CredentialManagerError
import org.signal.passwordmanager.CredentialManagerResult
import org.signal.passwordmanager.UsernamePasswordCredential
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SignalLoginInfoViewModelTest {

  companion object {
    private val ACI_VALUE = ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91"))
    private const val ACCOUNT_ID = "A6B28482-2E32-83D0-7F23-91360A4C2B91"
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var mockRepository: RegistrationRepository
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var aep: AccountEntropyPool
  private lateinit var viewModel: SignalLoginInfoViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
    parentEventEmitter = {}
    aep = AccountEntropyPool.generate()
    viewModel = SignalLoginInfoViewModel(
      repository = mockRepository,
      parentState = MutableStateFlow(RegistrationFlowState(aci = ACI_VALUE, accountEntropyPool = aep)),
      parentEventEmitter = parentEventEmitter,
      isPasswordManagerAvailable = true
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `ParentStateChanged populates the credentials from the parent state`() = runTest(testDispatcher) {
    val aci = ACI.from(UUID.randomUUID())
    val aep = AccountEntropyPool.generate()
    var emittedState: SignalLoginInfoState? = null

    viewModel.applyEvent(
      SignalLoginInfoState(),
      SignalLoginInfoScreenEvents.ParentStateChanged(RegistrationFlowState(aci = aci, accountEntropyPool = aep)),
      {}
    ) { emittedState = it }

    assertThat(emittedState?.aci).isEqualTo(aci)
    assertThat(emittedState?.aep).isEqualTo(aep)
  }

  @Test
  fun `ViewDetailsClicked advances to the view details screen`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()

    viewModel.applyEvent(SignalLoginInfoState(), SignalLoginInfoScreenEvents.ViewDetailsClicked, { parentEvents.add(it) }) {}

    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.SignalLoginViewDetails))
  }

  @Test
  fun `SaveToPasswordManagerClicked hands the login to the password manager`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginInfoScreenActions>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginInfoScreenEvents.SaveToPasswordManagerClicked)

    assertThat(actions).containsExactly(SignalLoginInfoScreenActions.SaveToPasswordManager(accountId = ACCOUNT_ID, recoveryKey = aep.displayValue))
    assertThat(viewModel.state.value.showSpinner).isEqualTo(true)
  }

  @Test
  fun `SaveToPasswordManagerClicked without a login in the flow state shows the unknown error dialog`() = runTest(testDispatcher) {
    var emittedState: SignalLoginInfoState? = null

    viewModel.applyEvent(SignalLoginInfoState(), SignalLoginInfoScreenEvents.SaveToPasswordManagerClicked, {}) { emittedState = it }

    assertThat(emittedState?.dialogs?.unknownError).isEqualTo(true)
    assertThat(emittedState?.showSpinner).isEqualTo(false)
  }

  @Test
  fun `a successful save raises the confirm-you-saved-it sheet`() = runTest(testDispatcher) {
    var emittedState: SignalLoginInfoState? = null

    viewModel.applyEvent(
      SignalLoginInfoState(showSpinner = true),
      SignalLoginInfoScreenEvents.SaveToPasswordManagerCompleted(CredentialManagerResult.Success),
      {}
    ) { emittedState = it }

    assertThat(emittedState?.showConfirmSavedSheet).isEqualTo(true)
    assertThat(emittedState?.showSpinner).isEqualTo(false)
  }

  @Test
  fun `a canceled save leaves the user where they are`() = runTest(testDispatcher) {
    var emittedState: SignalLoginInfoState? = null

    viewModel.applyEvent(
      SignalLoginInfoState(showSpinner = true),
      SignalLoginInfoScreenEvents.SaveToPasswordManagerCompleted(CredentialManagerResult.UserCanceled),
      {}
    ) { emittedState = it }

    assertThat(emittedState?.showConfirmSavedSheet).isEqualTo(false)
    assertThat(emittedState?.showSpinner).isEqualTo(false)
    assertThat(emittedState?.dialogs).isEqualTo(SignalLoginInfoState.Dialogs())
  }

  @Test
  fun `an interrupted save is retried once and then given up on`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginInfoScreenActions>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginInfoScreenEvents.SaveToPasswordManagerCompleted(CredentialManagerResult.Interrupted(RuntimeException())))

    assertThat(actions).containsExactly(SignalLoginInfoScreenActions.SaveToPasswordManager(accountId = ACCOUNT_ID, recoveryKey = aep.displayValue))

    viewModel.onEvent(SignalLoginInfoScreenEvents.SaveToPasswordManagerCompleted(CredentialManagerResult.Interrupted(RuntimeException())))

    assertThat(actions).containsExactly(SignalLoginInfoScreenActions.SaveToPasswordManager(accountId = ACCOUNT_ID, recoveryKey = aep.displayValue))
    assertThat(viewModel.state.value.dialogs.saveFailed).isEqualTo(true)
  }

  @Test
  fun `a save the password manager refused shows the failed-save dialog`() = runTest(testDispatcher) {
    var emittedState: SignalLoginInfoState? = null

    viewModel.applyEvent(
      SignalLoginInfoState(),
      SignalLoginInfoScreenEvents.SaveToPasswordManagerCompleted(CredentialManagerError.SavePromptDisabled(RuntimeException())),
      {}
    ) { emittedState = it }

    assertThat(emittedState?.dialogs?.saveFailed).isEqualTo(true)
  }

  @Test
  fun `a save that failed unexpectedly shows the unknown error dialog`() = runTest(testDispatcher) {
    var emittedState: SignalLoginInfoState? = null

    viewModel.applyEvent(
      SignalLoginInfoState(),
      SignalLoginInfoScreenEvents.SaveToPasswordManagerCompleted(CredentialManagerError.Unexpected(RuntimeException())),
      {}
    ) { emittedState = it }

    assertThat(emittedState?.dialogs?.unknownError).isEqualTo(true)
  }

  @Test
  fun `ConfirmSavedContinueClicked reads the login back out of the password manager`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginInfoScreenActions>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginInfoScreenEvents.ConfirmSavedContinueClicked)

    assertThat(actions).containsExactly(SignalLoginInfoScreenActions.ReadBackFromPasswordManager(accountId = ACCOUNT_ID))
    assertThat(viewModel.state.value.showConfirmSavedSheet).isEqualTo(false)
  }

  @Test
  fun `a credential that matches the login advances to the add username screen`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()

    viewModel.applyEvent(
      SignalLoginInfoState(aci = ACI_VALUE, aep = aep, showSpinner = true),
      SignalLoginInfoScreenEvents.SavedCredentialRetrieved(UsernamePasswordCredential(username = ACCOUNT_ID, password = aep.displayValue)),
      { parentEvents.add(it) }
    ) {}

    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.AddUsername))
  }

  @Test
  fun `a credential that does not match the login shows the not-confirmed dialog`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()
    var emittedState: SignalLoginInfoState? = null

    viewModel.applyEvent(
      SignalLoginInfoState(aci = ACI_VALUE, aep = aep, showSpinner = true),
      SignalLoginInfoScreenEvents.SavedCredentialRetrieved(UsernamePasswordCredential(username = ACCOUNT_ID, password = AccountEntropyPool.generate().displayValue)),
      { parentEvents.add(it) }
    ) { emittedState = it }

    assertThat(emittedState?.dialogs?.saveNotConfirmed).isEqualTo(true)
    assertThat(emittedState?.showSpinner).isEqualTo(false)
    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `no credential at all shows the not-confirmed dialog`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()
    var emittedState: SignalLoginInfoState? = null

    viewModel.applyEvent(
      SignalLoginInfoState(aci = ACI_VALUE, aep = aep, showSpinner = true),
      SignalLoginInfoScreenEvents.SavedCredentialRetrieved(null),
      { parentEvents.add(it) }
    ) { emittedState = it }

    assertThat(emittedState?.dialogs?.saveNotConfirmed).isEqualTo(true)
    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `SeeLoginInfoAgainClicked drops the sheet and shows the login again`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()
    var emittedState: SignalLoginInfoState? = null

    viewModel.applyEvent(
      SignalLoginInfoState(showConfirmSavedSheet = true),
      SignalLoginInfoScreenEvents.SeeLoginInfoAgainClicked,
      { parentEvents.add(it) }
    ) { emittedState = it }

    assertThat(emittedState?.showConfirmSavedSheet).isEqualTo(false)
    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.SignalLoginViewDetails))
  }

  @Test
  fun `ConfirmSavedSheetDismissed drops the sheet`() = runTest(testDispatcher) {
    var emittedState: SignalLoginInfoState? = null

    viewModel.applyEvent(SignalLoginInfoState(showConfirmSavedSheet = true), SignalLoginInfoScreenEvents.ConfirmSavedSheetDismissed, {}) { emittedState = it }

    assertThat(emittedState?.showConfirmSavedSheet).isEqualTo(false)
  }

  @Test
  fun `SaveManuallyClicked advances to the save your login screen`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()

    viewModel.applyEvent(SignalLoginInfoState(), SignalLoginInfoScreenEvents.SaveManuallyClicked, { parentEvents.add(it) }) {}

    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.SignalLoginViewDetailsForManualSave))
  }

  @Test
  fun `SaveNotConfirmedDialogDismissed clears the not-confirmed dialog`() = runTest(testDispatcher) {
    var emittedState: SignalLoginInfoState? = null

    viewModel.applyEvent(
      SignalLoginInfoState(dialogs = SignalLoginInfoState.Dialogs(saveNotConfirmed = true)),
      SignalLoginInfoScreenEvents.SaveNotConfirmedDialogDismissed,
      {}
    ) { emittedState = it }

    assertThat(emittedState?.dialogs?.saveNotConfirmed).isEqualTo(false)
  }
}
