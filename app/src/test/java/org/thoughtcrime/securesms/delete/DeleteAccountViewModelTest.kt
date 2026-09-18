/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.delete

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.signal.appsettings.deleteaccount.DeleteAccountAction
import org.signal.appsettings.deleteaccount.DeleteAccountEvent
import org.signal.appsettings.deleteaccount.DeleteAccountState.Dialog
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val repository = mockk<DeleteAccountRepository>(relaxUnitFun = true)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { repository.getFormattedWalletBalance() } returns null
    every { repository.getRegionDisplayName(any()) } returns ""
    every { repository.getRegionDisplayName("US") } returns "United States"
    every { repository.getRegionCountryCode("US") } returns 1
    every { repository.isNumberMatch(any(), any()) } returns true
    coEvery { repository.deleteAccount(any()) } returns DeleteAccountRepository.DeletionResult.Success
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `a wallet balance is read out of the repository up front`() = runTest(testDispatcher) {
    every { repository.getFormattedWalletBalance() } returns "0.1000 MOB"

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.walletBalance).isEqualTo("0.1000 MOB")
  }

  @Test
  fun `CountrySelected fills in the calling code and display name for the region`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(DeleteAccountEvent.CountrySelected("US"))

    assertThat(viewModel.state.value.regionCode).isEqualTo("US")
    assertThat(viewModel.state.value.countryCode).isEqualTo("1")
    assertThat(viewModel.state.value.countryDisplayName).isEqualTo("United States")
  }

  @Test
  fun `CountryCodeChanged keeps only digits and picks the matching region`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(DeleteAccountEvent.CountryCodeChanged("+1a"))

    assertThat(viewModel.state.value.countryCode).isEqualTo("1")
    assertThat(viewModel.state.value.regionCode).isEqualTo("US")
  }

  @Test
  fun `NationalNumberChanged keeps only digits and formats them for the region`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    viewModel.onEvent(DeleteAccountEvent.CountrySelected("US"))

    viewModel.onEvent(DeleteAccountEvent.NationalNumberChanged("(610) 555-0103"))

    assertThat(viewModel.state.value.nationalNumber).isEqualTo("6105550103")
    assertThat(viewModel.state.value.formattedNumber).isEqualTo("(610) 555-0103")
  }

  @Test
  fun `DeleteAccountClicked without a country code asks for one`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(DeleteAccountEvent.DeleteAccountClicked)

    assertThat(actions).contains(DeleteAccountAction.ShowNoCountryCode)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `DeleteAccountClicked without a number asks for one`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    viewModel.onEvent(DeleteAccountEvent.CountrySelected("US"))

    viewModel.onEvent(DeleteAccountEvent.DeleteAccountClicked)

    assertThat(actions).contains(DeleteAccountAction.ShowNoNationalNumber)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `DeleteAccountClicked with a number that isn't ours says so`() = runTest(testDispatcher) {
    every { repository.isNumberMatch(any(), any()) } returns false

    val viewModel = enterNumber()

    viewModel.onEvent(DeleteAccountEvent.DeleteAccountClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.NumberDoesNotMatch)
    coVerify(exactly = 0) { repository.deleteAccount(any()) }
  }

  @Test
  fun `DeleteAccountClicked with our own number asks for confirmation`() = runTest(testDispatcher) {
    val viewModel = enterNumber()

    viewModel.onEvent(DeleteAccountEvent.DeleteAccountClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmDeletion)
    coVerify(exactly = 0) { repository.deleteAccount(any()) }
  }

  @Test
  fun `DeletionConfirmed reports the progress it's told about`() = runTest(testDispatcher) {
    lateinit var viewModel: DeleteAccountViewModel
    val dialogs = mutableListOf<Dialog>()

    coEvery { repository.deleteAccount(any()) } answers {
      val onProgress = firstArg<(DeleteAccountRepository.Progress) -> Unit>()

      onProgress(DeleteAccountRepository.Progress.CancelingSubscription)
      dialogs += viewModel.state.value.dialog

      onProgress(DeleteAccountRepository.Progress.LeavingGroups(totalCount = 3, leaveCount = 1))
      dialogs += viewModel.state.value.dialog

      onProgress(DeleteAccountRepository.Progress.DeletingAccount)
      dialogs += viewModel.state.value.dialog

      DeleteAccountRepository.DeletionResult.Success
    }

    viewModel = createViewModel()

    viewModel.onEvent(DeleteAccountEvent.DeletionConfirmed)

    assertThat(dialogs).containsExactly(
      Dialog.CancelingSubscription,
      Dialog.LeavingGroups(totalCount = 3, leaveCount = 1),
      Dialog.DeletingAccount
    )
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `a deletion the network couldn't finish offers a retry`() = runTest(testDispatcher) {
    coEvery { repository.deleteAccount(any()) } returns DeleteAccountRepository.DeletionResult.ServerDeletionFailed

    val viewModel = createViewModel()

    viewModel.onEvent(DeleteAccountEvent.DeletionConfirmed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.DeletionFailed)
  }

  @Test
  fun `a deletion that couldn't wipe the device sends the user to the system settings`() = runTest(testDispatcher) {
    coEvery { repository.deleteAccount(any()) } returns DeleteAccountRepository.DeletionResult.LocalDataDeletionFailed

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(DeleteAccountEvent.DeletionConfirmed)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.LocalDataDeletionFailed)

    viewModel.onEvent(DeleteAccountEvent.LaunchAppSettingsClicked)
    assertThat(actions).contains(DeleteAccountAction.LaunchAppSettings)
  }

  @Test
  fun `CountryPickerClicked opens the picker`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(DeleteAccountEvent.CountryPickerClicked)

    assertThat(actions).contains(DeleteAccountAction.NavigateToCountryPicker)
  }

  @Test
  fun `NavigateBackClicked leaves the screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(DeleteAccountEvent.NavigateBackClicked)

    assertThat(actions).contains(DeleteAccountAction.NavigateBack)
  }

  @Test
  fun `DialogDismissed clears the dialog`() = runTest(testDispatcher) {
    val viewModel = enterNumber()
    viewModel.onEvent(DeleteAccountEvent.DeleteAccountClicked)

    viewModel.onEvent(DeleteAccountEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  private fun createViewModel(): DeleteAccountViewModel = DeleteAccountViewModel(repository)

  private fun enterNumber(): DeleteAccountViewModel {
    return createViewModel().apply {
      onEvent(DeleteAccountEvent.CountrySelected("US"))
      onEvent(DeleteAccountEvent.NationalNumberChanged("6105550103"))
    }
  }

  private fun TestScope.collectActions(actions: Flow<DeleteAccountAction>): List<DeleteAccountAction> {
    val collected = mutableListOf<DeleteAccountAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
