/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.remotebackuprestore

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteBackupRestoreViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var mockRepository: RegistrationRepository
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<RemoteBackupRestoreState>
  private lateinit var stateEmitter: (RemoteBackupRestoreState) -> Unit
  private lateinit var aep: AccountEntropyPool

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    aep = AccountEntropyPool.generate()
    mockRepository = mockk(relaxed = true)
    every { mockRepository.restoreRemoteBackup(any()) } returns emptyFlow()
    coEvery { mockRepository.getRemoteBackupInfo(any()) } returns
      RequestResult.NonSuccess(NetworkController.GetBackupInfoError.NoBackup)
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(): RemoteBackupRestoreViewModel {
    return RemoteBackupRestoreViewModel(
      aep = aep,
      repository = mockRepository,
      parentEventEmitter = parentEventEmitter,
      ioDispatcher = testDispatcher
    )
  }

  // ==================== BackupRestoreBackup Tests ====================

  @Test
  fun `BackupRestoreBackup emits InProgress state and triggers restore`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val initialState = RemoteBackupRestoreState(aep = aep)

    viewModel.applyEvent(
      initialState,
      RemoteBackupRestoreScreenEvents.BackupRestoreBackup,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().restoreState).isEqualTo(RemoteBackupRestoreState.RestoreState.InProgress)
    coVerify { mockRepository.restoreRemoteBackup(aep) }
  }

  // ==================== Cancel Tests ====================

  @Test
  fun `Cancel emits NavigateBack`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val initialState = RemoteBackupRestoreState(aep = aep)

    viewModel.applyEvent(initialState, RemoteBackupRestoreScreenEvents.Cancel, stateEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  // ==================== DismissError Tests ====================

  @Test
  fun `DismissError resets restoreState to None and clears progress`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val initialState = RemoteBackupRestoreState(
      aep = aep,
      restoreState = RemoteBackupRestoreState.RestoreState.Failed,
      restoreProgress = RemoteBackupRestoreState.RestoreProgress(
        phase = RemoteBackupRestoreState.RestoreProgress.Phase.Downloading,
        bytesCompleted = 50,
        totalBytes = 100
      )
    )

    viewModel.applyEvent(initialState, RemoteBackupRestoreScreenEvents.DismissError, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().restoreState).isEqualTo(RemoteBackupRestoreState.RestoreState.None)
    assertThat(emittedStates.last().restoreProgress).isNull()
  }

  // ==================== loadBackupInfo Tests ====================

  @Test
  fun `init with successful backup info invokes getRemoteBackupInfo and getBackupFileLastModified`() = runTest(testDispatcher) {
    val info = NetworkController.GetBackupInfoResponse(
      cdn = 3,
      backupDir = "dir",
      mediaDir = "media",
      backupName = "backup",
      usedSpace = 1024L
    )
    coEvery { mockRepository.getRemoteBackupInfo(any()) } returns RequestResult.Success(info)
    coEvery { mockRepository.getBackupFileLastModified(any(), any()) } returns RequestResult.Success(1234L)

    createViewModel()

    coVerify { mockRepository.getRemoteBackupInfo(aep) }
    coVerify { mockRepository.getBackupFileLastModified(aep, info) }
  }

  @Test
  fun `init with NoBackup error invokes getRemoteBackupInfo`() = runTest(testDispatcher) {
    coEvery { mockRepository.getRemoteBackupInfo(any()) } returns
      RequestResult.NonSuccess(NetworkController.GetBackupInfoError.NoBackup)

    createViewModel()

    coVerify { mockRepository.getRemoteBackupInfo(aep) }
  }

  // ==================== Restore Progress Tests ====================

  @Test
  fun `BackupRestoreBackup Complete progress emits RegistrationComplete and UserSuppliedAepVerified`() = runTest(testDispatcher) {
    every { mockRepository.restoreRemoteBackup(any()) } returns flowOf(
      RemoteBackupRestoreProgress.Complete
    )

    val viewModel = createViewModel()
    val initialState = RemoteBackupRestoreState(aep = aep)

    viewModel.applyEvent(
      initialState,
      RemoteBackupRestoreScreenEvents.BackupRestoreBackup,
      stateEmitter
    )

    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.UserSuppliedAepVerified>()
    assertThat(emittedParentEvents[1]).isEqualTo(RegistrationFlowEvent.RegistrationComplete)
  }

  @Test
  fun `BackupRestoreBackup NetworkError progress triggers restore and emits no parent events`() = runTest(testDispatcher) {
    every { mockRepository.restoreRemoteBackup(any()) } returns flowOf(
      RemoteBackupRestoreProgress.NetworkError()
    )

    val viewModel = createViewModel()
    val initialState = RemoteBackupRestoreState(aep = aep)

    viewModel.applyEvent(
      initialState,
      RemoteBackupRestoreScreenEvents.BackupRestoreBackup,
      stateEmitter
    )

    coVerify { mockRepository.restoreRemoteBackup(aep) }
    assertThat(emittedParentEvents).hasSize(0)
  }
}
