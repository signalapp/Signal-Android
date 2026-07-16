/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.linkaccount

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.libsignal.net.RequestResult
import org.signal.registration.LinkAndSyncWaitResult
import org.signal.registration.LinkedDeviceResult
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.quickrestore.QrState

@OptIn(ExperimentalCoroutinesApi::class)
class LinkAccountViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var mockRepository: RegistrationRepository
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<LinkAccountScreenState>
  private lateinit var stateEmitter: (LinkAccountScreenState) -> Unit

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
    every { mockRepository.startLinkDeviceProvisioning() } returns emptyFlow()
    coEvery { mockRepository.isCleanStart() } returns true
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun TestScope.createViewModel(parentState: RegistrationFlowState = RegistrationFlowState()): LinkAccountViewModel {
    val viewModel = LinkAccountViewModel(
      repository = mockRepository,
      parentState = MutableStateFlow(parentState),
      parentEventEmitter = parentEventEmitter
    )
    // Keep the WhileSubscribed state flow hot so state.value reflects updates during the test.
    backgroundScope.launch { viewModel.state.collect {} }
    return viewModel
  }

  @Test
  fun `initial state has qrState Loading`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.qrCodeState).isEqualTo(QrState.Loading)
    assertThat(viewModel.state.value.isRegistering).isFalse()
    assertThat(viewModel.state.value.showError).isFalse()
  }

  @Test
  fun `QrCodeReady provisioning event updates state to Loaded`() = runTest(testDispatcher) {
    val flow = givenProvisioningFlow()

    val viewModel = createViewModel()
    flow.emit(NetworkController.LinkDeviceProvisioningEvent.QrCodeReady("sgnl://linkdevice"))

    assertThat(viewModel.state.value.qrCodeState).isInstanceOf<QrState.Loaded>()
  }

  @Test
  fun `Error provisioning event updates state to Failed`() = runTest(testDispatcher) {
    val flow = givenProvisioningFlow()

    val viewModel = createViewModel()
    flow.emit(NetworkController.LinkDeviceProvisioningEvent.Error(RuntimeException("boom")))

    assertThat(viewModel.state.value.qrCodeState).isEqualTo(QrState.Failed)
  }

  @Test
  fun `successful registration without link-and-sync navigates to FullyComplete`() = runTest(testDispatcher) {
    val flow = givenProvisioningFlow()
    val message = mockk<NetworkController.LinkDeviceProvisioningMessage>(relaxed = true)
    coEvery { mockRepository.registerAsLinkedDevice(message, any()) } returns RequestResult.Success(LinkedDeviceResult(hasLinkAndSyncBackup = false))

    val viewModel = createViewModel()
    flow.emit(NetworkController.LinkDeviceProvisioningEvent.MessageReceived(message))

    assertThat(emittedParentEvents).contains(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.FullyComplete))
  }

  @Test
  fun `successful registration with link-and-sync navigates to MessageSync`() = runTest(testDispatcher) {
    val flow = givenProvisioningFlow()
    val message = mockk<NetworkController.LinkDeviceProvisioningMessage>(relaxed = true)
    coEvery { mockRepository.registerAsLinkedDevice(message, any()) } returns RequestResult.Success(LinkedDeviceResult(hasLinkAndSyncBackup = true))
    coEvery { mockRepository.awaitLinkAndSyncArchive() } returns LinkAndSyncWaitResult.ArchiveAvailable(cdn = 3, key = "archive-key")

    val viewModel = createViewModel()
    flow.emit(NetworkController.LinkDeviceProvisioningEvent.MessageReceived(message))

    assertThat(emittedParentEvents).contains(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.MessageSync))
  }

  @Test
  fun `link-and-sync offered on a relink over existing data skips import and restores from storage service`() = runTest(testDispatcher) {
    val flow = givenProvisioningFlow()
    val message = mockk<NetworkController.LinkDeviceProvisioningMessage>(relaxed = true)
    coEvery { mockRepository.isCleanStart() } returns false
    coEvery { mockRepository.registerAsLinkedDevice(message, any()) } returns RequestResult.Success(LinkedDeviceResult(hasLinkAndSyncBackup = true))

    val viewModel = createViewModel()
    flow.emit(NetworkController.LinkDeviceProvisioningEvent.MessageReceived(message))

    coVerify(exactly = 0) { mockRepository.awaitLinkAndSyncArchive() }
    coVerify { mockRepository.restoreLinkedDeviceFromStorageService() }
    assertThat(emittedParentEvents).contains(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.FullyComplete))
  }

  @Test
  fun `link-and-sync offered but archive never arrives navigates to FullyComplete`() = runTest(testDispatcher) {
    val flow = givenProvisioningFlow()
    val message = mockk<NetworkController.LinkDeviceProvisioningMessage>(relaxed = true)
    coEvery { mockRepository.registerAsLinkedDevice(message, any()) } returns RequestResult.Success(LinkedDeviceResult(hasLinkAndSyncBackup = true))
    coEvery { mockRepository.awaitLinkAndSyncArchive() } returns LinkAndSyncWaitResult.ContinueWithoutBackup

    val viewModel = createViewModel()
    flow.emit(NetworkController.LinkDeviceProvisioningEvent.MessageReceived(message))

    assertThat(emittedParentEvents).contains(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.FullyComplete))
  }

  @Test
  fun `link-and-sync relink requested wipes local data and restarts`() = runTest(testDispatcher) {
    val flow = givenProvisioningFlow()
    val message = mockk<NetworkController.LinkDeviceProvisioningMessage>(relaxed = true)
    coEvery { mockRepository.registerAsLinkedDevice(message, any()) } returns RequestResult.Success(LinkedDeviceResult(hasLinkAndSyncBackup = true))
    coEvery { mockRepository.awaitLinkAndSyncArchive() } returns LinkAndSyncWaitResult.RelinkRequired

    val viewModel = createViewModel()
    flow.emit(NetworkController.LinkDeviceProvisioningEvent.MessageReceived(message))

    coVerify { mockRepository.clearLocalDataAndRestart() }
  }

  @Test
  fun `failed registration shows error`() = runTest(testDispatcher) {
    val flow = givenProvisioningFlow()
    val message = mockk<NetworkController.LinkDeviceProvisioningMessage>(relaxed = true)
    coEvery { mockRepository.registerAsLinkedDevice(message, any()) } returns RequestResult.NonSuccess(NetworkController.RegisterAsLinkedDeviceError.MaxLinkedDevices)

    val viewModel = createViewModel()
    flow.emit(NetworkController.LinkDeviceProvisioningEvent.MessageReceived(message))

    assertThat(viewModel.state.value.showError).isTrue()
    assertThat(viewModel.state.value.isRegistering).isFalse()
  }

  @Test
  fun `applyEvent DisplayOverlayClick shows the QR overlay`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.applyEvent(LinkAccountScreenState(), LinkAccountScreenEvent.DisplayOverlayClick, stateEmitter)

    assertThat(emittedStates.last().displayQrOverlay).isTrue()
  }

  @Test
  fun `applyEvent HideOverlayClick hides the QR overlay`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.applyEvent(LinkAccountScreenState(displayQrOverlay = true), LinkAccountScreenEvent.HideOverlayClick, stateEmitter)

    assertThat(emittedStates.last().displayQrOverlay).isFalse()
  }

  @Test
  fun `applyEvent RetryQrCode resets to Loading and clears error`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val errored = LinkAccountScreenState(qrCodeState = QrState.Failed, showError = true)

    viewModel.applyEvent(errored, LinkAccountScreenEvent.RetryQrCode, stateEmitter)

    assertThat(emittedStates.last().qrCodeState).isEqualTo(QrState.Loading)
    assertThat(emittedStates.last().showError).isFalse()
  }

  @Test
  fun `applyEvent DismissError resets to Loading and clears error`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val errored = LinkAccountScreenState(qrCodeState = QrState.Failed, showError = true)

    viewModel.applyEvent(errored, LinkAccountScreenEvent.DismissError, stateEmitter)

    assertThat(emittedStates.last().qrCodeState).isEqualTo(QrState.Loading)
    assertThat(emittedStates.last().showError).isFalse()
  }

  @Test
  fun `applyEvent CreateAccountClick from link-device-first flow routes through Permissions`() = runTest(testDispatcher) {
    val viewModel = createViewModel(
      RegistrationFlowState(backStack = listOf(RegistrationRoute.Welcome, RegistrationRoute.LinkAccount()))
    )

    viewModel.applyEvent(LinkAccountScreenState(), LinkAccountScreenEvent.CreateAccountClick, stateEmitter)

    assertThat(emittedParentEvents).contains(
      RegistrationFlowEvent.NavigateToScreen(
        route = RegistrationRoute.Permissions(nextRoute = RegistrationRoute.PhoneNumberEntry),
        popCurrent = true
      )
    )
  }

  @Test
  fun `applyEvent CreateAccountClick from phone number screen returns to it without Permissions`() = runTest(testDispatcher) {
    val viewModel = createViewModel(
      RegistrationFlowState(
        backStack = listOf(
          RegistrationRoute.Welcome,
          RegistrationRoute.Permissions(nextRoute = RegistrationRoute.PhoneNumberEntry),
          RegistrationRoute.PhoneNumberEntry,
          RegistrationRoute.LinkAccount()
        )
      )
    )

    viewModel.applyEvent(LinkAccountScreenState(), LinkAccountScreenEvent.CreateAccountClick, stateEmitter)

    assertThat(emittedParentEvents).contains(
      RegistrationFlowEvent.NavigateBackToScreen(RegistrationRoute.PhoneNumberEntry)
    )
  }

  @Test
  fun `provisioning message for a different account prompts to delete data without registering`() = runTest(testDispatcher) {
    val flow = givenProvisioningFlow()
    val message = mockk<NetworkController.LinkDeviceProvisioningMessage>(relaxed = true)
    coEvery { mockRepository.isProvisioningForDifferentAccount(message) } returns true

    val viewModel = createViewModel()
    flow.emit(NetworkController.LinkDeviceProvisioningEvent.MessageReceived(message))

    assertThat(viewModel.state.value.showDeleteDataDialog).isTrue()
    assertThat(viewModel.state.value.isRegistering).isFalse()
    coVerify(exactly = 0) { mockRepository.registerAsLinkedDevice(any(), any()) }
  }

  @Test
  fun `applyEvent ConfirmDeleteAndRelink wipes local data and dismisses the dialog`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.applyEvent(LinkAccountScreenState(showDeleteDataDialog = true), LinkAccountScreenEvent.ConfirmDeleteAndRelink, stateEmitter)

    coVerify { mockRepository.clearLocalDataAndRestart() }
    assertThat(emittedStates.last().showDeleteDataDialog).isFalse()
  }

  @Test
  fun `applyEvent CancelDeleteAndRelink dismisses the dialog and restarts provisioning`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.applyEvent(LinkAccountScreenState(showDeleteDataDialog = true), LinkAccountScreenEvent.CancelDeleteAndRelink, stateEmitter)

    assertThat(emittedStates.last().qrCodeState).isEqualTo(QrState.Loading)
    assertThat(emittedStates.last().showDeleteDataDialog).isFalse()
    coVerify(exactly = 0) { mockRepository.clearLocalDataAndRestart() }
  }

  private fun givenProvisioningFlow(): MutableSharedFlow<NetworkController.LinkDeviceProvisioningEvent> {
    val flow = MutableSharedFlow<NetworkController.LinkDeviceProvisioningEvent>(replay = 1)
    every { mockRepository.startLinkDeviceProvisioning() } returns flow
    return flow
  }
}
