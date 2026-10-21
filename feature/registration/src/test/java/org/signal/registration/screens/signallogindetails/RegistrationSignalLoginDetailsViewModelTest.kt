/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogindetails

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
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
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsState
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationSignalLoginDetailsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var viewModel: RegistrationSignalLoginDetailsViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    viewModel = RegistrationSignalLoginDetailsViewModel(
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

    val viewModel = RegistrationSignalLoginDetailsViewModel(
      parentState = MutableStateFlow(RegistrationFlowState(aci = aci, accountEntropyPool = aep)),
      parentEventEmitter = {}
    )

    assertThat(viewModel.state.value.accountKey).isEqualTo("A6B28482-2E32-83D0-7F23-91360A4C2B91")
    assertThat(viewModel.state.value.recoveryKey).isEqualTo(aep.displayValue)
    assertThat(viewModel.state.value.showSaveToPasswordManagerButton).isFalse()
  }

  @Test
  fun `BackClicked navigates back`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()

    viewModel.applyEvent(SignalLoginViewDetailsScreenEvents.BackClicked) { parentEvents.add(it) }

    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `SaveToPasswordManagerClicked produces no action`() = runTest(testDispatcher) {
    val actions = mutableListOf<RegistrationSignalLoginDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked)

    assertThat(actions).isEmpty()
  }

  @Test
  fun `SaveAsPdfClicked raises the do-not-share warning before saving`() = runTest(testDispatcher) {
    val actions = mutableListOf<RegistrationSignalLoginDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked)

    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.doNotShareWarningAction).isEqualTo(SignalLoginViewDetailsState.DoNotShareWarningAction.SAVE_AS_PDF)
  }

  @Test
  fun `acknowledging the warning after SaveAsPdfClicked launches the save as PDF flow`() = runTest(testDispatcher) {
    val actions = mutableListOf<RegistrationSignalLoginDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked)
    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.DoNotShareWarningGotItClicked)

    assertThat(actions).containsExactly(RegistrationSignalLoginDetailsAction.LaunchSaveAsPdf)
    assertThat(viewModel.state.value.doNotShareWarningAction).isNull()
  }

  @Test
  fun `ResetRecoveryKeyClicked produces no action`() = runTest(testDispatcher) {
    val actions = mutableListOf<RegistrationSignalLoginDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.ResetRecoveryKeyClicked)

    assertThat(actions).isEmpty()
  }

  @Test
  fun `CopyAccountIdClicked raises the do-not-share warning before copying`() = runTest(testDispatcher) {
    val actions = mutableListOf<RegistrationSignalLoginDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.CopyAccountIdClicked)

    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.doNotShareWarningAction).isEqualTo(SignalLoginViewDetailsState.DoNotShareWarningAction.COPY_ACCOUNT_ID)
  }

  @Test
  fun `acknowledging the warning after CopyAccountIdClicked copies the account key to the clipboard`() = runTest(testDispatcher) {
    val aci = ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91"))
    val viewModel = RegistrationSignalLoginDetailsViewModel(
      parentState = MutableStateFlow(RegistrationFlowState(aci = aci, accountEntropyPool = AccountEntropyPool.generate())),
      parentEventEmitter = {}
    )
    val actions = mutableListOf<RegistrationSignalLoginDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.CopyAccountIdClicked)
    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.DoNotShareWarningGotItClicked)

    assertThat(actions).containsExactly(RegistrationSignalLoginDetailsAction.CopyTextToClipboard("A6B28482-2E32-83D0-7F23-91360A4C2B91"))
    assertThat(viewModel.state.value.doNotShareWarningAction).isNull()
  }

  @Test
  fun `acknowledging the warning after CopyRecoveryKeyClicked copies the recovery key to the clipboard`() = runTest(testDispatcher) {
    val aep = AccountEntropyPool.generate()
    val viewModel = RegistrationSignalLoginDetailsViewModel(
      parentState = MutableStateFlow(RegistrationFlowState(aci = ACI.from(UUID.randomUUID()), accountEntropyPool = aep)),
      parentEventEmitter = {}
    )
    val actions = mutableListOf<RegistrationSignalLoginDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.CopyRecoveryKeyClicked)
    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.DoNotShareWarningGotItClicked)

    assertThat(actions).containsExactly(RegistrationSignalLoginDetailsAction.CopyTextToClipboard(aep.displayValue))
    assertThat(viewModel.state.value.doNotShareWarningAction).isNull()
  }

  @Test
  fun `DoNotShareWarningLearnMoreClicked drops the warning and opens the help article without copying`() = runTest(testDispatcher) {
    val actions = mutableListOf<RegistrationSignalLoginDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.CopyRecoveryKeyClicked)
    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.DoNotShareWarningLearnMoreClicked)

    assertThat(actions).containsExactly(RegistrationSignalLoginDetailsAction.OpenDoNotShareLearnMoreArticle)
    assertThat(viewModel.state.value.doNotShareWarningAction).isNull()
  }

  @Test
  fun `DoNotShareWarningDismissed drops the warning without copying`() = runTest(testDispatcher) {
    val actions = mutableListOf<RegistrationSignalLoginDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.CopyRecoveryKeyClicked)
    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.DoNotShareWarningDismissed)

    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.doNotShareWarningAction).isNull()
  }
}
