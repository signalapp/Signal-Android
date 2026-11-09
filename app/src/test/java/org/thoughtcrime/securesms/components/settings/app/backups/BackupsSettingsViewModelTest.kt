/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class BackupsSettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private val backupState = MutableStateFlow<BackupState>(BackupState.None)
  private val parentEvents = mutableListOf<AppSettingsNavigationEvent>()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    mockkObject(BackupsSettingsRepository)
    every { BackupsSettingsRepository.observeBackupState(any()) } returns backupState
    every { BackupsSettingsRepository.lastBackupTime() } returns 0L
    every { BackupsSettingsRepository.lastBackupTimeFlow() } returns emptyFlow()
    every { BackupsSettingsRepository.isPrimaryDevice() } returns true
    every { BackupsSettingsRepository.isLinkedDevice() } returns false
    every { BackupsSettingsRepository.canOverrideBackupTier() } returns false
    every { BackupsSettingsRepository.backupTierInternalOverride() } returns null
    coEvery { BackupsSettingsRepository.refreshBackupFileTimestamp() } returns Unit
    coEvery { BackupsSettingsRepository.setBackupTierInternalOverride(any(), any()) } returns Unit
  }

  @After
  fun tearDown() {
    unmockkObject(BackupsSettingsRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `state follows the observed backup state`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    backupState.value = BackupState.NotFound

    assertThat(viewModel.state.value.backupState).isEqualTo(BackupState.NotFound)
  }

  @Test
  fun `NavigateBackClicked leaves the screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(BackupsSettingsEvent.NavigateBackClicked)

    assertThat(parentEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack())
  }

  @Test
  fun `BackupsRowClicked with no backups starts checkout on a primary device`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(BackupsSettingsEvent.BackupsRowClicked)

    assertThat(actions).containsExactly(BackupsSettingsAction.LaunchCheckout)
    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `BackupsRowClicked with no backups does nothing on a linked device`() = runTest(testDispatcher) {
    every { BackupsSettingsRepository.isLinkedDevice() } returns true
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(BackupsSettingsEvent.BackupsRowClicked)

    assertThat(actions).isEmpty()
    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `BackupsRowClicked in an error state does nothing`() = runTest(testDispatcher) {
    backupState.value = BackupState.Error(BackupState.None)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(BackupsSettingsEvent.BackupsRowClicked)

    assertThat(actions).isEmpty()
    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `BackupsRowClicked with a backup opens the remote backups screen`() = runTest(testDispatcher) {
    backupState.value = BackupState.LocalStore(MessageBackupTier.FREE)
    val viewModel = createViewModel()

    viewModel.onEvent(BackupsSettingsEvent.BackupsRowClicked)

    assertThat(parentEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.BackupsRoute.Remote()))
  }

  @Test
  fun `OnDeviceBackupsRowClicked opens on-device backups`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(BackupsSettingsEvent.OnDeviceBackupsRowClicked)

    assertThat(parentEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.BackupsRoute.Local()))
  }

  @Test
  fun `CheckoutCompleted opens the remote backups screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(BackupsSettingsEvent.CheckoutCompleted)

    assertThat(parentEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.BackupsRoute.Remote()))
  }

  @Test
  fun `LearnMoreClicked opens the support article`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(BackupsSettingsEvent.LearnMoreClicked)

    assertThat(actions).containsExactly(BackupsSettingsAction.OpenSupportArticle("https://support.signal.org/hc/articles/360007059752"))
  }

  @Test
  fun `BackupTierInternalOverrideChanged applies the override`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(BackupsSettingsEvent.BackupTierInternalOverrideChanged(MessageBackupTier.PAID))

    coVerify { BackupsSettingsRepository.setBackupTierInternalOverride(MessageBackupTier.PAID, any()) }
  }

  @Test
  fun `a linked device refreshes the backup file timestamp`() = runTest(testDispatcher) {
    every { BackupsSettingsRepository.isLinkedDevice() } returns true

    createViewModel()

    coVerify { BackupsSettingsRepository.refreshBackupFileTimestamp() }
  }

  @Test
  fun `opening to launch checkout starts it once on a primary device`() = runTest(testDispatcher) {
    val savedStateHandle = SavedStateHandle()
    val first = createViewModel(launchCheckoutFlow = true, savedStateHandle = savedStateHandle)
    val firstActions = collectActions(first)

    val restored = createViewModel(launchCheckoutFlow = true, savedStateHandle = savedStateHandle)
    val restoredActions = collectActions(restored)

    assertThat(firstActions).containsExactly(BackupsSettingsAction.LaunchCheckout)
    assertThat(restoredActions).isEmpty()
  }

  @Test
  fun `opening to launch checkout does nothing on a linked device`() = runTest(testDispatcher) {
    every { BackupsSettingsRepository.isPrimaryDevice() } returns false
    val viewModel = createViewModel(launchCheckoutFlow = true)
    val actions = collectActions(viewModel)

    assertThat(actions).isEmpty()
  }

  private fun createViewModel(launchCheckoutFlow: Boolean = false, savedStateHandle: SavedStateHandle = SavedStateHandle()): BackupsSettingsViewModel {
    return BackupsSettingsViewModel(
      parentEventEmitter = { parentEvents += it },
      launchCheckoutFlow = launchCheckoutFlow,
      savedStateHandle = savedStateHandle
    )
  }

  private fun TestScope.collectActions(viewModel: BackupsSettingsViewModel): List<BackupsSettingsAction> {
    val collected = mutableListOf<BackupsSettingsAction>()
    backgroundScope.launch { viewModel.actions.toList(collected) }
    return collected
  }
}
