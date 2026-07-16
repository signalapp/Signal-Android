/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.remotebackuprestore

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.RestoreDecision
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

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

  private fun createViewModel(storageCapable: Boolean = false): RemoteBackupRestoreViewModel {
    return RemoteBackupRestoreViewModel(
      aep = aep,
      repository = mockRepository,
      parentState = MutableStateFlow(RegistrationFlowState(storageCapable = storageCapable)),
      parentEventEmitter = parentEventEmitter,
      ioDispatcher = testDispatcher
    )
  }

  /** Keeps the [WhileSubscribed] `state` flow hot and records every emission for assertions. */
  private fun TestScope.collectStatesOf(viewModel: RemoteBackupRestoreViewModel): List<RemoteBackupRestoreState> {
    val states = mutableListOf<RemoteBackupRestoreState>()
    backgroundScope.launch { viewModel.state.collect { states.add(it) } }
    return states
  }

  private fun backupInfo(usedSpace: Long? = 1024L) = NetworkController.GetBackupInfoResponse(
    cdn = 3,
    backupDir = "dir",
    mediaDir = "media",
    backupName = "backup",
    usedSpace = usedSpace
  )

  // ==================== BackupRestoreBackup ====================

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

  // ==================== Cancel ====================

  @Test
  fun `Cancel emits NavigateBack`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val initialState = RemoteBackupRestoreState(aep = aep)

    viewModel.applyEvent(initialState, RemoteBackupRestoreScreenEvents.Cancel, stateEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  // ==================== Retry ====================

  @Test
  fun `Retry reloads backup info and re-emits the current state`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)
    val currentState = RemoteBackupRestoreState(aep = aep)

    viewModel.applyEvent(currentState, RemoteBackupRestoreScreenEvents.Retry, stateEmitter)

    coVerify(exactly = 2) { mockRepository.getRemoteBackupInfo(aep) }
    assertThat(emittedStates).hasSize(1)
    assertThat(states.last().loadAttempts).isEqualTo(2)
  }

  @Test
  fun `init records a single load attempt`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    assertThat(states.last().loadAttempts).isEqualTo(1)
  }

  // ==================== DismissError ====================

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

  // ==================== loadBackupInfo ====================

  @Test
  fun `init with successful backup info invokes getRemoteBackupInfo and getBackupFileLastModified`() = runTest(testDispatcher) {
    val info = backupInfo()
    coEvery { mockRepository.getRemoteBackupInfo(any()) } returns RequestResult.Success(info)
    coEvery { mockRepository.getBackupFileLastModified(any(), any()) } returns RequestResult.Success(1234L)

    createViewModel()

    coVerify { mockRepository.getRemoteBackupInfo(aep) }
    coVerify { mockRepository.getBackupFileLastModified(aep, info) }
  }

  @Test
  fun `init with successful backup info moves to Loaded with size and time`() = runTest(testDispatcher) {
    coEvery { mockRepository.getRemoteBackupInfo(any()) } returns RequestResult.Success(backupInfo(usedSpace = 2048L))
    coEvery { mockRepository.getBackupFileLastModified(any(), any()) } returns RequestResult.Success(99999L)

    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    assertThat(states.last().loadState).isEqualTo(RemoteBackupRestoreState.LoadState.Loaded)
    assertThat(states.last().backupSize).isEqualTo(2048L)
    assertThat(states.last().backupTime).isEqualTo(99999L)
  }

  @Test
  fun `init with null usedSpace defaults backup size to zero`() = runTest(testDispatcher) {
    coEvery { mockRepository.getRemoteBackupInfo(any()) } returns RequestResult.Success(backupInfo(usedSpace = null))
    coEvery { mockRepository.getBackupFileLastModified(any(), any()) } returns RequestResult.Success(1L)

    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    assertThat(states.last().loadState).isEqualTo(RemoteBackupRestoreState.LoadState.Loaded)
    assertThat(states.last().backupSize).isEqualTo(0L)
  }

  @Test
  fun `init with successful info but failed last-modified lookup uses sentinel backup time`() = runTest(testDispatcher) {
    coEvery { mockRepository.getRemoteBackupInfo(any()) } returns RequestResult.Success(backupInfo())
    coEvery { mockRepository.getBackupFileLastModified(any(), any()) } returns
      RequestResult.NonSuccess(NetworkController.GetBackupInfoError.NoBackup)

    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    assertThat(states.last().loadState).isEqualTo(RemoteBackupRestoreState.LoadState.Loaded)
    assertThat(states.last().backupTime).isEqualTo(-1L)
  }

  @Test
  fun `init with NoBackup moves to NotFound`() = runTest(testDispatcher) {
    coEvery { mockRepository.getRemoteBackupInfo(any()) } returns
      RequestResult.NonSuccess(NetworkController.GetBackupInfoError.NoBackup)

    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    assertThat(states.last().loadState).isEqualTo(RemoteBackupRestoreState.LoadState.NotFound)
  }

  @Test
  fun `init with BadArguments moves to Failure`() = runTest(testDispatcher) {
    coEvery { mockRepository.getRemoteBackupInfo(any()) } returns
      RequestResult.NonSuccess(NetworkController.GetBackupInfoError.BadArguments())

    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    assertThat(states.last().loadState).isEqualTo(RemoteBackupRestoreState.LoadState.Failure)
  }

  @Test
  fun `init with BadAuthCredential moves to Failure`() = runTest(testDispatcher) {
    coEvery { mockRepository.getRemoteBackupInfo(any()) } returns
      RequestResult.NonSuccess(NetworkController.GetBackupInfoError.BadAuthCredential())

    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    assertThat(states.last().loadState).isEqualTo(RemoteBackupRestoreState.LoadState.Failure)
  }

  @Test
  fun `init with Forbidden moves to Failure`() = runTest(testDispatcher) {
    coEvery { mockRepository.getRemoteBackupInfo(any()) } returns
      RequestResult.NonSuccess(NetworkController.GetBackupInfoError.Forbidden())

    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    assertThat(states.last().loadState).isEqualTo(RemoteBackupRestoreState.LoadState.Failure)
  }

  @Test
  fun `init with RateLimited moves to Failure`() = runTest(testDispatcher) {
    coEvery { mockRepository.getRemoteBackupInfo(any()) } returns
      RequestResult.NonSuccess(NetworkController.GetBackupInfoError.RateLimited(30.seconds))

    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    assertThat(states.last().loadState).isEqualTo(RemoteBackupRestoreState.LoadState.Failure)
  }

  @Test
  fun `init with retryable network error moves to Failure`() = runTest(testDispatcher) {
    coEvery { mockRepository.getRemoteBackupInfo(any()) } returns
      RequestResult.RetryableNetworkError(IOException("Network error"))

    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    assertThat(states.last().loadState).isEqualTo(RemoteBackupRestoreState.LoadState.Failure)
  }

  @Test
  fun `init with application error moves to Failure`() = runTest(testDispatcher) {
    coEvery { mockRepository.getRemoteBackupInfo(any()) } returns
      RequestResult.ApplicationError(RuntimeException("Unexpected"))

    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    assertThat(states.last().loadState).isEqualTo(RemoteBackupRestoreState.LoadState.Failure)
  }

  // ==================== Restore progress ====================

  @Test
  fun `Downloading progress maps to InProgress with download bytes`() = runTest(testDispatcher) {
    every { mockRepository.restoreRemoteBackup(any()) } returns flowOf(
      RemoteBackupRestoreProgress.Downloading(bytesDownloaded = 30, totalBytes = 100)
    )
    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    viewModel.applyEvent(RemoteBackupRestoreState(aep = aep), RemoteBackupRestoreScreenEvents.BackupRestoreBackup, stateEmitter)

    val last = states.last()
    assertThat(last.restoreState).isEqualTo(RemoteBackupRestoreState.RestoreState.InProgress)
    assertThat(last.restoreProgress?.phase).isEqualTo(RemoteBackupRestoreState.RestoreProgress.Phase.Downloading)
    assertThat(last.restoreProgress?.bytesCompleted).isEqualTo(30L)
    assertThat(last.restoreProgress?.totalBytes).isEqualTo(100L)
  }

  @Test
  fun `Restoring progress maps to InProgress with restore bytes`() = runTest(testDispatcher) {
    every { mockRepository.restoreRemoteBackup(any()) } returns flowOf(
      RemoteBackupRestoreProgress.Restoring(bytesRead = 75, totalBytes = 100)
    )
    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    viewModel.applyEvent(RemoteBackupRestoreState(aep = aep), RemoteBackupRestoreScreenEvents.BackupRestoreBackup, stateEmitter)

    val last = states.last()
    assertThat(last.restoreState).isEqualTo(RemoteBackupRestoreState.RestoreState.InProgress)
    assertThat(last.restoreProgress?.phase).isEqualTo(RemoteBackupRestoreState.RestoreProgress.Phase.Restoring)
    assertThat(last.restoreProgress?.bytesCompleted).isEqualTo(75L)
  }

  @Test
  fun `Finalizing progress maps to InProgress with finalizing phase`() = runTest(testDispatcher) {
    every { mockRepository.restoreRemoteBackup(any()) } returns flowOf(
      RemoteBackupRestoreProgress.Finalizing
    )
    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    viewModel.applyEvent(RemoteBackupRestoreState(aep = aep), RemoteBackupRestoreScreenEvents.BackupRestoreBackup, stateEmitter)

    val last = states.last()
    assertThat(last.restoreState).isEqualTo(RemoteBackupRestoreState.RestoreState.InProgress)
    assertThat(last.restoreProgress?.phase).isEqualTo(RemoteBackupRestoreState.RestoreProgress.Phase.Finalizing)
  }

  @Test
  fun `Complete progress with a known pin completes registration`() = runTest(testDispatcher) {
    every { mockRepository.restoreRemoteBackup(any()) } returns flowOf(
      RemoteBackupRestoreProgress.Complete(restoredSvrPin = "1234", restoredProfileKey = null)
    )
    coEvery { mockRepository.hasKnownPin() } returns true

    val viewModel = createViewModel()
    val initialState = RemoteBackupRestoreState(aep = aep)

    viewModel.applyEvent(
      initialState,
      RemoteBackupRestoreScreenEvents.BackupRestoreBackup,
      stateEmitter
    )

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents[0]).isEqualTo(RegistrationFlowEvent.RegistrationComplete)
    coVerify { mockRepository.persistRestoredBackupState("1234", null) }
    coVerify { mockRepository.setRestoreDecision(RestoreDecision.COMPLETED) }
    coVerify { mockRepository.restoreAccountRecord(any()) }
  }

  @Test
  fun `Complete progress without a known pin navigates to pin creation when not storage capable`() = runTest(testDispatcher) {
    every { mockRepository.restoreRemoteBackup(any()) } returns flowOf(
      RemoteBackupRestoreProgress.Complete(restoredSvrPin = null, restoredProfileKey = null)
    )
    coEvery { mockRepository.hasKnownPin() } returns false

    val viewModel = createViewModel(storageCapable = false)

    viewModel.applyEvent(
      RemoteBackupRestoreState(aep = aep),
      RemoteBackupRestoreScreenEvents.BackupRestoreBackup,
      stateEmitter
    )

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents[0]).isEqualTo(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.PinCreate))
    coVerify { mockRepository.setRestoreDecision(RestoreDecision.COMPLETED) }
  }

  @Test
  fun `Complete progress without a known pin navigates to SVR pin entry when storage capable`() = runTest(testDispatcher) {
    every { mockRepository.restoreRemoteBackup(any()) } returns flowOf(
      RemoteBackupRestoreProgress.Complete(restoredSvrPin = null, restoredProfileKey = null)
    )
    coEvery { mockRepository.hasKnownPin() } returns false

    val viewModel = createViewModel(storageCapable = true)

    viewModel.applyEvent(
      RemoteBackupRestoreState(aep = aep),
      RemoteBackupRestoreScreenEvents.BackupRestoreBackup,
      stateEmitter
    )

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents[0]).isEqualTo(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.PinEntryForSvrRestore))
    coVerify { mockRepository.setRestoreDecision(RestoreDecision.COMPLETED) }
  }

  @Test
  fun `successful backup info emits UserSuppliedAepVerified`() = runTest(testDispatcher) {
    coEvery { mockRepository.getRemoteBackupInfo(any()) } returns RequestResult.Success(backupInfo())
    coEvery { mockRepository.getBackupFileLastModified(any(), any()) } returns RequestResult.Success(1234L)

    createViewModel()

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents[0]).isEqualTo(RegistrationFlowEvent.UserSuppliedAepVerified(aep))
  }

  @Test
  fun `Complete progress moves restore state to Restored and clears progress`() = runTest(testDispatcher) {
    every { mockRepository.restoreRemoteBackup(any()) } returns flowOf(
      RemoteBackupRestoreProgress.Complete(restoredSvrPin = null, restoredProfileKey = null)
    )
    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    viewModel.applyEvent(RemoteBackupRestoreState(aep = aep), RemoteBackupRestoreScreenEvents.BackupRestoreBackup, stateEmitter)

    assertThat(states.last().restoreState).isEqualTo(RemoteBackupRestoreState.RestoreState.Restored)
    assertThat(states.last().restoreProgress).isNull()
  }

  @Test
  fun `NetworkError progress moves restore state to NetworkFailure and emits no parent events`() = runTest(testDispatcher) {
    every { mockRepository.restoreRemoteBackup(any()) } returns flowOf(
      RemoteBackupRestoreProgress.NetworkError()
    )
    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    viewModel.applyEvent(RemoteBackupRestoreState(aep = aep), RemoteBackupRestoreScreenEvents.BackupRestoreBackup, stateEmitter)

    coVerify { mockRepository.restoreRemoteBackup(aep) }
    assertThat(states.last().restoreState).isEqualTo(RemoteBackupRestoreState.RestoreState.NetworkFailure)
    assertThat(emittedParentEvents).hasSize(0)
  }

  @Test
  fun `InvalidBackupVersion progress moves restore state to InvalidBackupVersion`() = runTest(testDispatcher) {
    every { mockRepository.restoreRemoteBackup(any()) } returns flowOf(
      RemoteBackupRestoreProgress.InvalidBackupVersion
    )
    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    viewModel.applyEvent(RemoteBackupRestoreState(aep = aep), RemoteBackupRestoreScreenEvents.BackupRestoreBackup, stateEmitter)

    assertThat(states.last().restoreState).isEqualTo(RemoteBackupRestoreState.RestoreState.InvalidBackupVersion)
  }

  @Test
  fun `PermanentSvrBFailure progress moves restore state to PermanentSvrBFailure`() = runTest(testDispatcher) {
    every { mockRepository.restoreRemoteBackup(any()) } returns flowOf(
      RemoteBackupRestoreProgress.PermanentSvrBFailure
    )
    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    viewModel.applyEvent(RemoteBackupRestoreState(aep = aep), RemoteBackupRestoreScreenEvents.BackupRestoreBackup, stateEmitter)

    assertThat(states.last().restoreState).isEqualTo(RemoteBackupRestoreState.RestoreState.PermanentSvrBFailure)
  }

  @Test
  fun `Canceled progress moves restore state to Failed`() = runTest(testDispatcher) {
    every { mockRepository.restoreRemoteBackup(any()) } returns flowOf(
      RemoteBackupRestoreProgress.Canceled
    )
    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    viewModel.applyEvent(RemoteBackupRestoreState(aep = aep), RemoteBackupRestoreScreenEvents.BackupRestoreBackup, stateEmitter)

    assertThat(states.last().restoreState).isEqualTo(RemoteBackupRestoreState.RestoreState.Failed)
  }

  @Test
  fun `GenericError progress moves restore state to Failed`() = runTest(testDispatcher) {
    every { mockRepository.restoreRemoteBackup(any()) } returns flowOf(
      RemoteBackupRestoreProgress.GenericError()
    )
    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    viewModel.applyEvent(RemoteBackupRestoreState(aep = aep), RemoteBackupRestoreScreenEvents.BackupRestoreBackup, stateEmitter)

    assertThat(states.last().restoreState).isEqualTo(RemoteBackupRestoreState.RestoreState.Failed)
  }

  @Test
  fun `a multi-phase progress sequence ends in Restored`() = runTest(testDispatcher) {
    every { mockRepository.restoreRemoteBackup(any()) } returns flowOf(
      RemoteBackupRestoreProgress.Downloading(bytesDownloaded = 10, totalBytes = 100),
      RemoteBackupRestoreProgress.Restoring(bytesRead = 60, totalBytes = 100),
      RemoteBackupRestoreProgress.Finalizing,
      RemoteBackupRestoreProgress.Complete(restoredSvrPin = "1234", restoredProfileKey = null)
    )
    coEvery { mockRepository.hasKnownPin() } returns true
    val viewModel = createViewModel()
    val states = collectStatesOf(viewModel)

    viewModel.applyEvent(RemoteBackupRestoreState(aep = aep), RemoteBackupRestoreScreenEvents.BackupRestoreBackup, stateEmitter)

    assertThat(states.last().restoreState).isEqualTo(RemoteBackupRestoreState.RestoreState.Restored)
    coVerify { mockRepository.restoreAccountRecord(any()) }
  }
}
