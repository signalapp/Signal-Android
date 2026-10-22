/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
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
import org.signal.appsettings.home.AppSettingsAction
import org.signal.appsettings.home.AppSettingsEvent
import org.signal.appsettings.home.AppSettingsState
import org.signal.appsettings.home.AppSettingsState.Snackbar
import org.signal.appsettings.home.BackupFailureState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val profile = MutableStateFlow(AppSettingsState.Profile(name = "Miles Morales"))
  private val unreadPaymentsCount = MutableStateFlow(0)
  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  private var isPrimaryDevice = true
  private var userUnregistered = false
  private var backupFailureState = BackupFailureState.NONE

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(AppSettingsRepository)

    every { AppSettingsRepository.observeProfile() } returns profile
    every { AppSettingsRepository.observeUnreadPaymentsCount() } returns unreadPaymentsCount
    every { AppSettingsRepository.isPrimaryDevice() } answers { isPrimaryDevice }
    every { AppSettingsRepository.isUserUnregistered() } answers { userUnregistered }
    every { AppSettingsRepository.isClientDeprecated() } returns false
    every { AppSettingsRepository.hasExpiredGiftBadge() } returns false
    every { AppSettingsRepository.showInternalPreferences() } returns false
    every { AppSettingsRepository.showPayments() } returns true
    every { AppSettingsRepository.showAppUpdates() } returns false
    every { AppSettingsRepository.likelyCanManageDonations() } returns false
    coEvery { AppSettingsRepository.canManageDonations() } returns null
    every { AppSettingsRepository.getBackupFailureState() } answers { backupFailureState }
    every { AppSettingsRepository.markBackupFailedIndicatorClicked() } just Runs
    every { AppSettingsRepository.markBackupAlreadyRedeemedIndicatorClicked() } just Runs
    coEvery { AppSettingsRepository.copySubscriberIdToClipboard(any()) } returns true
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkObject(AppSettingsRepository)
  }

  @Test
  fun `initial state is read out of the repository`() = runTest(testDispatcher) {
    unreadPaymentsCount.value = 3

    val state = createViewModel().state.value

    assertThat(state.profile).isEqualTo(profile.value)
    assertThat(state.unreadPaymentsCount).isEqualTo(3)
    assertThat(state.isPrimaryDevice).isTrue()
    assertThat(state.showPayments).isTrue()
    assertThat(state.allowUserToGoToDonationManagementScreen).isFalse()
  }

  @Test
  fun `the fetched subscription decides whether donations can be managed`() = runTest(testDispatcher) {
    coEvery { AppSettingsRepository.canManageDonations() } returns true

    val state = createViewModel().state.value

    assertThat(state.allowUserToGoToDonationManagementScreen).isTrue()
  }

  @Test
  fun `a failed subscription fetch keeps the initial guess`() = runTest(testDispatcher) {
    every { AppSettingsRepository.likelyCanManageDonations() } returns true

    val state = createViewModel().state.value

    assertThat(state.allowUserToGoToDonationManagementScreen).isTrue()
  }

  @Test
  fun `unread payments update as they arrive`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    unreadPaymentsCount.value = 7

    assertThat(viewModel.state.value.unreadPaymentsCount).isEqualTo(7)
  }

  @Test
  fun `ScreenResumed picks up registration and backup changes`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    userUnregistered = true
    backupFailureState = BackupFailureState.OUT_OF_STORAGE_SPACE

    viewModel.onEvent(AppSettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.userUnregistered).isTrue()
    assertThat(viewModel.state.value.isRegisteredAndUpToDate).isFalse()
    assertThat(viewModel.state.value.backupFailureState).isEqualTo(BackupFailureState.OUT_OF_STORAGE_SPACE)
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppSettingsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `row clicks navigate to their routes`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppSettingsEvent.ProfileClicked)
    viewModel.onEvent(AppSettingsEvent.UsernameLinkClicked)
    viewModel.onEvent(AppSettingsEvent.AccountClicked)
    viewModel.onEvent(AppSettingsEvent.LinkedDevicesClicked)
    viewModel.onEvent(AppSettingsEvent.AppearanceClicked)
    viewModel.onEvent(AppSettingsEvent.ChatsClicked)
    viewModel.onEvent(AppSettingsEvent.StoriesClicked)
    viewModel.onEvent(AppSettingsEvent.NotificationsClicked)
    viewModel.onEvent(AppSettingsEvent.PrivacyClicked)
    viewModel.onEvent(AppSettingsEvent.BackupsClicked)
    viewModel.onEvent(AppSettingsEvent.DataAndStorageClicked)
    viewModel.onEvent(AppSettingsEvent.AppUpdatesClicked)
    viewModel.onEvent(AppSettingsEvent.PaymentsClicked)
    viewModel.onEvent(AppSettingsEvent.HelpClicked)
    viewModel.onEvent(AppSettingsEvent.InviteClicked)
    viewModel.onEvent(AppSettingsEvent.LabsClicked)
    viewModel.onEvent(AppSettingsEvent.InternalClicked)

    assertThat(navigationEvents.map { (it as AppSettingsNavigationEvent.NavigateToScreen).route }).containsExactly(
      AppSettingsRoute.AccountRoute.ManageProfile,
      AppSettingsRoute.UsernameLinkRoute.UsernameLink,
      AppSettingsRoute.AccountRoute.Account,
      AppSettingsRoute.LinkDeviceRoute.LinkDevice,
      AppSettingsRoute.AppearanceRoute.Appearance,
      AppSettingsRoute.ChatsRoute.Chats,
      AppSettingsRoute.StoriesRoute.Privacy(titleId = R.string.preferences__stories),
      AppSettingsRoute.NotificationsRoute.Notifications,
      AppSettingsRoute.PrivacyRoute.Privacy,
      AppSettingsRoute.BackupsRoute.Backups(),
      AppSettingsRoute.DataAndStorageRoute.DataAndStorage,
      AppSettingsRoute.AppUpdates,
      AppSettingsRoute.Payments,
      AppSettingsRoute.HelpRoute.Settings(),
      AppSettingsRoute.Invite,
      AppSettingsRoute.LabsRoute.Labs,
      AppSettingsRoute.InternalRoute.Internal
    )
  }

  @Test
  fun `DonateClicked opens donation management when the user can manage donations`() = runTest(testDispatcher) {
    every { AppSettingsRepository.likelyCanManageDonations() } returns true
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AppSettingsEvent.DonateClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.DonationsRoute.Donations()))
    assertThat(actions).isEmpty()
  }

  @Test
  fun `DonateClicked opens the donate page when the user can't manage donations`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AppSettingsEvent.DonateClicked)

    assertThat(actions).containsExactly(AppSettingsAction.OpenDonateUrl)
    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `DonateLongClicked copies the donor subscriber id`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppSettingsEvent.DonateLongClicked)

    coVerify { AppSettingsRepository.copySubscriberIdToClipboard(InAppPaymentSubscriberRecord.Type.DONATION) }
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.CopiedDonorSubscriberId)
  }

  @Test
  fun `DonateLongClicked says nothing when there is no subscriber`() = runTest(testDispatcher) {
    coEvery { AppSettingsRepository.copySubscriberIdToClipboard(any()) } returns false
    val viewModel = createViewModel()

    viewModel.onEvent(AppSettingsEvent.DonateLongClicked)

    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.None)
  }

  @Test
  fun `BackupsLongClicked copies the backups subscriber id on a primary device`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppSettingsEvent.BackupsLongClicked)

    coVerify { AppSettingsRepository.copySubscriberIdToClipboard(InAppPaymentSubscriberRecord.Type.BACKUP) }
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.CopiedBackupsSubscriberId)
  }

  @Test
  fun `BackupsLongClicked does nothing on a linked device`() = runTest(testDispatcher) {
    isPrimaryDevice = false
    val viewModel = createViewModel()

    viewModel.onEvent(AppSettingsEvent.BackupsLongClicked)

    coVerify(exactly = 0) { AppSettingsRepository.copySubscriberIdToClipboard(any()) }
  }

  @Test
  fun `SnackbarDismissed clears the snackbar`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppSettingsEvent.DonateLongClicked)
    viewModel.onEvent(AppSettingsEvent.SnackbarDismissed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.None)
  }

  @Test
  fun `BackupWarningClicked for a failed backup clears the indicator and opens remote backups`() = runTest(testDispatcher) {
    backupFailureState = BackupFailureState.COULD_NOT_COMPLETE_BACKUP
    val viewModel = createViewModel()

    viewModel.onEvent(AppSettingsEvent.BackupWarningClicked)

    verify { AppSettingsRepository.markBackupFailedIndicatorClicked() }
    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.BackupsRoute.Remote()))
  }

  @Test
  fun `BackupWarningClicked for an already redeemed subscription clears that indicator`() = runTest(testDispatcher) {
    backupFailureState = BackupFailureState.ALREADY_REDEEMED
    val viewModel = createViewModel()

    viewModel.onEvent(AppSettingsEvent.BackupWarningClicked)

    verify { AppSettingsRepository.markBackupAlreadyRedeemedIndicatorClicked() }
    verify(exactly = 0) { AppSettingsRepository.markBackupFailedIndicatorClicked() }
    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.BackupsRoute.Remote()))
  }

  @Test
  fun `BackupWarningClicked for full storage only opens remote backups`() = runTest(testDispatcher) {
    backupFailureState = BackupFailureState.OUT_OF_STORAGE_SPACE
    val viewModel = createViewModel()

    viewModel.onEvent(AppSettingsEvent.BackupWarningClicked)

    verify(exactly = 0) { AppSettingsRepository.markBackupFailedIndicatorClicked() }
    verify(exactly = 0) { AppSettingsRepository.markBackupAlreadyRedeemedIndicatorClicked() }
    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.BackupsRoute.Remote()))
  }

  @Test
  fun `BackupWarningClicked is ignored without a warning`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppSettingsEvent.BackupWarningClicked)

    assertThat(navigationEvents).isEmpty()
  }

  private fun createViewModel(): AppSettingsViewModel {
    return AppSettingsViewModel(parentEventEmitter = { navigationEvents += it })
  }

  private fun TestScope.collectActions(actions: Flow<AppSettingsAction>): List<AppSettingsAction> {
    val collected = mutableListOf<AppSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
