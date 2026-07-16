/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.setup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.signal.devicetransfer.TransferStatus
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRoute

/**
 * Exercises the event-reducer half of the setup VM (TransferStatus → step mapping, the
 * setting-up / waiting timeout jobs, and the screen-event reducer + parent navigation). The OS
 * gates (permissions / location services / wifi / Wi-Fi Direct) are driven by concrete Android
 * APIs against `context`; that side is covered by manual QA rather than unit tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeviceTransferSetupViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private lateinit var context: Context
  private lateinit var networkController: NetworkController
  private lateinit var setupEvents: MutableSharedFlow<TransferStatus>
  private lateinit var emittedEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<DeviceTransferSetupState>
  private lateinit var stateEmitter: (DeviceTransferSetupState) -> Unit
  private lateinit var viewModel: DeviceTransferSetupViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    context = ApplicationProvider.getApplicationContext()
    networkController = mockk(relaxed = true)
    setupEvents = MutableSharedFlow(extraBufferCapacity = 16)
    emittedEvents = mutableListOf()
    parentEventEmitter = { emittedEvents.add(it) }
    emittedStates = mutableListOf()
    stateEmitter = { emittedStates.add(it) }
    viewModel = DeviceTransferSetupViewModel(context, networkController, setupEvents, MutableStateFlow(RegistrationFlowState()), parentEventEmitter)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // region TransferStatus → step mapping

  @Test
  fun `Ready event moves to SETTING_UP`() = runTest {
    setupEvents.tryEmit(TransferStatus.ready())
    testDispatcher.scheduler.runCurrent()

    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.SETTING_UP)
  }

  @Test
  fun `StartingUp event moves to SETTING_UP`() = runTest {
    setupEvents.tryEmit(TransferStatus.startingUp())
    testDispatcher.scheduler.runCurrent()

    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.SETTING_UP)
  }

  @Test
  fun `Discovery event moves to WAITING and clears takingTooLong`() = runTest {
    setupEvents.tryEmit(TransferStatus.discovery())
    testDispatcher.scheduler.runCurrent()

    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.WAITING)
    assertThat(viewModel.state.value.takingTooLong).isFalse()
  }

  @Test
  fun `VerificationRequired event moves to VERIFY with SAS code`() = runTest {
    setupEvents.tryEmit(TransferStatus.verificationRequired(1234567))
    testDispatcher.scheduler.runCurrent()

    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.VERIFY)
    assertThat(viewModel.state.value.authenticationCode).isEqualTo(1234567)
  }

  @Test
  fun `ServiceConnected event moves to CONNECTED and navigates to progress screen`() = runTest {
    setupEvents.tryEmit(TransferStatus.serviceConnected())
    testDispatcher.scheduler.runCurrent()

    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.CONNECTED)
    assertThat(emittedEvents).contains(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.DeviceTransferProgress))
  }

  @Test
  fun `Failed event moves to ERROR and shows error dialog`() = runTest {
    setupEvents.tryEmit(TransferStatus.failed())
    testDispatcher.scheduler.runCurrent()

    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.ERROR)
    assertThat(viewModel.state.value.showErrorDialog).isTrue()
  }

  @Test
  fun `Shutdown event moves to ERROR and shows error dialog`() = runTest {
    setupEvents.tryEmit(TransferStatus.shutdown())
    testDispatcher.scheduler.runCurrent()

    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.ERROR)
    assertThat(viewModel.state.value.showErrorDialog).isTrue()
  }

  @Test
  fun `Unavailable event is ignored`() = runTest {
    val before = viewModel.state.value.step
    setupEvents.tryEmit(TransferStatus.unavailable())
    testDispatcher.scheduler.runCurrent()

    assertThat(viewModel.state.value.step).isEqualTo(before)
  }

  @Test
  fun `NetworkConnected event is ignored`() = runTest {
    val before = viewModel.state.value.step
    setupEvents.tryEmit(TransferStatus.networkConnected())
    testDispatcher.scheduler.runCurrent()

    assertThat(viewModel.state.value.step).isEqualTo(before)
  }

  // endregion

  // region Timeout jobs

  @Test
  fun `SETTING_UP that lingers flips takingTooLong after the prepare timeout`() = runTest {
    setupEvents.tryEmit(TransferStatus.ready())
    testDispatcher.scheduler.runCurrent()
    assertThat(viewModel.state.value.takingTooLong).isFalse()

    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.SETTING_UP)
    assertThat(viewModel.state.value.takingTooLong).isTrue()
  }

  @Test
  fun `WAITING that lingers moves to TROUBLESHOOTING after the waiting timeout`() = runTest {
    setupEvents.tryEmit(TransferStatus.discovery())
    testDispatcher.scheduler.runCurrent()
    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.WAITING)

    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.TROUBLESHOOTING)
  }

  @Test
  fun `VerificationRequired cancels the waiting timeout`() = runTest {
    setupEvents.tryEmit(TransferStatus.discovery())
    testDispatcher.scheduler.runCurrent()
    setupEvents.tryEmit(TransferStatus.verificationRequired(7654321))
    testDispatcher.scheduler.runCurrent()

    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.VERIFY)
  }

  @Test
  fun `events are ignored after a waiting-timeout shutdown until a retry`() = runTest {
    setupEvents.tryEmit(TransferStatus.discovery())
    testDispatcher.scheduler.runCurrent()
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.TROUBLESHOOTING)

    setupEvents.tryEmit(TransferStatus.serviceConnected())
    testDispatcher.scheduler.runCurrent()

    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.TROUBLESHOOTING)
    assertThat(emittedEvents).doesNotContain(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.DeviceTransferProgress))
  }

  // endregion

  // region Screen-event reducer

  @Test
  fun `PermissionsDenied moves to PERMISSIONS_DENIED`() = runTest {
    viewModel.applyEvent(
      DeviceTransferSetupState(step = SetupStep.PERMISSIONS_CHECK),
      DeviceTransferSetupScreenEvents.PermissionsDenied,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates.last().step).isEqualTo(SetupStep.PERMISSIONS_DENIED)
  }

  @Test
  fun `RequestPermissionClicked requests the location permission`() = runTest {
    viewModel.applyEvent(
      DeviceTransferSetupState(),
      DeviceTransferSetupScreenEvents.RequestPermissionClicked,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates.last().pendingActions.requestLocationPermission).isTrue()
  }

  @Test
  fun `OpenLocationSettingsClicked sets the open-location pending action`() = runTest {
    viewModel.applyEvent(
      DeviceTransferSetupState(),
      DeviceTransferSetupScreenEvents.OpenLocationSettingsClicked,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates.last().pendingActions.openLocationSettings).isTrue()
  }

  @Test
  fun `OpenWifiSettingsClicked sets the open-wifi pending action`() = runTest {
    viewModel.applyEvent(
      DeviceTransferSetupState(),
      DeviceTransferSetupScreenEvents.OpenWifiSettingsClicked,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates.last().pendingActions.openWifiSettings).isTrue()
  }

  @Test
  fun `OpenAppSettingsClicked sets the open-app-settings pending action`() = runTest {
    viewModel.applyEvent(
      DeviceTransferSetupState(),
      DeviceTransferSetupScreenEvents.OpenAppSettingsClicked,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates.last().pendingActions.openAppSettings).isTrue()
  }

  @Test
  fun `UserVerifiedCode moves to WAITING_FOR_OTHER_TO_VERIFY`() = runTest {
    viewModel.applyEvent(
      DeviceTransferSetupState(step = SetupStep.VERIFY, authenticationCode = 12345),
      DeviceTransferSetupScreenEvents.UserVerifiedCode,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates.last().step).isEqualTo(SetupStep.WAITING_FOR_OTHER_TO_VERIFY)
  }

  @Test
  fun `UserRejectedCode shows the reject-confirmation dialog`() = runTest {
    viewModel.applyEvent(
      DeviceTransferSetupState(step = SetupStep.VERIFY),
      DeviceTransferSetupScreenEvents.UserRejectedCode,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates.last().showVerifyRejectDialog).isTrue()
  }

  @Test
  fun `VerifyRejectConfirmed hides the dialog and navigates back`() = runTest {
    viewModel.applyEvent(
      DeviceTransferSetupState(step = SetupStep.VERIFY, showVerifyRejectDialog = true),
      DeviceTransferSetupScreenEvents.VerifyRejectConfirmed,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates.last().showVerifyRejectDialog).isFalse()
    assertThat(emittedEvents).contains(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `VerifyRejectDismissed hides the dialog and stays put`() = runTest {
    viewModel.applyEvent(
      DeviceTransferSetupState(step = SetupStep.VERIFY, showVerifyRejectDialog = true),
      DeviceTransferSetupScreenEvents.VerifyRejectDismissed,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates.last().showVerifyRejectDialog).isFalse()
    assertThat(emittedEvents).isEmpty()
  }

  @Test
  fun `BackClicked navigates back without emitting state`() = runTest {
    viewModel.applyEvent(
      DeviceTransferSetupState(step = SetupStep.WAITING),
      DeviceTransferSetupScreenEvents.BackClicked,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedEvents).containsExactly(RegistrationFlowEvent.NavigateBack)
    assertThat(emittedStates).isEmpty()
  }

  @Test
  fun `OpenWifiSettingsHandled clears only the open-wifi pending action`() = runTest {
    viewModel.applyEvent(
      DeviceTransferSetupState(pendingActions = DeviceTransferSetupState.PendingActions(openWifiSettings = true, requestLocationPermission = true)),
      DeviceTransferSetupScreenEvents.OpenWifiSettingsHandled,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates.last().pendingActions).isEqualTo(DeviceTransferSetupState.PendingActions(requestLocationPermission = true))
  }

  // endregion
}
