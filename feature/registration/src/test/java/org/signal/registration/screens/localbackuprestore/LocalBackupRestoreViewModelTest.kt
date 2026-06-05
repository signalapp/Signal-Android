/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import android.net.Uri
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import java.time.LocalDateTime

class LocalBackupRestoreViewModelTest {

  private lateinit var mockRepository: RegistrationRepository
  private lateinit var resultBus: ResultEventBus
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<LocalBackupRestoreState>
  private lateinit var stateEmitter: (LocalBackupRestoreState) -> Unit

  private val resultKey = "test-result-key"

  @Before
  fun setup() {
    mockRepository = mockk(relaxed = true)
    resultBus = ResultEventBus()
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
  }

  private fun createViewModel(isPreRegistration: Boolean): LocalBackupRestoreViewModel {
    return LocalBackupRestoreViewModel(
      repository = mockRepository,
      parentEventEmitter = parentEventEmitter,
      isPreRegistration = isPreRegistration,
      resultBus = resultBus,
      resultKey = resultKey
    )
  }

  // ==================== PickBackupFolder Tests ====================

  @Test
  fun `PickBackupFolder sets launchFolderPicker to true`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = LocalBackupRestoreState()

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.PickBackupFolder, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().launchFolderPicker).isTrue()
  }

  // ==================== BackupFolderSelected Tests ====================

  @Test
  fun `BackupFolderSelected sets restorePhase to Scanning and selectedFolderUri`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = LocalBackupRestoreState()
    val folderUri = mockk<Uri>()

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.BackupFolderSelected(folderUri), stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().restorePhase).isEqualTo(LocalBackupRestoreState.RestorePhase.Scanning)
    assertThat(emittedStates.last().selectedFolderUri).isEqualTo(folderUri)
  }

  // ==================== RestoreBackup with V1 Tests ====================

  @Test
  fun `RestoreBackup with V1 backup navigates to EnterLocalBackupV1Passphrase`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V1,
      date = LocalDateTime.now(),
      name = "backup.backup",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(backupInfo = backupInfo)

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.RestoreBackup, stateEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.EnterLocalBackupV1Passphrase)
  }

  // ==================== RestoreBackup with V2 Tests ====================

  @Test
  fun `RestoreBackup with V2 backup navigates to EnterAepForLocalBackup`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V2,
      date = LocalDateTime.now(),
      name = "backup.bin",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(backupInfo = backupInfo)

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.RestoreBackup, stateEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.EnterAepForLocalBackup)
  }

  // ==================== RestoreBackup with no backup Tests ====================

  @Test
  fun `RestoreBackup with null backupInfo does nothing`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = LocalBackupRestoreState(backupInfo = null)

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.RestoreBackup, stateEmitter)

    assertThat(emittedParentEvents).isEmpty()
    assertThat(emittedStates).isEmpty()
  }

  // ==================== ChooseDifferentFolder Tests ====================

  @Test
  fun `ChooseDifferentFolder resets state and sets launchFolderPicker to true`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V2,
      date = LocalDateTime.now(),
      name = "backup.bin",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(
      restorePhase = LocalBackupRestoreState.RestorePhase.BackupFound,
      backupInfo = backupInfo,
      selectedFolderUri = mockk()
    )

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.ChooseDifferentFolder, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().launchFolderPicker).isTrue()
    assertThat(emittedStates.last().restorePhase).isEqualTo(LocalBackupRestoreState.RestorePhase.SelectFolder)
    assertThat(emittedStates.last().backupInfo).isNull()
    assertThat(emittedStates.last().selectedFolderUri).isNull()
  }

  // ==================== BackupSelected Tests ====================

  @Test
  fun `BackupSelected updates backupInfo in state`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V2,
      date = LocalDateTime.now(),
      name = "backup.bin",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState()

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.BackupSelected(backupInfo), stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().backupInfo).isEqualTo(backupInfo)
  }

  // ==================== FolderPickerDismissed Tests ====================

  @Test
  fun `FolderPickerDismissed sets launchFolderPicker to false`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = LocalBackupRestoreState(launchFolderPicker = true)

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.FolderPickerDismissed, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().launchFolderPicker).isEqualTo(false)
  }

  // ==================== Cancel (pre-registration) Tests ====================

  @Test
  fun `Cancel when pre-registration sends Canceled result and emits NavigateBack`() = runTest {
    val viewModel = createViewModel(isPreRegistration = true)
    val initialState = LocalBackupRestoreState()

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.Cancel, stateEmitter)

    val result = resultBus.channelMap[resultKey]?.tryReceive()?.getOrNull()
    assertThat(result).isNotNull().isEqualTo(LocalBackupRestoreResult.Canceled)
    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  // ==================== Cancel (post-registration) Tests ====================

  @Test
  fun `Cancel when NOT pre-registration emits NavigateBack without sending result`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = LocalBackupRestoreState()

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.Cancel, stateEmitter)

    assertThat(resultBus.channelMap[resultKey]).isNull()
    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }
}
