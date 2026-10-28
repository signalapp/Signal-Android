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
import org.signal.appsettings.managestorage.KeepMessagesSettingsEvent
import org.signal.appsettings.managestorage.KeepMessagesSettingsState.Dialog
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.models.storage.KeepMessagesDuration
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule

@OptIn(ExperimentalCoroutinesApi::class)
class KeepMessagesSettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @get:Rule
  val signalStore = MockSignalStoreRule()

  private val repository = mockk<ManageStorageSettingsRepository>(relaxUnitFun = true)
  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { signalStore.settings.keepMessagesDuration } returns KeepMessagesDuration.FOREVER
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial selection is read out of the store`() = runTest(testDispatcher) {
    every { signalStore.settings.keepMessagesDuration } returns KeepMessagesDuration.SIX_MONTHS

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.selection).isEqualTo(KeepMessagesDuration.SIX_MONTHS)
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(KeepMessagesSettingsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `selecting a shorter duration asks for confirmation before saving`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(KeepMessagesSettingsEvent.DurationSelected(KeepMessagesDuration.THIRTY_DAYS))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmDuration(KeepMessagesDuration.THIRTY_DAYS))
    verify(exactly = 0) { repository.setKeepMessagesDuration(any()) }
  }

  @Test
  fun `DurationConfirmed saves the duration`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(KeepMessagesSettingsEvent.DurationSelected(KeepMessagesDuration.THIRTY_DAYS))
    viewModel.onEvent(KeepMessagesSettingsEvent.DurationConfirmed)

    verify { repository.setKeepMessagesDuration(KeepMessagesDuration.THIRTY_DAYS) }
    assertThat(viewModel.state.value.selection).isEqualTo(KeepMessagesDuration.THIRTY_DAYS)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `selecting a longer duration saves it without asking`() = runTest(testDispatcher) {
    every { signalStore.settings.keepMessagesDuration } returns KeepMessagesDuration.THIRTY_DAYS
    val viewModel = createViewModel()

    viewModel.onEvent(KeepMessagesSettingsEvent.DurationSelected(KeepMessagesDuration.ONE_YEAR))

    verify { repository.setKeepMessagesDuration(KeepMessagesDuration.ONE_YEAR) }
    assertThat(viewModel.state.value.selection).isEqualTo(KeepMessagesDuration.ONE_YEAR)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `dismissing the confirmation leaves the duration alone`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(KeepMessagesSettingsEvent.DurationSelected(KeepMessagesDuration.THIRTY_DAYS))
    viewModel.onEvent(KeepMessagesSettingsEvent.DialogDismissed)
    viewModel.onEvent(KeepMessagesSettingsEvent.DurationConfirmed)

    verify(exactly = 0) { repository.setKeepMessagesDuration(any()) }
    assertThat(viewModel.state.value.selection).isEqualTo(KeepMessagesDuration.FOREVER)
  }

  private fun createViewModel(): KeepMessagesSettingsViewModel {
    return KeepMessagesSettingsViewModel(
      parentEventEmitter = { navigationEvents += it },
      repository = repository
    )
  }
}
