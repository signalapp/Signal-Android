/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pincreation

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RestoreDecision
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@OptIn(ExperimentalCoroutinesApi::class)
class PinCreationViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var viewModel: PinCreationViewModel
  private lateinit var mockRepository: RegistrationRepository
  private lateinit var parentState: MutableStateFlow<RegistrationFlowState>
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
    parentState = MutableStateFlow(RegistrationFlowState())
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    viewModel = PinCreationViewModel(
      repository = mockRepository,
      parentState = parentState,
      parentEventEmitter = parentEventEmitter
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun TestScope.collectStates(): List<PinCreationState> {
    val states = mutableListOf<PinCreationState>()
    backgroundScope.launch(testDispatcher) { viewModel.state.collect { states.add(it) } }
    return states
  }

  // ==================== PIN Confirmation Tests ====================

  @Test
  fun `first PinSubmitted enters confirm mode and does not back up`() = runTest(testDispatcher) {
    val states = collectStates()
    val initialState = PinCreationState(accountEntropyPool = AccountEntropyPool.generate())

    viewModel.applyEvent(initialState, PinCreationScreenEvents.PinSubmitted("123456"))

    coVerify(exactly = 0) { mockRepository.setNewlyCreatedPin(any(), any(), any<MasterKey>()) }
    assertThat(emittedParentEvents).hasSize(0)
    assertThat(states.last().isConfirmEnabled).isTrue()
    assertThat(states.last().pinMismatch).isFalse()
  }

  @Test
  fun `mismatched confirmation PIN returns to creation with error and does not back up`() = runTest(testDispatcher) {
    val states = collectStates()
    val confirmState = PinCreationState(
      accountEntropyPool = AccountEntropyPool.generate(),
      isConfirmEnabled = true,
      firstPin = "123456"
    )

    viewModel.applyEvent(confirmState, PinCreationScreenEvents.PinSubmitted("999999"))

    coVerify(exactly = 0) { mockRepository.setNewlyCreatedPin(any(), any(), any<MasterKey>()) }
    assertThat(emittedParentEvents).hasSize(0)
    assertThat(states.last().isConfirmEnabled).isFalse()
    assertThat(states.last().pinMismatch).isTrue()
    assertThat(states.last().firstPin).isNull()
  }

  @Test
  fun `BackToPinEntry returns to creation step and clears the first PIN`() = runTest(testDispatcher) {
    val states = collectStates()
    val confirmState = PinCreationState(
      accountEntropyPool = AccountEntropyPool.generate(),
      isConfirmEnabled = true,
      firstPin = "123456",
      pinMismatch = true
    )

    viewModel.applyEvent(confirmState, PinCreationScreenEvents.BackToPinEntry)

    assertThat(states.last().isConfirmEnabled).isFalse()
    assertThat(states.last().firstPin).isNull()
    assertThat(states.last().pinMismatch).isFalse()
  }

  // ==================== PinSubmitted Success Tests ====================

  @Test
  fun `matching confirmation PIN with valid AEP and successful SVR backup completes registration`() = runTest(testDispatcher) {
    val states = collectStates()
    val aep = AccountEntropyPool.generate()
    val confirmState = PinCreationState(accountEntropyPool = aep, isConfirmEnabled = true, firstPin = "123456")

    coEvery { mockRepository.setNewlyCreatedPin(any(), any(), any<MasterKey>()) } returns
      RequestResult.Success(null)

    viewModel.applyEvent(confirmState, PinCreationScreenEvents.PinSubmitted("123456"))

    coVerify { mockRepository.setRestoreDecision(RestoreDecision.NEW_ACCOUNT) }
    coVerify { mockRepository.restoreAccountRecord(any()) }
    assertThat(emittedParentEvents).contains(RegistrationFlowEvent.RegistrationComplete)
    assertThat(states.last().loading).isTrue()
  }

  // ==================== PinSubmitted Missing AEP Test ====================

  @Test
  fun `PinSubmitted with null AEP emits ResetState`() = runTest(testDispatcher) {
    val confirmState = PinCreationState(accountEntropyPool = null, isConfirmEnabled = true, firstPin = "123456")

    viewModel.applyEvent(confirmState, PinCreationScreenEvents.PinSubmitted("123456"))

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  // ==================== PinSubmitted Error Tests ====================

  @Test
  fun `PinSubmitted with NotRegistered error emits ResetState`() = runTest(testDispatcher) {
    val aep = AccountEntropyPool.generate()
    val confirmState = PinCreationState(accountEntropyPool = aep, isConfirmEnabled = true, firstPin = "123456")

    coEvery { mockRepository.setNewlyCreatedPin(any(), any(), any<MasterKey>()) } returns
      RequestResult.NonSuccess(NetworkController.BackupMasterKeyError.NotRegistered)

    viewModel.applyEvent(confirmState, PinCreationScreenEvents.PinSubmitted("123456"))

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `PinSubmitted with EnclaveNotFound error surfaces a service error and stops loading`() = runTest(testDispatcher) {
    val states = collectStates()
    val aep = AccountEntropyPool.generate()
    val confirmState = PinCreationState(accountEntropyPool = aep, isConfirmEnabled = true, firstPin = "123456")

    coEvery { mockRepository.setNewlyCreatedPin(any(), any(), any<MasterKey>()) } returns
      RequestResult.NonSuccess(NetworkController.BackupMasterKeyError.EnclaveNotFound)

    viewModel.applyEvent(confirmState, PinCreationScreenEvents.PinSubmitted("123456"))

    assertThat(emittedParentEvents).hasSize(0)
    assertThat(states.last().dialogs.serviceError).isTrue()
    assertThat(states.last().loading).isFalse()
  }

  @Test
  fun `PinSubmitted with application error surfaces a service error and stops loading`() = runTest(testDispatcher) {
    val states = collectStates()
    val aep = AccountEntropyPool.generate()
    val confirmState = PinCreationState(accountEntropyPool = aep, isConfirmEnabled = true, firstPin = "123456")

    coEvery { mockRepository.setNewlyCreatedPin(any(), any(), any<MasterKey>()) } returns
      RequestResult.ApplicationError(RuntimeException("Unexpected"))

    viewModel.applyEvent(confirmState, PinCreationScreenEvents.PinSubmitted("123456"))

    assertThat(emittedParentEvents).hasSize(0)
    assertThat(states.last().dialogs.serviceError).isTrue()
    assertThat(states.last().loading).isFalse()
  }

  @Test
  fun `PinSubmitted with retryable network error surfaces a network error with retryAfter`() = runTest(testDispatcher) {
    val states = collectStates()
    val aep = AccountEntropyPool.generate()
    val confirmState = PinCreationState(accountEntropyPool = aep, isConfirmEnabled = true, firstPin = "123456")
    val retryAfter = 30.seconds

    coEvery { mockRepository.setNewlyCreatedPin(any(), any(), any<MasterKey>()) } returns
      RequestResult.RetryableNetworkError(IOException("Network error"), retryAfter.toJavaDuration())

    viewModel.applyEvent(confirmState, PinCreationScreenEvents.PinSubmitted("123456"))

    assertThat(emittedParentEvents).hasSize(0)
    assertThat(states.last().dialogs.networkError).isEqualTo(PinCreationState.Dialogs.NetworkError(retryAfter))
    assertThat(states.last().loading).isFalse()
  }

  @Test
  fun `ServiceErrorDialogDismissed clears only the service error dialog`() = runTest(testDispatcher) {
    val states = collectStates()
    val networkError = PinCreationState.Dialogs.NetworkError(retryAfter = null)
    val stateWithEvent = PinCreationState(dialogs = PinCreationState.Dialogs(serviceError = true, networkError = networkError))

    viewModel.applyEvent(stateWithEvent, PinCreationScreenEvents.ServiceErrorDialogDismissed)

    assertThat(states.last().dialogs).isEqualTo(PinCreationState.Dialogs(networkError = networkError))
  }

  @Test
  fun `NetworkErrorDialogDismissed clears only the network error dialog`() = runTest(testDispatcher) {
    val states = collectStates()
    val stateWithEvent = PinCreationState(dialogs = PinCreationState.Dialogs(serviceError = true, networkError = PinCreationState.Dialogs.NetworkError(retryAfter = null)))

    viewModel.applyEvent(stateWithEvent, PinCreationScreenEvents.NetworkErrorDialogDismissed)

    assertThat(states.last().dialogs).isEqualTo(PinCreationState.Dialogs(serviceError = true))
  }

  // ==================== OptOut Tests ====================

  @Test
  fun `OptOut records opt-out and completes registration`() = runTest(testDispatcher) {
    val initialState = PinCreationState(accountEntropyPool = AccountEntropyPool.generate())

    viewModel.applyEvent(initialState, PinCreationScreenEvents.OptOut)

    coVerify { mockRepository.setPinOptedOut() }
    coVerify { mockRepository.setRestoreDecision(RestoreDecision.NEW_ACCOUNT) }
    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.RegistrationComplete)
  }

  // ==================== ParentStateChanged Tests ====================

  @Test
  fun `ParentStateChanged copies accountEntropyPool from parent`() = runTest(testDispatcher) {
    val states = collectStates()
    val aep = AccountEntropyPool.generate()
    val parentFlowState = RegistrationFlowState(accountEntropyPool = aep)

    viewModel.applyEvent(PinCreationState(), PinCreationScreenEvents.ParentStateChanged(parentFlowState))

    assertThat(states.last().accountEntropyPool).isEqualTo(aep)
  }

  @Test
  fun `ParentStateChanged with null accountEntropyPool keeps null`() = runTest(testDispatcher) {
    val states = collectStates()
    val parentFlowState = RegistrationFlowState(accountEntropyPool = null)

    viewModel.applyEvent(PinCreationState(), PinCreationScreenEvents.ParentStateChanged(parentFlowState))

    assertThat(states.last().accountEntropyPool).isNull()
  }

  @Test
  fun `parent state changes are merged into state through the event stream`() = runTest(testDispatcher) {
    val states = collectStates()
    val aep = AccountEntropyPool.generate()

    parentState.value = RegistrationFlowState(accountEntropyPool = aep)

    assertThat(states.last().accountEntropyPool).isEqualTo(aep)
  }
}
