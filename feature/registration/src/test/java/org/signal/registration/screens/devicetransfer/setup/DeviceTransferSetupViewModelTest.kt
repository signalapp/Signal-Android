/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.setup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
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
 * Exercises the event-reducer half of the setup VM (TransferStatus → step mapping + parent
 * navigation). The OS gates (permissions / location services / wifi / Wi-Fi Direct) are driven
 * by concrete Android APIs against `context`; that side is covered by manual QA rather than unit
 * tests.
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

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    context = ApplicationProvider.getApplicationContext()
    networkController = mockk(relaxed = true)
    setupEvents = MutableSharedFlow(extraBufferCapacity = 16)
    emittedEvents = mutableListOf()
    parentEventEmitter = { emittedEvents.add(it) }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `VerificationRequired event moves to VERIFY with SAS code`() = runTest {
    val viewModel = DeviceTransferSetupViewModel(context, networkController, setupEvents, MutableStateFlow(RegistrationFlowState()), parentEventEmitter)
    testDispatcher.scheduler.advanceUntilIdle()

    setupEvents.tryEmit(TransferStatus.verificationRequired(1234567))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.VERIFY)
    assertThat(viewModel.state.value.authenticationCode).isEqualTo(1234567)
  }

  @Test
  fun `ServiceConnected event navigates to progress screen`() = runTest {
    val viewModel = DeviceTransferSetupViewModel(context, networkController, setupEvents, MutableStateFlow(RegistrationFlowState()), parentEventEmitter)
    testDispatcher.scheduler.advanceUntilIdle()

    setupEvents.tryEmit(TransferStatus.serviceConnected())
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.CONNECTED)
    assertThat(emittedEvents).contains(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.DeviceTransferProgress))
  }

  @Test
  fun `Failed event moves to ERROR`() = runTest {
    val viewModel = DeviceTransferSetupViewModel(context, networkController, setupEvents, MutableStateFlow(RegistrationFlowState()), parentEventEmitter)
    testDispatcher.scheduler.advanceUntilIdle()

    setupEvents.tryEmit(TransferStatus.failed())
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.step).isEqualTo(SetupStep.ERROR)
    assertThat(viewModel.state.value.showErrorDialog).isEqualTo(true)
  }
}
