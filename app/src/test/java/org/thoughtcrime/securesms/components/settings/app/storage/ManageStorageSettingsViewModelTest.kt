/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.signal.appsettings.managestorage.ChatLengthLimitSettingsState
import org.signal.appsettings.managestorage.ManageStorageSettingsAction
import org.signal.appsettings.managestorage.ManageStorageSettingsEvent
import org.signal.appsettings.managestorage.ManageStorageSettingsState
import org.signal.appsettings.managestorage.ManageStorageSettingsState.Dialog
import org.signal.appsettings.managestorage.ManageStorageSettingsState.OnDeviceStorageOptimizationState
import org.signal.appsettings.managestorage.StorageUsage
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.models.storage.KeepMessagesDuration
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule

@OptIn(ExperimentalCoroutinesApi::class)
class ManageStorageSettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @get:Rule
  val signalStore = MockSignalStoreRule()

  private val repository = mockk<ManageStorageSettingsRepository>(relaxUnitFun = true)
  private val storageUsageRepository = mockk<StorageUsageRepository>(relaxUnitFun = true)
  private val storageUsage = MutableStateFlow<StorageUsage?>(null)
  private val isPaidTierPending = MutableStateFlow(false)
  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { storageUsageRepository.usage } returns storageUsage
    every { repository.observeIsPaidTierPending() } returns isPaidTierPending
    every { signalStore.settings.keepMessagesDuration } returns KeepMessagesDuration.FOREVER
    every { repository.getLengthLimit() } returns ChatLengthLimitSettingsState.NO_LIMIT
    every { signalStore.settings.syncThreadTrimDeletes } returns false
    every { signalStore.account.isPrimaryDevice } returns true
    every { signalStore.account.isMultiDevice } returns false
    every { signalStore.backup.newLocalBackupsEnabled } returns false
    every { signalStore.backup.optimizeStorage } returns false
    every { signalStore.backup.backupTier } returns MessageBackupTier.PAID
    coEvery { repository.isOptimizeStorageAvailable() } returns true
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state is read out of the store and repository`() = runTest(testDispatcher) {
    every { signalStore.settings.keepMessagesDuration } returns KeepMessagesDuration.SIX_MONTHS
    every { repository.getLengthLimit() } returns 500
    every { signalStore.settings.syncThreadTrimDeletes } returns true
    every { signalStore.account.isPrimaryDevice } returns false
    every { signalStore.account.isMultiDevice } returns true
    every { signalStore.backup.backupTier } returns MessageBackupTier.FREE

    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(
      ManageStorageSettingsState(
        onDeviceStorageOptimizationState = OnDeviceStorageOptimizationState.REQUIRES_PAID_TIER,
        keepMessagesDuration = KeepMessagesDuration.SIX_MONTHS,
        lengthLimit = 500,
        syncTrimDeletes = true,
        isPrimaryDevice = false,
        isMultiDevice = true
      )
    )
  }

  @Test
  fun `storage usage is shown as soon as it is computed`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    storageUsage.value = StorageUsage(photos = 100, messages = 50)

    assertThat(viewModel.state.value.storageUsage).isEqualTo(StorageUsage(photos = 100, messages = 50))
  }

  @Test
  fun `ScreenResumed recomputes storage usage`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ManageStorageSettingsEvent.ScreenResumed)

    verify { storageUsageRepository.refresh() }
  }

  @Test
  fun `ScreenResumed picks up chat limits changed on the sub-screens`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    every { signalStore.settings.keepMessagesDuration } returns KeepMessagesDuration.THIRTY_DAYS
    every { repository.getLengthLimit() } returns 500

    viewModel.onEvent(ManageStorageSettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.keepMessagesDuration).isEqualTo(KeepMessagesDuration.THIRTY_DAYS)
    assertThat(viewModel.state.value.lengthLimit).isEqualTo(500)
  }

  @Test
  fun `NavigateBackClicked leaves the screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ManageStorageSettingsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `KeepMessagesClicked and ChatLengthLimitClicked open their screens`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ManageStorageSettingsEvent.KeepMessagesClicked)
    viewModel.onEvent(ManageStorageSettingsEvent.ChatLengthLimitClicked)

    assertThat(navigationEvents).containsExactly(
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.DataAndStorageRoute.KeepMessages),
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.DataAndStorageRoute.ChatLengthLimit)
    )
  }

  @Test
  fun `ReviewStorageClicked opens the media overview`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(ManageStorageSettingsEvent.ReviewStorageClicked)

    assertThat(actions).containsExactly(ManageStorageSettingsAction.NavigateToReviewStorage)
  }

  @Test
  fun `SyncTrimDeletesToggled saves the setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ManageStorageSettingsEvent.SyncTrimDeletesToggled(true))

    verify { signalStore.settings.syncThreadTrimDeletes = true }
    assertThat(viewModel.state.value.syncTrimDeletes).isTrue()
  }

  @Test
  fun `deleting message history takes two confirmations`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ManageStorageSettingsEvent.DeleteChatHistoryClicked)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmDeleteChatHistory)

    viewModel.onEvent(ManageStorageSettingsEvent.DeleteChatHistoryConfirmed)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ReconfirmDeleteChatHistory)
    verify(exactly = 0) { repository.deleteAllConversations() }

    viewModel.onEvent(ManageStorageSettingsEvent.DeleteChatHistoryConfirmed)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    verify(exactly = 1) { repository.deleteAllConversations() }
  }

  @Test
  fun `dismissing the second delete confirmation deletes nothing`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ManageStorageSettingsEvent.DeleteChatHistoryClicked)
    viewModel.onEvent(ManageStorageSettingsEvent.DeleteChatHistoryConfirmed)
    viewModel.onEvent(ManageStorageSettingsEvent.DialogDismissed)
    viewModel.onEvent(ManageStorageSettingsEvent.DeleteChatHistoryConfirmed)

    verify(exactly = 0) { repository.deleteAllConversations() }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `turning on optimized storage saves it`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ManageStorageSettingsEvent.OptimizeStorageToggled(true))

    verify { signalStore.backup.optimizeStorage = true }
    assertThat(viewModel.state.value.onDeviceStorageOptimizationState).isEqualTo(OnDeviceStorageOptimizationState.ENABLED)
  }

  @Test
  fun `turning on optimized storage with local backups asks for confirmation first`() = runTest(testDispatcher) {
    every { signalStore.backup.newLocalBackupsEnabled } returns true
    val viewModel = createViewModel()

    viewModel.onEvent(ManageStorageSettingsEvent.OptimizeStorageToggled(true))
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmOptimizeStorageWithLocalBackup)
    verify(exactly = 0) { signalStore.backup.optimizeStorage = any() }

    viewModel.onEvent(ManageStorageSettingsEvent.OptimizeStorageWithLocalBackupConfirmed)

    verify { signalStore.backup.optimizeStorage = true }
    assertThat(viewModel.state.value.onDeviceStorageOptimizationState).isEqualTo(OnDeviceStorageOptimizationState.ENABLED)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `dismissing the local backup confirmation leaves optimized storage off`() = runTest(testDispatcher) {
    every { signalStore.backup.newLocalBackupsEnabled } returns true
    val viewModel = createViewModel()

    viewModel.onEvent(ManageStorageSettingsEvent.OptimizeStorageToggled(true))
    viewModel.onEvent(ManageStorageSettingsEvent.DialogDismissed)
    viewModel.onEvent(ManageStorageSettingsEvent.OptimizeStorageWithLocalBackupConfirmed)

    verify(exactly = 0) { signalStore.backup.optimizeStorage = any() }
    assertThat(viewModel.state.value.onDeviceStorageOptimizationState).isEqualTo(OnDeviceStorageOptimizationState.DISABLED)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `turning off optimized storage with local backups does not ask`() = runTest(testDispatcher) {
    every { signalStore.backup.newLocalBackupsEnabled } returns true
    every { signalStore.backup.optimizeStorage } returns true
    val viewModel = createViewModel()

    viewModel.onEvent(ManageStorageSettingsEvent.OptimizeStorageToggled(false))

    verify { signalStore.backup.optimizeStorage = false }
    assertThat(viewModel.state.value.onDeviceStorageOptimizationState).isEqualTo(OnDeviceStorageOptimizationState.DISABLED)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `toggling optimized storage while the paid tier is pending explains why it cannot be used yet`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    isPaidTierPending.value = true

    viewModel.onEvent(ManageStorageSettingsEvent.OptimizeStorageToggled(true))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.PaidTierPending)
    verify(exactly = 0) { signalStore.backup.optimizeStorage = any() }
  }

  @Test
  fun `toggling optimized storage without the paid tier offers the upgrade`() = runTest(testDispatcher) {
    every { signalStore.backup.backupTier } returns MessageBackupTier.FREE
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(ManageStorageSettingsEvent.OptimizeStorageToggled(true))

    assertThat(actions).containsExactly(ManageStorageSettingsAction.ShowUpgradeToEnableOptimizedStorage)
    verify(exactly = 0) { signalStore.backup.optimizeStorage = any() }
  }

  @Test
  fun `PaidTierUpgradeCompleted turns on optimized storage once the user is eligible`() = runTest(testDispatcher) {
    every { signalStore.backup.backupTier } returns MessageBackupTier.FREE
    val viewModel = createViewModel()
    every { signalStore.backup.backupTier } returns MessageBackupTier.PAID

    viewModel.onEvent(ManageStorageSettingsEvent.PaidTierUpgradeCompleted)

    verify { signalStore.backup.optimizeStorage = true }
    assertThat(viewModel.state.value.onDeviceStorageOptimizationState).isEqualTo(OnDeviceStorageOptimizationState.ENABLED)
  }

  @Test
  fun `PaidTierUpgradeCompleted does nothing while the user is still not on the paid tier`() = runTest(testDispatcher) {
    every { signalStore.backup.backupTier } returns MessageBackupTier.FREE
    val viewModel = createViewModel()

    viewModel.onEvent(ManageStorageSettingsEvent.PaidTierUpgradeCompleted)

    verify(exactly = 0) { signalStore.backup.optimizeStorage = any() }
    assertThat(viewModel.state.value.onDeviceStorageOptimizationState).isEqualTo(OnDeviceStorageOptimizationState.REQUIRES_PAID_TIER)
  }

  @Test
  fun `a slow availability check does not hold up other events`() = runTest(testDispatcher) {
    val availability = CompletableDeferred<Boolean>()
    coEvery { repository.isOptimizeStorageAvailable() } coAnswers { availability.await() }
    val viewModel = createViewModel()

    viewModel.onEvent(ManageStorageSettingsEvent.DeleteChatHistoryClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmDeleteChatHistory)
    assertThat(viewModel.state.value.onDeviceStorageOptimizationState).isEqualTo(OnDeviceStorageOptimizationState.FEATURE_NOT_AVAILABLE)

    availability.complete(true)

    assertThat(viewModel.state.value.onDeviceStorageOptimizationState).isEqualTo(OnDeviceStorageOptimizationState.DISABLED)
  }

  @Test
  fun `ScreenResumed picks up a paid tier upgrade made elsewhere`() = runTest(testDispatcher) {
    every { signalStore.backup.backupTier } returns MessageBackupTier.FREE
    val viewModel = createViewModel()
    assertThat(viewModel.state.value.onDeviceStorageOptimizationState).isEqualTo(OnDeviceStorageOptimizationState.REQUIRES_PAID_TIER)
    every { signalStore.backup.backupTier } returns MessageBackupTier.PAID

    viewModel.onEvent(ManageStorageSettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.onDeviceStorageOptimizationState).isEqualTo(OnDeviceStorageOptimizationState.DISABLED)
  }

  @Test
  fun `leaving after turning on optimized storage starts the offload`() = runTest(testDispatcher) {
    val (store, viewModel) = createStoredViewModel()

    viewModel.onEvent(ManageStorageSettingsEvent.OptimizeStorageToggled(true))
    store.clear()

    verify { repository.offloadOptimizedMedia() }
    verify(exactly = 0) { repository.restoreOptimizedMedia() }
  }

  @Test
  fun `leaving after turning off optimized storage restores the offloaded media`() = runTest(testDispatcher) {
    every { signalStore.backup.optimizeStorage } returns true
    val (store, viewModel) = createStoredViewModel()

    viewModel.onEvent(ManageStorageSettingsEvent.OptimizeStorageToggled(false))
    store.clear()

    verify { repository.restoreOptimizedMedia() }
    verify(exactly = 0) { repository.offloadOptimizedMedia() }
  }

  @Test
  fun `leaving after toggling optimized storage back to where it started does nothing`() = runTest(testDispatcher) {
    val (store, viewModel) = createStoredViewModel()

    viewModel.onEvent(ManageStorageSettingsEvent.OptimizeStorageToggled(true))
    viewModel.onEvent(ManageStorageSettingsEvent.OptimizeStorageToggled(false))
    store.clear()

    verify(exactly = 0) { repository.offloadOptimizedMedia() }
    verify(exactly = 0) { repository.restoreOptimizedMedia() }
  }

  private fun createViewModel(): ManageStorageSettingsViewModel {
    return ManageStorageSettingsViewModel(
      parentEventEmitter = { navigationEvents += it },
      repository = repository,
      storageUsageRepository = storageUsageRepository
    )
  }

  /** A view model held by a [ViewModelStore], so clearing the store runs `onCleared`. */
  private fun createStoredViewModel(): Pair<ViewModelStore, ManageStorageSettingsViewModel> {
    val store = ViewModelStore()
    val viewModel = ViewModelProvider(store, viewModelFactory { initializer { createViewModel() } })[ManageStorageSettingsViewModel::class.java]
    return store to viewModel
  }

  private fun TestScope.collectActions(actions: Flow<ManageStorageSettingsAction>): List<ManageStorageSettingsAction> {
    val collected = mutableListOf<ManageStorageSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
