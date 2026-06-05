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
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.registration.PendingRestoreOption
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRoute

class ArchiveRestoreSelectionViewModelTest {

  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<ArchiveRestoreSelectionState>
  private lateinit var stateEmitter: (ArchiveRestoreSelectionState) -> Unit

  @Before
  fun setup() {
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
    isPreRegistration: Boolean = false
  ): ArchiveRestoreSelectionViewModel {
    return ArchiveRestoreSelectionViewModel(
      restoreOptions = restoreOptions,
      isPreRegistration = isPreRegistration,
      parentEventEmitter = parentEventEmitter
    )
  }

  // ==================== RestoreOptionSelected Tests ====================

  @Test
  fun `SignalSecureBackup pre-registration emits PendingRestoreOptionSelected and navigates to PhoneNumberEntry`() = runTest {
    val viewModel = createViewModel(isPreRegistration = true)
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
    val viewModel = createViewModel(isPreRegistration = false)
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
    val viewModel = createViewModel(isPreRegistration = true)
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
    val viewModel = createViewModel(isPreRegistration = false)
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
  fun `DeviceTransfer is not implemented and emits no events`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = ArchiveRestoreSelectionState()

    viewModel.applyEvent(
      initialState,
      ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected(ArchiveRestoreOption.DeviceTransfer),
      stateEmitter
    )

    assertThat(emittedParentEvents).hasSize(0)
  }

  @Test
  fun `None option sets showSkipWarningDialog to true`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = ArchiveRestoreSelectionState()

    viewModel.applyEvent(
      initialState,
      ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected(ArchiveRestoreOption.None),
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().showSkipWarningDialog).isTrue()
  }

  // ==================== Skip Tests ====================

  @Test
  fun `Skip sets showSkipWarningDialog to true`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = ArchiveRestoreSelectionState()

    viewModel.applyEvent(initialState, ArchiveRestoreSelectionScreenEvents.Skip, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().showSkipWarningDialog).isTrue()
  }

  // ==================== ConfirmSkip Tests ====================

  @Test
  fun `ConfirmSkip navigates to PinCreate and clears dialog`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = ArchiveRestoreSelectionState(showSkipWarningDialog = true)

    viewModel.applyEvent(initialState, ArchiveRestoreSelectionScreenEvents.ConfirmSkip, stateEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.PinCreate)
    assertThat(emittedStates.last().showSkipWarningDialog).isFalse()
  }

  // ==================== DismissSkipWarning Tests ====================

  @Test
  fun `DismissSkipWarning sets showSkipWarningDialog to false`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
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

  @Test
  fun `showSkipButton is false when None is in options`() = runTest {
    val viewModel = createViewModel(
      restoreOptions = listOf(ArchiveRestoreOption.SignalSecureBackup, ArchiveRestoreOption.None)
    )

    assertThat(viewModel.state.value.showSkipButton).isFalse()
  }

  @Test
  fun `showSkipButton is true when None is not in options`() = runTest {
    val viewModel = createViewModel(
      restoreOptions = listOf(ArchiveRestoreOption.SignalSecureBackup, ArchiveRestoreOption.LocalBackup)
    )

    assertThat(viewModel.state.value.showSkipButton).isTrue()
  }
}
