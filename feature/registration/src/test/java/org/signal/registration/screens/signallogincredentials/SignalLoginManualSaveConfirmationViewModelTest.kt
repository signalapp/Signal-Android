/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogincredentials

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId.ACI
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.aepentry.AepInput
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SignalLoginManualSaveConfirmationViewModelTest {

  companion object {
    private val ACI_VALUE = ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91"))
    private const val RAW_ACCOUNT_ID = "a6b284822e3283d07f2391360a4c2b91"
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  private val aep = AccountEntropyPool.generate()
  private val parentState = RegistrationFlowState(aci = ACI_VALUE, accountEntropyPool = aep)

  private lateinit var viewModel: SignalLoginManualSaveConfirmationViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    viewModel = SignalLoginManualSaveConfirmationViewModel(
      parentState = MutableStateFlow(parentState),
      parentEventEmitter = {}
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `the screen starts in confirm-saved mode`() {
    assertThat(viewModel.state.value.mode).isEqualTo(SignalLoginCredentialEntryState.Mode.ConfirmSaved)
  }

  @Test
  fun `NextClicked with the login the user was shown advances to the add username screen`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()

    viewModel.applyEvent(matchingState(), SignalLoginCredentialEntryScreenEvents.NextClicked, parentState, { parentEvents.add(it) }) {}

    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.AddUsername))
  }

  @Test
  fun `NextClicked with a mistyped recovery key flags both fields and stays put`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()
    var emittedState: SignalLoginCredentialEntryState? = null
    val state = matchingState().copy(recoveryKey = AepInput.from(AccountEntropyPool.generate().displayValue))

    viewModel.applyEvent(state, SignalLoginCredentialEntryScreenEvents.NextClicked, parentState, { parentEvents.add(it) }) { emittedState = it }

    assertThat(emittedState?.areCredentialsIncorrect).isEqualTo(true)
    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `NextClicked with a mistyped account ID flags both fields and stays put`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()
    var emittedState: SignalLoginCredentialEntryState? = null
    val state = matchingState().copy(accountId = "a6b284822e3283d07f2391360a4c2b92")

    viewModel.applyEvent(state, SignalLoginCredentialEntryScreenEvents.NextClicked, parentState, { parentEvents.add(it) }) { emittedState = it }

    assertThat(emittedState?.areCredentialsIncorrect).isEqualTo(true)
    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `NextClicked with no login in the flow state lets the user through rather than trapping them`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()

    viewModel.applyEvent(matchingState(), SignalLoginCredentialEntryScreenEvents.NextClicked, RegistrationFlowState(), { parentEvents.add(it) }) {}

    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.AddUsername))
  }

  @Test
  fun `ShowLoginInfoAgainClicked navigates back to the keys`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()

    viewModel.applyEvent(matchingState(), SignalLoginCredentialEntryScreenEvents.ShowLoginInfoAgainClicked, parentState, { parentEvents.add(it) }) {}

    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `BackClicked navigates back`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()

    viewModel.applyEvent(matchingState(), SignalLoginCredentialEntryScreenEvents.BackClicked, parentState, { parentEvents.add(it) }) {}

    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `a password manager credential that matches confirms straight away`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()

    viewModel.applyEvent(
      SignalLoginCredentialEntryState(mode = SignalLoginCredentialEntryState.Mode.ConfirmSaved),
      SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected(accountId = RAW_ACCOUNT_ID, recoveryKey = aep.displayValue),
      parentState,
      { parentEvents.add(it) }
    ) {}

    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.AddUsername))
  }

  @Test
  fun `AccountIdChanged clears the mismatch flag`() = runTest(testDispatcher) {
    var emittedState: SignalLoginCredentialEntryState? = null
    val state = matchingState().copy(areCredentialsIncorrect = true)

    viewModel.applyEvent(state, SignalLoginCredentialEntryScreenEvents.AccountIdChanged(RAW_ACCOUNT_ID), parentState, {}) { emittedState = it }

    assertThat(emittedState?.areCredentialsIncorrect).isEqualTo(false)
  }

  private fun matchingState(): SignalLoginCredentialEntryState {
    return SignalLoginCredentialEntryState(
      mode = SignalLoginCredentialEntryState.Mode.ConfirmSaved,
      accountId = RAW_ACCOUNT_ID,
      recoveryKey = AepInput.from(aep.displayValue)
    )
  }
}
