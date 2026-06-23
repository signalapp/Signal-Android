/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.progress

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
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
  private lateinit var viewModel: DeviceTransferProgressViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    context = ApplicationProvider.getApplicationContext()
    progressEvents = MutableSharedFlow(extraBufferCapacity = 16)
    emittedEvents = mutableListOf()
    parentEventEmitter = { emittedEvents.add(it) }
    viewModel = DeviceTransferProgressViewModel(context, progressEvents, parentEventEmitter)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `InProgress updates message count`() = runTest {
    progressEvents.tryEmit(NewDeviceRestoreStatus(42, NewDeviceRestoreStatus.State.IN_PROGRESS))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.messageCount).isEqualTo(42L)
    assertThat(viewModel.state.value.status).isEqualTo(DeviceTransferProgressState.Status.RECEIVING)
  }

  @Test
  fun `RestoreComplete navigates to Complete screen`() = runTest {
    progressEvents.tryEmit(NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.RESTORE_COMPLETE))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(emittedEvents).contains(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.DeviceTransferComplete))
  }

  @Test
  fun `VersionDowngrade failure sets FAILED with correct reason`() = runTest {
    progressEvents.tryEmit(NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.FAILURE_VERSION_DOWNGRADE))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.status).isEqualTo(DeviceTransferProgressState.Status.FAILED)
    assertThat(viewModel.state.value.errorReason).isEqualTo(DeviceTransferProgressState.ErrorReason.VERSION_DOWNGRADE)
  }
}
