/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pincreation

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.coEvery
import io.mockk.mockk
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
import org.signal.core.models.MasterKey
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository

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

  // ==================== PinSubmitted Success Tests ====================

  @Test
  fun `PinSubmitted with valid AEP and successful SVR backup emits RegistrationComplete`() = runTest(testDispatcher) {
    val aep = AccountEntropyPool.generate()
    val initialState = PinCreationState(accountEntropyPool = aep)

    coEvery { mockRepository.setNewlyCreatedPin(any(), any(), any<MasterKey>()) } returns
      RequestResult.Success(null)

    viewModel.applyEvent(initialState, PinCreationScreenEvents.PinSubmitted("123456"))

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.RegistrationComplete)
  }

  // ==================== PinSubmitted Missing AEP Test ====================

  @Test
  fun `PinSubmitted with null AEP emits ResetState`() = runTest(testDispatcher) {
    val initialState = PinCreationState(accountEntropyPool = null)

    viewModel.applyEvent(initialState, PinCreationScreenEvents.PinSubmitted("123456"))

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  // ==================== PinSubmitted Error Tests ====================

  @Test
  fun `PinSubmitted with NotRegistered error emits ResetState`() = runTest(testDispatcher) {
    val aep = AccountEntropyPool.generate()
    val initialState = PinCreationState(accountEntropyPool = aep)

    coEvery { mockRepository.setNewlyCreatedPin(any(), any(), any<MasterKey>()) } returns
      RequestResult.NonSuccess(NetworkController.BackupMasterKeyError.NotRegistered)

    viewModel.applyEvent(initialState, PinCreationScreenEvents.PinSubmitted("123456"))

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  // ==================== applyParentState Tests ====================

  @Test
  fun `applyParentState copies accountEntropyPool from parent`() {
    val aep = AccountEntropyPool.generate()
    val parentFlowState = RegistrationFlowState(accountEntropyPool = aep)
    val initialState = PinCreationState()

    val result = viewModel.applyParentState(initialState, parentFlowState)

    assertThat(result.accountEntropyPool).isEqualTo(aep)
  }

  @Test
  fun `applyParentState with null accountEntropyPool keeps null`() {
    val parentFlowState = RegistrationFlowState(accountEntropyPool = null)
    val initialState = PinCreationState()

    val result = viewModel.applyParentState(initialState, parentFlowState)

    assertThat(result.accountEntropyPool).isNull()
  }
}
