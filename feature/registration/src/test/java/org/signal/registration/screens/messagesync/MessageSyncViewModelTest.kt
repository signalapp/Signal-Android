/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.messagesync

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.signal.core.util.bytes
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute

@OptIn(ExperimentalCoroutinesApi::class)
class MessageSyncViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var mockRepository: RegistrationRepository
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
    every { mockRepository.restoreLinkAndSyncBackup() } returns MutableSharedFlow()
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `restore Complete restores from storage service then navigates to FullyComplete`() = runTest(testDispatcher) {
    every { mockRepository.restoreLinkAndSyncBackup() } returns flowOf(LinkAndSyncProgress.Complete)

    createViewModel()

    coVerify { mockRepository.restoreLinkedDeviceFromStorageService() }
    assertThat(emittedParentEvents).contains(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.FullyComplete))
  }

  @Test
  fun `restore Failed shows the retry dialog and does not finish registration`() = runTest(testDispatcher) {
    every { mockRepository.restoreLinkAndSyncBackup() } returns flowOf(LinkAndSyncProgress.Failed())

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.showSyncFailedDialog).isTrue()
    assertThat(viewModel.state.value.isFinishing).isFalse()
    coVerify(exactly = 0) { mockRepository.restoreLinkedDeviceFromStorageService() }
    assertThat(emittedParentEvents).doesNotContain(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.FullyComplete))
  }

  @Test
  fun `applyEvent RetryClick clears the dialog and restarts the restore`() = runTest(testDispatcher) {
    every { mockRepository.restoreLinkAndSyncBackup() } returns flowOf(LinkAndSyncProgress.Failed())

    val viewModel = createViewModel()
    var emitted: MessageSyncScreenState? = null
    viewModel.applyEvent(viewModel.state.value, MessageSyncScreenEvent.RetryClick) { emitted = it }

    assertThat(emitted!!.showSyncFailedDialog).isFalse()
    // Once on init, once on retry.
    verify(exactly = 2) { mockRepository.restoreLinkAndSyncBackup() }
  }

  @Test
  fun `applyEvent ContinueWithoutMessagesClick restores from storage service then navigates to FullyComplete`() = runTest(testDispatcher) {
    every { mockRepository.restoreLinkAndSyncBackup() } returns flowOf(LinkAndSyncProgress.Failed())

    val viewModel = createViewModel()
    viewModel.applyEvent(viewModel.state.value, MessageSyncScreenEvent.ContinueWithoutMessagesClick) {}

    coVerify { mockRepository.restoreLinkedDeviceFromStorageService() }
    assertThat(emittedParentEvents).contains(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.FullyComplete))
  }

  @Test
  fun `restore RelinkRequired wipes local data and does not finish registration`() = runTest(testDispatcher) {
    every { mockRepository.restoreLinkAndSyncBackup() } returns flowOf(LinkAndSyncProgress.RelinkRequired)

    createViewModel()

    coVerify { mockRepository.clearLocalDataAndRestart() }
    coVerify(exactly = 0) { mockRepository.restoreLinkedDeviceFromStorageService() }
    assertThat(emittedParentEvents).doesNotContain(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.FullyComplete))
  }

  @Test
  fun `applyEvent CancelClick restores from storage service then navigates to FullyComplete`() = runTest(testDispatcher) {
    // A never-emitting flow keeps the restore job active so cancelAndJoin has something to wait on.
    every { mockRepository.restoreLinkAndSyncBackup() } returns MutableSharedFlow()

    val viewModel = createViewModel()
    viewModel.applyEvent(MessageSyncScreenState(), MessageSyncScreenEvent.CancelClick) {}

    coVerify { mockRepository.restoreLinkedDeviceFromStorageService() }
    assertThat(emittedParentEvents).contains(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.FullyComplete))
  }

  @Test
  fun `restore Downloading shows byte progress and is not finishing`() = runTest(testDispatcher) {
    every { mockRepository.restoreLinkAndSyncBackup() } returns flowOf(LinkAndSyncProgress.Downloading(bytesDownloaded = 5.bytes, totalBytes = 10.bytes))

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.isFinishing).isFalse()
  }

  @Test
  fun `restore Restoring switches to indeterminate finishing state`() = runTest(testDispatcher) {
    every { mockRepository.restoreLinkAndSyncBackup() } returns flowOf(
      LinkAndSyncProgress.Downloading(bytesDownloaded = 5.bytes, totalBytes = 10.bytes),
      LinkAndSyncProgress.Restoring
    )

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.isFinishing).isTrue()
  }

  private fun TestScope.createViewModel(): MessageSyncViewModel {
    val viewModel = MessageSyncViewModel(
      repository = mockRepository,
      parentState = MutableStateFlow(RegistrationFlowState()),
      parentEventEmitter = parentEventEmitter
    )
    // Keep the WhileSubscribed state flow hot so state.value reflects updates during the test.
    backgroundScope.launch { viewModel.state.collect {} }
    return viewModel
  }
}
