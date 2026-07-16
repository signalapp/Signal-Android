/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restoreselection

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.registration.PendingRestoreOption
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.RestoreDecision

class ArchiveRestoreSelectionViewModelTest {

  private lateinit var mockRepository: RegistrationRepository
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<ArchiveRestoreSelectionState>
  private lateinit var stateEmitter: (ArchiveRestoreSelectionState) -> Unit

  @Before
  fun setup() {
    mockRepository = mockk(relaxed = true)
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
  }

  private fun createViewModel(
    restoreOptions: List<ArchiveRestoreOption> = listOf(
      ArchiveRestoreOption.SignalSecureBackup,
      ArchiveRestoreOption.LocalBackup,
      ArchiveRestoreOption.DeviceTransfer
    ),
    registeredState: RegisteredState = RegisteredState.RegisteredAndPinUnknown
  ): ArchiveRestoreSelectionViewModel {
    return ArchiveRestoreSelectionViewModel(
      restoreOptions = restoreOptions,
      registeredState = registeredState,
      repository = mockRepository,
      parentState = MutableStateFlow(RegistrationFlowState()),
      parentEventEmitter = parentEventEmitter
    )
  }

  // ==================== RestoreOptionSelected Tests ====================

  @Test
  fun `SignalSecureBackup pre-registration emits PendingRestoreOptionSelected and navigates to PhoneNumberEntry`() = runTest {
    val viewModel = createViewModel(registeredState = RegisteredState.NotRegistered)
    val initialState = ArchiveRestoreSelectionState()

    viewModel.applyEvent(
      initialState,
      ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected(ArchiveRestoreOption.SignalSecureBackup),
      stateEmitter
    )

    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[0])
      .isInstanceOf<RegistrationFlowEvent.PendingRestoreOptionSelected>()
      .prop(RegistrationFlowEvent.PendingRestoreOptionSelected::option)
      .isEqualTo(PendingRestoreOption.RemoteBackup)
    assertThat(emittedParentEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.PhoneNumberEntry)
  }

  @Test
  fun `SignalSecureBackup post-registration navigates to EnterAepForRemoteBackupPostRegistration`() = runTest {
    val viewModel = createViewModel(registeredState = RegisteredState.RegisteredAndPinUnknown)
    val initialState = ArchiveRestoreSelectionState()

    viewModel.applyEvent(
      initialState,
      ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected(ArchiveRestoreOption.SignalSecureBackup),
      stateEmitter
    )

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.EnterAepForRemoteBackupPostRegistration)
  }

  @Test
  fun `LocalBackup pre-registration emits PendingRestoreOptionSelected and navigates to PhoneNumberEntry`() = runTest {
    val viewModel = createViewModel(registeredState = RegisteredState.NotRegistered)
    val initialState = ArchiveRestoreSelectionState()

    viewModel.applyEvent(
      initialState,
      ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected(ArchiveRestoreOption.LocalBackup),
      stateEmitter
    )

    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[0])
      .isInstanceOf<RegistrationFlowEvent.PendingRestoreOptionSelected>()
      .prop(RegistrationFlowEvent.PendingRestoreOptionSelected::option)
      .isEqualTo(PendingRestoreOption.LocalBackup)
    assertThat(emittedParentEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.PhoneNumberEntry)
  }

  @Test
  fun `LocalBackup post-registration navigates to LocalBackupRestore`() = runTest {
    val viewModel = createViewModel(registeredState = RegisteredState.RegisteredAndPinUnknown)
    val initialState = ArchiveRestoreSelectionState()

    viewModel.applyEvent(
      initialState,
      ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected(ArchiveRestoreOption.LocalBackup),
      stateEmitter
    )

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.LocalBackupRestore(isPreRegistration = false))
  }

  @Test
  fun `DeviceTransfer navigates to DeviceTransferInstructions`() = runTest {
    val viewModel = createViewModel(registeredState = RegisteredState.RegisteredAndPinUnknown)
    val initialState = ArchiveRestoreSelectionState()

    viewModel.applyEvent(
      initialState,
      ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected(ArchiveRestoreOption.DeviceTransfer),
      stateEmitter
    )

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.DeviceTransferInstructions)
  }

  @Test
  fun `None option sets showSkipWarningDialog to true`() = runTest {
    val viewModel = createViewModel(registeredState = RegisteredState.RegisteredAndPinUnknown)
    val initialState = ArchiveRestoreSelectionState()

    viewModel.applyEvent(
      initialState,
      ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected(ArchiveRestoreOption.None),
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().showSkipWarningDialog).isTrue()
  }

  // ==================== ConfirmSkip Tests ====================

  @Test
  fun `ConfirmSkip pre-registration navigates to PhoneNumberEntry and clears dialog without recording a skip`() = runTest {
    val viewModel = createViewModel(registeredState = RegisteredState.NotRegistered)
    val initialState = ArchiveRestoreSelectionState(showSkipWarningDialog = true)

    viewModel.applyEvent(initialState, ArchiveRestoreSelectionScreenEvents.ConfirmSkip, stateEmitter)

    coVerify(exactly = 0) { mockRepository.setRestoreDecision(any()) }
    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.PhoneNumberEntry)
    assertThat(emittedStates.last().showSkipWarningDialog).isFalse()
  }

  @Test
  fun `ConfirmSkip post-registration when not storage capable navigates to PinCreate and clears dialog`() = runTest {
    val viewModel = createViewModel(registeredState = RegisteredState.RegisteredAndPinUnknown)
    val initialState = ArchiveRestoreSelectionState(showSkipWarningDialog = true, storageCapable = false)

    viewModel.applyEvent(initialState, ArchiveRestoreSelectionScreenEvents.ConfirmSkip, stateEmitter)

    coVerify { mockRepository.setRestoreDecision(RestoreDecision.SKIPPED) }
    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.PinCreate)
    assertThat(emittedStates.last().showSkipWarningDialog).isFalse()
  }

  @Test
  fun `ConfirmSkip post-registration when storage capable navigates to PinEntryForSvrRestore and clears dialog`() = runTest {
    val viewModel = createViewModel(registeredState = RegisteredState.RegisteredAndPinUnknown)
    val initialState = ArchiveRestoreSelectionState(showSkipWarningDialog = true, storageCapable = true)

    viewModel.applyEvent(initialState, ArchiveRestoreSelectionScreenEvents.ConfirmSkip, stateEmitter)

    coVerify { mockRepository.setRestoreDecision(RestoreDecision.SKIPPED) }
    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.PinEntryForSvrRestore)
    assertThat(emittedStates.last().showSkipWarningDialog).isFalse()
  }

  @Test
  fun `ConfirmSkip post-registration when PIN is known records skip and completes registration`() = runTest {
    val viewModel = createViewModel(registeredState = RegisteredState.RegisteredAndPinKnown)
    val initialState = ArchiveRestoreSelectionState(showSkipWarningDialog = true)

    viewModel.applyEvent(initialState, ArchiveRestoreSelectionScreenEvents.ConfirmSkip, stateEmitter)

    coVerify { mockRepository.setRestoreDecision(RestoreDecision.SKIPPED) }
    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.RegistrationComplete)
    assertThat(emittedStates.last().showSkipWarningDialog).isFalse()
  }

  // ==================== DismissSkipWarning Tests ====================

  @Test
  fun `DismissSkipWarning sets showSkipWarningDialog to false`() = runTest {
    val viewModel = createViewModel(registeredState = RegisteredState.RegisteredAndPinUnknown)
    val initialState = ArchiveRestoreSelectionState(showSkipWarningDialog = true)

    viewModel.applyEvent(
      initialState,
      ArchiveRestoreSelectionScreenEvents.DismissSkipWarning,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().showSkipWarningDialog).isFalse()
  }

  // ==================== Initial State Tests ====================

  @Test
  fun `initial state contains provided restore options`() = runTest {
    val options = listOf(ArchiveRestoreOption.SignalSecureBackup, ArchiveRestoreOption.None)
    val viewModel = createViewModel(restoreOptions = options)

    assertThat(viewModel.state.value.restoreOptions).isEqualTo(options)
  }

  // ==================== ParentStateChanged Tests ====================

  @Test
  fun `ParentStateChanged copies storageCapable from parent`() = runTest {
    val viewModel = createViewModel()

    viewModel.applyEvent(ArchiveRestoreSelectionState(), ArchiveRestoreSelectionScreenEvents.ParentStateChanged(RegistrationFlowState(storageCapable = true)), stateEmitter)

    assertThat(emittedStates.last().storageCapable).isTrue()
  }
}
