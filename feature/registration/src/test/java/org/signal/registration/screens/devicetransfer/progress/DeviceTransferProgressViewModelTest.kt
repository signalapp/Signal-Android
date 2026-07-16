/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.progress

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.signal.devicetransfer.NewDeviceRestoreStatus
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRoute

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeviceTransferProgressViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private lateinit var context: Context
  private lateinit var progressEvents: MutableSharedFlow<NewDeviceRestoreStatus>
  private lateinit var emittedEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<DeviceTransferProgressState>
  private lateinit var stateEmitter: (DeviceTransferProgressState) -> Unit
  private lateinit var viewModel: DeviceTransferProgressViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    context = ApplicationProvider.getApplicationContext()
    progressEvents = MutableSharedFlow(extraBufferCapacity = 16)
    emittedEvents = mutableListOf()
    parentEventEmitter = { emittedEvents.add(it) }
    emittedStates = mutableListOf()
    stateEmitter = { emittedStates.add(it) }
    viewModel = DeviceTransferProgressViewModel(context, progressEvents, parentEventEmitter)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // region Progress (EventBus → state) mapping

  @Test
  fun `default state is receiving with no error`() = runTest {
    assertThat(viewModel.state.value.status).isEqualTo(DeviceTransferProgressState.Status.RECEIVING)
    assertThat(viewModel.state.value.messageCount).isEqualTo(0L)
    assertThat(viewModel.state.value.errorReason).isNull()
    assertThat(viewModel.showCancelDialog.value).isFalse()
  }

  @Test
  fun `InProgress updates message count and keeps receiving`() = runTest {
    progressEvents.tryEmit(NewDeviceRestoreStatus(42, NewDeviceRestoreStatus.State.IN_PROGRESS))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.messageCount).isEqualTo(42L)
    assertThat(viewModel.state.value.status).isEqualTo(DeviceTransferProgressState.Status.RECEIVING)
  }

  @Test
  fun `successive InProgress events advance the message count`() = runTest {
    progressEvents.tryEmit(NewDeviceRestoreStatus(10, NewDeviceRestoreStatus.State.IN_PROGRESS))
    testDispatcher.scheduler.advanceUntilIdle()
    progressEvents.tryEmit(NewDeviceRestoreStatus(99, NewDeviceRestoreStatus.State.IN_PROGRESS))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.messageCount).isEqualTo(99L)
  }

  @Test
  fun `TransferComplete moves to importing and retains message count`() = runTest {
    progressEvents.tryEmit(NewDeviceRestoreStatus(7, NewDeviceRestoreStatus.State.TRANSFER_COMPLETE))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.status).isEqualTo(DeviceTransferProgressState.Status.IMPORTING)
    assertThat(viewModel.state.value.messageCount).isEqualTo(7L)
  }

  @Test
  fun `RestoreComplete moves to finalizing and navigates to Complete screen`() = runTest {
    progressEvents.tryEmit(NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.RESTORE_COMPLETE))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.status).isEqualTo(DeviceTransferProgressState.Status.FINALIZING)
    assertThat(emittedEvents).contains(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.DeviceTransferComplete))
  }

  @Test
  fun `VersionDowngrade failure sets FAILED with correct reason`() = runTest {
    progressEvents.tryEmit(NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.FAILURE_VERSION_DOWNGRADE))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.status).isEqualTo(DeviceTransferProgressState.Status.FAILED)
    assertThat(viewModel.state.value.errorReason).isEqualTo(DeviceTransferProgressState.ErrorReason.VERSION_DOWNGRADE)
  }

  @Test
  fun `ForeignKey failure sets FAILED with correct reason`() = runTest {
    progressEvents.tryEmit(NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.FAILURE_FOREIGN_KEY))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.status).isEqualTo(DeviceTransferProgressState.Status.FAILED)
    assertThat(viewModel.state.value.errorReason).isEqualTo(DeviceTransferProgressState.ErrorReason.FOREIGN_KEY)
  }

  @Test
  fun `Unknown failure sets FAILED with correct reason`() = runTest {
    progressEvents.tryEmit(NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.FAILURE_UNKNOWN))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.status).isEqualTo(DeviceTransferProgressState.Status.FAILED)
    assertThat(viewModel.state.value.errorReason).isEqualTo(DeviceTransferProgressState.ErrorReason.UNKNOWN)
  }

  @Test
  fun `a failure does not navigate away from the progress screen`() = runTest {
    progressEvents.tryEmit(NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.FAILURE_UNKNOWN))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(emittedEvents).isEmpty()
  }

  // endregion

  // region Reducer (screen events)

  @Test
  fun `CancelClicked shows the cancel dialog`() = runTest {
    viewModel.applyEvent(
      DeviceTransferProgressState(),
      DeviceTransferProgressScreenEvents.CancelClicked,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(viewModel.showCancelDialog.value).isTrue()
    assertThat(emittedEvents).isEmpty()
  }

  @Test
  fun `CancelDismissed hides the cancel dialog and stays on screen`() = runTest {
    viewModel.applyEvent(
      DeviceTransferProgressState(),
      DeviceTransferProgressScreenEvents.CancelClicked,
      parentEventEmitter,
      stateEmitter
    )
    viewModel.applyEvent(
      DeviceTransferProgressState(),
      DeviceTransferProgressScreenEvents.CancelDismissed,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(viewModel.showCancelDialog.value).isFalse()
    assertThat(emittedEvents).isEmpty()
  }

  @Test
  fun `CancelConfirmed hides the dialog and navigates back`() = runTest {
    viewModel.applyEvent(
      DeviceTransferProgressState(),
      DeviceTransferProgressScreenEvents.CancelClicked,
      parentEventEmitter,
      stateEmitter
    )
    viewModel.applyEvent(
      DeviceTransferProgressState(),
      DeviceTransferProgressScreenEvents.CancelConfirmed,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(viewModel.showCancelDialog.value).isFalse()
    assertThat(emittedEvents).containsExactly(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `TryAgainClicked navigates back to the instructions screen`() = runTest {
    viewModel.applyEvent(
      DeviceTransferProgressState(status = DeviceTransferProgressState.Status.FAILED),
      DeviceTransferProgressScreenEvents.TryAgainClicked,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedEvents).containsExactly(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.DeviceTransferInstructions))
  }

  @Test
  fun `CancelConfirmed through the real event channel navigates back`() = runTest {
    viewModel.onEvent(DeviceTransferProgressScreenEvents.CancelConfirmed)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(emittedEvents).containsExactly(RegistrationFlowEvent.NavigateBack)
  }

  // endregion
}
