/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.managestorage.ChatLengthLimitSettingsEvent
import org.signal.appsettings.managestorage.ChatLengthLimitSettingsState
import org.signal.appsettings.managestorage.ChatLengthLimitSettingsState.Dialog
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ChatLengthLimitSettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val repository = mockk<ManageStorageSettingsRepository>(relaxUnitFun = true)
  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { repository.getLengthLimit() } returns ChatLengthLimitSettingsState.NO_LIMIT
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial limit is read out of the repository`() = runTest(testDispatcher) {
    every { repository.getLengthLimit() } returns 500

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.lengthLimit).isEqualTo(500)
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ChatLengthLimitSettingsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack())
  }

  @Test
  fun `setting a first limit asks for confirmation, then saves and trims`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ChatLengthLimitSettingsEvent.LimitSelected(1000))
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmLimit(1000))
    verify(exactly = 0) { repository.setLengthLimit(any()) }

    viewModel.onEvent(ChatLengthLimitSettingsEvent.LimitConfirmed)

    verify { repository.setLengthLimit(1000) }
    verify { repository.trimAllThreads(1000) }
    assertThat(viewModel.state.value.lengthLimit).isEqualTo(1000)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `lowering the limit asks for confirmation`() = runTest(testDispatcher) {
    every { repository.getLengthLimit() } returns 1000
    val viewModel = createViewModel()

    viewModel.onEvent(ChatLengthLimitSettingsEvent.LimitSelected(100))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmLimit(100))
    verify(exactly = 0) { repository.setLengthLimit(any()) }
  }

  @Test
  fun `raising the limit saves it without asking or trimming`() = runTest(testDispatcher) {
    every { repository.getLengthLimit() } returns 500
    val viewModel = createViewModel()

    viewModel.onEvent(ChatLengthLimitSettingsEvent.LimitSelected(5000))

    verify { repository.setLengthLimit(5000) }
    verify(exactly = 0) { repository.trimAllThreads(any()) }
    assertThat(viewModel.state.value.lengthLimit).isEqualTo(5000)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `removing the limit saves it without asking or trimming`() = runTest(testDispatcher) {
    every { repository.getLengthLimit() } returns 500
    val viewModel = createViewModel()

    viewModel.onEvent(ChatLengthLimitSettingsEvent.LimitSelected(ChatLengthLimitSettingsState.NO_LIMIT))

    verify { repository.setLengthLimit(ChatLengthLimitSettingsState.NO_LIMIT) }
    verify(exactly = 0) { repository.trimAllThreads(any()) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `selecting no limit when there already is none does not ask for confirmation`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ChatLengthLimitSettingsEvent.LimitSelected(ChatLengthLimitSettingsState.NO_LIMIT))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    verify(exactly = 0) { repository.trimAllThreads(any()) }
  }

  @Test
  fun `dismissing the confirmation leaves the limit alone`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ChatLengthLimitSettingsEvent.LimitSelected(1000))
    viewModel.onEvent(ChatLengthLimitSettingsEvent.DialogDismissed)
    viewModel.onEvent(ChatLengthLimitSettingsEvent.LimitConfirmed)

    verify(exactly = 0) { repository.setLengthLimit(any()) }
    assertThat(viewModel.state.value.lengthLimit).isEqualTo(ChatLengthLimitSettingsState.NO_LIMIT)
  }

  @Test
  fun `CustomClicked starts the dialog from the current limit`() = runTest(testDispatcher) {
    every { repository.getLengthLimit() } returns 1234
    val viewModel = createViewModel()

    viewModel.onEvent(ChatLengthLimitSettingsEvent.CustomClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.CustomLimit(initialLimit = 1234))
  }

  @Test
  fun `CustomClicked starts the dialog empty when there is no limit`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ChatLengthLimitSettingsEvent.CustomClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.CustomLimit(initialLimit = null))
  }

  @Test
  fun `entering a tighter custom limit asks for confirmation`() = runTest(testDispatcher) {
    every { repository.getLengthLimit() } returns 1000
    val viewModel = createViewModel()

    viewModel.onEvent(ChatLengthLimitSettingsEvent.CustomClicked)
    viewModel.onEvent(ChatLengthLimitSettingsEvent.CustomLimitEntered(250))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmLimit(250))
    verify(exactly = 0) { repository.setLengthLimit(any()) }
  }

  @Test
  fun `entering a looser custom limit saves it straight away`() = runTest(testDispatcher) {
    every { repository.getLengthLimit() } returns 1000
    val viewModel = createViewModel()

    viewModel.onEvent(ChatLengthLimitSettingsEvent.CustomClicked)
    viewModel.onEvent(ChatLengthLimitSettingsEvent.CustomLimitEntered(2500))

    verify { repository.setLengthLimit(2500) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  private fun createViewModel(): ChatLengthLimitSettingsViewModel {
    return ChatLengthLimitSettingsViewModel(
      parentEventEmitter = { navigationEvents += it },
      repository = repository
    )
  }
}
