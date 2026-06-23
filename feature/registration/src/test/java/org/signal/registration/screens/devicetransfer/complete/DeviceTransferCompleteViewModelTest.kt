/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.complete

import io.mockk.coVerify
import io.mockk.mockk
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
import org.signal.registration.RegistrationRepository

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceTransferCompleteViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var viewModel: DeviceTransferCompleteViewModel
  private lateinit var mockRepository: RegistrationRepository
  private lateinit var emittedEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<DeviceTransferCompleteState>
  private lateinit var stateEmitter: (DeviceTransferCompleteState) -> Unit

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
    emittedEvents = mutableListOf()
    parentEventEmitter = { emittedEvents.add(it) }
    emittedStates = mutableListOf()
    stateEmitter = { emittedStates.add(it) }
    viewModel = DeviceTransferCompleteViewModel(mockRepository, parentEventEmitter)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `ContinueClicked hands off to finishRegistrationOrCreateProfile`() = runTest {
    viewModel.applyEvent(
      DeviceTransferCompleteState(),
      DeviceTransferCompleteScreenEvents.ContinueClicked,
      parentEventEmitter,
      mockRepository,
      stateEmitter
    )

    coVerify { mockRepository.finishRegistrationOrCreateProfile(parentEventEmitter, any()) }
  }
}
