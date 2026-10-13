/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginmanualsave

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
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
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRoute
import org.signal.signallogin.RecoveryKeyGroups
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SignalLoginViewDetailsForManualSaveViewModelTest {

  companion object {
    private val ACI_VALUE = ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91"))
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var viewModel: SignalLoginViewDetailsForManualSaveViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    viewModel = SignalLoginViewDetailsForManualSaveViewModel(
      parentState = MutableStateFlow(RegistrationFlowState()),
      parentEventEmitter = {}
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `ParentStateChanged maps the credentials into display form`() = runTest(testDispatcher) {
    val aep = AccountEntropyPool.generate()
    var emittedState: SignalLoginViewDetailsForManualSaveState? = null

    viewModel.applyEvent(
      SignalLoginViewDetailsForManualSaveState(),
      SignalLoginViewDetailsForManualSaveScreenEvents.ParentStateChanged(RegistrationFlowState(aci = ACI_VALUE, accountEntropyPool = aep)),
      {}
    ) { emittedState = it }

    assertThat(emittedState?.accountId).isEqualTo("A6B28482-2E32-83D0-7F23-91360A4C2B91")
    assertThat(emittedState?.recoveryKey).isEqualTo(aep.displayValue)
    assertThat(emittedState?.recoveryKeyGroups?.groups?.size).isEqualTo(AccountEntropyPool.LENGTH / RecoveryKeyGroups.GROUP_SIZE)
  }

  @Test
  fun `BackClicked navigates back`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()

    viewModel.applyEvent(SignalLoginViewDetailsForManualSaveState(), SignalLoginViewDetailsForManualSaveScreenEvents.BackClicked, { parentEvents.add(it) }) {}

    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `SaveAsPdfClicked launches the save as PDF flow`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginViewDetailsForManualSaveScreenActions>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsForManualSaveScreenEvents.SaveAsPdfClicked)

    assertThat(actions).containsExactly(SignalLoginViewDetailsForManualSaveScreenActions.LaunchSaveAsPdf)
  }

  @Test
  fun `CopyAccountIdClicked copies the account ID to the clipboard`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginViewDetailsForManualSaveScreenActions>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsForManualSaveScreenEvents.CopyAccountIdClicked("A6B28482-2E32-83D0-7F23-91360A4C2B91"))

    assertThat(actions).containsExactly(SignalLoginViewDetailsForManualSaveScreenActions.CopyTextToClipboard("A6B28482-2E32-83D0-7F23-91360A4C2B91"))
  }

  @Test
  fun `CopyRecoveryKeyClicked copies the recovery key to the clipboard`() = runTest(testDispatcher) {
    val recoveryKey = AccountEntropyPool.generate().displayValue
    val actions = mutableListOf<SignalLoginViewDetailsForManualSaveScreenActions>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsForManualSaveScreenEvents.CopyRecoveryKeyClicked(recoveryKey))

    assertThat(actions).containsExactly(SignalLoginViewDetailsForManualSaveScreenActions.CopyTextToClipboard(recoveryKey))
  }

  @Test
  fun `ContinueClicked raises the confirm-you-saved-it sheet without navigating`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()
    var emittedState: SignalLoginViewDetailsForManualSaveState? = null

    viewModel.applyEvent(SignalLoginViewDetailsForManualSaveState(), SignalLoginViewDetailsForManualSaveScreenEvents.ContinueClicked, { parentEvents.add(it) }) { emittedState = it }

    assertThat(emittedState?.showConfirmSavedSheet).isEqualTo(true)
    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `ConfirmSavedContinueClicked drops the sheet and moves on to confirming the login`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()
    var emittedState: SignalLoginViewDetailsForManualSaveState? = null

    viewModel.applyEvent(
      SignalLoginViewDetailsForManualSaveState(showConfirmSavedSheet = true),
      SignalLoginViewDetailsForManualSaveScreenEvents.ConfirmSavedContinueClicked,
      { parentEvents.add(it) }
    ) { emittedState = it }

    assertThat(emittedState?.showConfirmSavedSheet).isEqualTo(false)
    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.SignalLoginManualSaveConfirmation))
  }

  @Test
  fun `ShowLoginInfoAgainClicked drops the sheet and stays put`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()
    var emittedState: SignalLoginViewDetailsForManualSaveState? = null

    viewModel.applyEvent(
      SignalLoginViewDetailsForManualSaveState(showConfirmSavedSheet = true),
      SignalLoginViewDetailsForManualSaveScreenEvents.ShowLoginInfoAgainClicked,
      { parentEvents.add(it) }
    ) { emittedState = it }

    assertThat(emittedState?.showConfirmSavedSheet).isEqualTo(false)
    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `ConfirmSavedSheetDismissed drops the sheet`() = runTest(testDispatcher) {
    var emittedState: SignalLoginViewDetailsForManualSaveState? = null

    viewModel.applyEvent(SignalLoginViewDetailsForManualSaveState(showConfirmSavedSheet = true), SignalLoginViewDetailsForManualSaveScreenEvents.ConfirmSavedSheetDismissed, {}) { emittedState = it }

    assertThat(emittedState?.showConfirmSavedSheet).isEqualTo(false)
  }
}
