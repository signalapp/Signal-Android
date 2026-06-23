/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.instructions

import assertk.assertThat
import assertk.assertions.containsExactly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRoute

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceTransferInstructionsViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var viewModel: DeviceTransferInstructionsViewModel
  private lateinit var emittedEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<DeviceTransferInstructionsState>
  private lateinit var stateEmitter: (DeviceTransferInstructionsState) -> Unit

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    emittedEvents = mutableListOf()
    parentEventEmitter = { emittedEvents.add(it) }
    emittedStates = mutableListOf()
    stateEmitter = { emittedStates.add(it) }
    viewModel = DeviceTransferInstructionsViewModel(parentEventEmitter)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `ContinueClicked navigates to Setup`() = runTest {
    viewModel.applyEvent(
      DeviceTransferInstructionsState(),
      DeviceTransferInstructionsScreenEvents.ContinueClicked,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedEvents).containsExactly(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.DeviceTransferSetup))
  }

  @Test
  fun `BackClicked navigates back`() = runTest {
    viewModel.applyEvent(
      DeviceTransferInstructionsState(),
      DeviceTransferInstructionsScreenEvents.BackClicked,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedEvents).containsExactly(RegistrationFlowEvent.NavigateBack)
  }
}
