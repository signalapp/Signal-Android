/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.quickrestore

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.libsignal.net.RequestResult
import org.signal.registration.KeyMaterial
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.fakes.FakeNetworkController

@OptIn(ExperimentalCoroutinesApi::class)
class QuickRestoreQrViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var mockRepository: RegistrationRepository
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<QuickRestoreQrState>
  private lateinit var stateEmitter: (QuickRestoreQrState) -> Unit

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
    every { mockRepository.startProvisioning() } returns emptyFlow()
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(): QuickRestoreQrViewModel {
    return QuickRestoreQrViewModel(
      repository = mockRepository,
      parentEventEmitter = parentEventEmitter
    )
  }

  // ==================== Cancel Tests ====================

  @Test
  fun `Cancel emits NavigateBack`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val initialState = QuickRestoreQrState()

    viewModel.applyEvent(initialState, QuickRestoreQrEvents.Cancel, stateEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  // ==================== RetryQrCode Tests ====================

  @Test
  fun `RetryQrCode resets qrState to Loading and clears errors`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val initialState = QuickRestoreQrState(
      qrState = QrState.Failed,
      showRegistrationError = true,
      errorMessage = "some error"
    )

    viewModel.applyEvent(initialState, QuickRestoreQrEvents.RetryQrCode, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().qrState).isEqualTo(QrState.Loading)
    assertThat(emittedStates.last().showRegistrationError).isFalse()
    assertThat(emittedStates.last().errorMessage).isNull()
  }

  // ==================== DismissError Tests ====================

  @Test
  fun `DismissError clears registration error`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val initialState = QuickRestoreQrState(
      showRegistrationError = true,
      errorMessage = "rate limited"
    )

    viewModel.applyEvent(initialState, QuickRestoreQrEvents.DismissError, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().showRegistrationError).isFalse()
    assertThat(emittedStates.last().errorMessage).isNull()
  }

  // ==================== Initial State Tests ====================

  @Test
  fun `initial state has qrState Loading`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.qrState).isEqualTo(QrState.Loading)
    assertThat(viewModel.state.value.isRegistering).isFalse()
    assertThat(viewModel.state.value.showRegistrationError).isFalse()
  }

  // ==================== Provisioning Flow Tests ====================

  @Test
  fun `QrCodeReady provisioning event updates state to Loaded`() = runTest(testDispatcher) {
    val flow = MutableSharedFlow<NetworkController.ProvisioningEvent>(replay = 1)
    every { mockRepository.startProvisioning() } returns flow

    val viewModel = createViewModel()
    flow.emit(NetworkController.ProvisioningEvent.QrCodeReady("sgnl://example"))

    assertThat(viewModel.state.value.qrState).isInstanceOf<QrState.Loaded>()
  }

  @Test
  fun `Error provisioning event updates state to Failed`() = runTest(testDispatcher) {
    val flow = MutableSharedFlow<NetworkController.ProvisioningEvent>(replay = 1)
    every { mockRepository.startProvisioning() } returns flow

    val viewModel = createViewModel()
    flow.emit(NetworkController.ProvisioningEvent.Error(RuntimeException("boom")))

    assertThat(viewModel.state.value.qrState).isEqualTo(QrState.Failed)
  }

  @Test
  fun `MessageReceived with RegistrationLock retries with the reglock token derived from the provisioned AEP`() = runTest(testDispatcher) {
    val aep = AccountEntropyPool.generate()
    val message = FakeNetworkController().provisioningMessage(aep = aep, e164 = "+15551234567")
    val keyMaterial = mockk<KeyMaterial>(relaxed = true) {
      every { accountEntropyPool } returns aep
    }
    val response = mockk<NetworkController.RegisterAccountResponse>(relaxed = true)
    val flow = MutableSharedFlow<NetworkController.ProvisioningEvent>(replay = 1)
    every { mockRepository.startProvisioning() } returns flow

    coEvery { mockRepository.registerAccountWithProvisioningData(any(), provideRegistrationLock = false) } returns
      RequestResult.NonSuccess(
        NetworkController.RegisterAccountError.RegistrationLock(registrationLockResponse())
      )
    coEvery { mockRepository.registerAccountWithProvisioningData(any(), provideRegistrationLock = true) } returns
      RequestResult.Success(response to keyMaterial)

    createViewModel()
    flow.emit(NetworkController.ProvisioningEvent.MessageReceived(message))

    coVerify { mockRepository.registerAccountWithProvisioningData(message, provideRegistrationLock = true) }
    assertThat(emittedParentEvents).hasSize(4)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.RestoreMethodTokenReceived>()
    assertThat(emittedParentEvents[1]).isEqualTo(RegistrationFlowEvent.E164Chosen("+15551234567"))
    assertThat(emittedParentEvents[2]).isInstanceOf<RegistrationFlowEvent.Registered>()
    assertThat(emittedParentEvents[3])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.ArchiveRestoreSelection>()
  }

  @Test
  fun `MessageReceived with RegistrationLock when already providing the reglock token navigates to PinEntryForRegistrationLock`() = runTest(testDispatcher) {
    val message = FakeNetworkController().provisioningMessage(aep = AccountEntropyPool.generate(), e164 = "+15551234567")
    val flow = MutableSharedFlow<NetworkController.ProvisioningEvent>(replay = 1)
    every { mockRepository.startProvisioning() } returns flow

    coEvery { mockRepository.registerAccountWithProvisioningData(any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.RegisterAccountError.RegistrationLock(registrationLockResponse())
      )

    createViewModel()
    flow.emit(NetworkController.ProvisioningEvent.MessageReceived(message))

    coVerify { mockRepository.registerAccountWithProvisioningData(message, provideRegistrationLock = true) }
    assertThat(emittedParentEvents).hasSize(3)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.RestoreMethodTokenReceived>()
    assertThat(emittedParentEvents[1]).isEqualTo(RegistrationFlowEvent.E164Chosen("+15551234567"))
    assertThat(emittedParentEvents[2])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinEntryForRegistrationLock>()
  }

  private fun registrationLockResponse(): NetworkController.RegistrationLockResponse {
    return NetworkController.RegistrationLockResponse(
      timeRemaining = 86400000L,
      svr2Credentials = NetworkController.SvrCredentials(username = "test-username", password = "test-password")
    )
  }
}
