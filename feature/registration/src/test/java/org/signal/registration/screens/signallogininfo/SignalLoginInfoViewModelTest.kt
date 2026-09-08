/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogininfo

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
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
import org.signal.core.models.ServiceId.ACI
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SignalLoginInfoViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var mockRepository: RegistrationRepository
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var viewModel: SignalLoginInfoViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
    parentEventEmitter = {}
    viewModel = SignalLoginInfoViewModel(
      repository = mockRepository,
      parentState = MutableStateFlow(RegistrationFlowState()),
      parentEventEmitter = parentEventEmitter,
      isPasswordManagerAvailable = true
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `ParentStateChanged populates the credentials from the parent state`() = runTest(testDispatcher) {
    val aci = ACI.from(UUID.randomUUID())
    val aep = AccountEntropyPool.generate()
    var emittedState: SignalLoginInfoState? = null

    viewModel.applyEvent(
      SignalLoginInfoState(),
      SignalLoginInfoScreenEvents.ParentStateChanged(RegistrationFlowState(aci = aci, accountEntropyPool = aep)),
      {}
    ) { emittedState = it }

    assertThat(emittedState?.aci).isEqualTo(aci)
    assertThat(emittedState?.aep).isEqualTo(aep)
  }

  @Test
  fun `ViewDetailsClicked advances to the view details screen`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()

    viewModel.applyEvent(SignalLoginInfoState(), SignalLoginInfoScreenEvents.ViewDetailsClicked, { parentEvents.add(it) }) {}

    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.SignalLoginViewDetails))
  }

  @Test
  fun `SaveToPasswordManagerClicked advances to the add username screen`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()

    viewModel.applyEvent(SignalLoginInfoState(), SignalLoginInfoScreenEvents.SaveToPasswordManagerClicked, { parentEvents.add(it) }) {}

    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.AddUsername))
  }

  @Test
  fun `SaveManuallyClicked advances to the save your login screen`() = runTest(testDispatcher) {
    val parentEvents = mutableListOf<RegistrationFlowEvent>()

    viewModel.applyEvent(SignalLoginInfoState(), SignalLoginInfoScreenEvents.SaveManuallyClicked, { parentEvents.add(it) }) {}

    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.SignalLoginViewDetailsForManualSave))
  }
}
