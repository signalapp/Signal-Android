/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogindetails

import assertk.assertThat
import assertk.assertions.containsExactly
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
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreenEvents
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SignalLoginViewDetailsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var viewModel: SignalLoginViewDetailsViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    viewModel = SignalLoginViewDetailsViewModel(
      parentState = MutableStateFlow(RegistrationFlowState()),
      parentEventEmitter = {}
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state maps the parent credentials into display form`() {
    val aci = ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91"))
    val aep = AccountEntropyPool.generate()

    val viewModel = SignalLoginViewDetailsViewModel(
      parentState = MutableStateFlow(RegistrationFlowState(aci = aci, accountEntropyPool = aep)),
      parentEventEmitter = {}
    )

    assertThat(viewModel.state.value.accountKey).isEqualTo("A6B28482-2E32-83D0-7F23-91360A4C2B91")
    assertThat(viewModel.state.value.recoveryKey).isEqualTo(aep.displayValue)
  }

  @Test
  fun `BackClicked navigates back`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()

    viewModel.applyEvent(SignalLoginViewDetailsScreenEvents.BackClicked) { parentEvents.add(it) }

    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `SaveToPasswordManagerClicked launches the save to password manager flow`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginViewDetailsScreenActions>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked)

    assertThat(actions).containsExactly(SignalLoginViewDetailsScreenActions.LaunchSaveToPasswordManager)
  }

  @Test
  fun `SaveAsPdfClicked launches the save as PDF flow`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginViewDetailsScreenActions>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked)

    assertThat(actions).containsExactly(SignalLoginViewDetailsScreenActions.LaunchSaveAsPdf)
  }

  @Test
  fun `AccountIdLongClicked copies the account key to the clipboard`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginViewDetailsScreenActions>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.AccountIdLongClicked("A6B28482-2E32-83D0-7F23-91360A4C2B91"))

    assertThat(actions).containsExactly(SignalLoginViewDetailsScreenActions.CopyTextToClipboard("A6B28482-2E32-83D0-7F23-91360A4C2B91"))
  }

  @Test
  fun `RecoveryKeyLongClicked copies the recovery key to the clipboard`() = runTest(testDispatcher) {
    val recoveryKey = AccountEntropyPool.generate().displayValue
    val actions = mutableListOf<SignalLoginViewDetailsScreenActions>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.RecoveryKeyLongClicked(recoveryKey))

    assertThat(actions).containsExactly(SignalLoginViewDetailsScreenActions.CopyTextToClipboard(recoveryKey))
  }
}
