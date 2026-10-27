/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.username

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.UsernameEditMode
import org.signal.appsettings.usernameedit.UsernameEditAction
import org.signal.appsettings.usernameedit.UsernameEditEvent
import org.signal.appsettings.usernameedit.UsernameEditState
import org.signal.core.util.Result
import org.signal.libsignal.usernames.Username
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameDeleteResult
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameSetResult
import org.thoughtcrime.securesms.profiles.manage.UsernameState
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class UsernameEditViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(UsernameEditRepository)

    every { UsernameEditRepository.getUsername() } returns null
    every { UsernameEditRepository.isNetworkConnected() } returns true
    every { UsernameEditRepository.markUsernameSet() } just Runs
    coEvery { UsernameEditRepository.reserveUsername(any(), any()) } answers {
      val nickname = firstArg<String>()
      val discriminator = secondArg<String?>() ?: "42"
      Result.success(UsernameState.Reserved(Username("$nickname.$discriminator")))
    }
    coEvery { UsernameEditRepository.confirmUsernameAndCreateNewLink(any()) } returns UsernameSetResult.SUCCESS
    coEvery { UsernameEditRepository.updateUsernameDisplayForCurrentLink(any()) } returns UsernameSetResult.SUCCESS
    coEvery { UsernameEditRepository.deleteUsernameAndLink() } returns UsernameDeleteResult.SUCCESS
  }

  @After
  fun tearDown() {
    unmockkObject(UsernameEditRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `without a username, the screen starts empty and prompts for one`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(UsernameEditState())
  }

  @Test
  fun `with a username, the screen starts with it filled in`() = runTest(testDispatcher) {
    every { UsernameEditRepository.getUsername() } returns "spider.42"

    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(
      UsernameEditState(nickname = "spider", discriminator = "42", showDiscriminator = true, username = "spider.42")
    )
  }

  @Test
  fun `typing a nickname reserves a discriminator once the user pauses`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameEditEvent.NicknameChanged("spider"))
    advanceTimeBy(UsernameEditViewModel.RESERVATION_DEBOUNCE_MILLIS - 1)
    coVerify(exactly = 0) { UsernameEditRepository.reserveUsername(any(), any()) }

    advanceTimeBy(2)
    runCurrent()

    coVerify(exactly = 1) { UsernameEditRepository.reserveUsername("spider", null) }
    assertThat(viewModel.state.value).isEqualTo(
      UsernameEditState(nickname = "spider", discriminator = "42", showDiscriminator = true, username = "spider.42", button = UsernameEditState.Button.SUBMIT)
    )
  }

  @Test
  fun `typing in quick succession only reserves what was typed last`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameEditEvent.NicknameChanged("spi"))
    advanceTimeBy(100)
    viewModel.onEvent(UsernameEditEvent.NicknameChanged("spider"))
    awaitReservation()

    coVerify(exactly = 1) { UsernameEditRepository.reserveUsername(any(), any()) }
    coVerify { UsernameEditRepository.reserveUsername("spider", null) }
  }

  @Test
  fun `typing an invalid nickname while a reservation is in flight stops the spinner`() = runTest(testDispatcher) {
    coEvery { UsernameEditRepository.reserveUsername(any(), any()) } coAnswers {
      delay(1_000)
      Result.success(UsernameState.Reserved(Username("spider.42")))
    }
    val viewModel = createViewModel()

    reserve(viewModel, "spider")
    assertThat(viewModel.state.value.reserving).isTrue()

    viewModel.onEvent(UsernameEditEvent.NicknameChanged("1spider"))
    awaitReservation()

    assertThat(viewModel.state.value.reserving).isFalse()
    assertThat(viewModel.state.value.error).isEqualTo(UsernameEditState.UsernameError.CANNOT_START_WITH_NUMBER)
  }

  @Test
  fun `an invalid nickname shows why without reserving it`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameEditEvent.NicknameChanged("1spider"))
    awaitReservation()

    coVerify(exactly = 0) { UsernameEditRepository.reserveUsername(any(), any()) }
    assertThat(viewModel.state.value.error).isEqualTo(UsernameEditState.UsernameError.CANNOT_START_WITH_NUMBER)
    assertThat(viewModel.state.value.button).isEqualTo(UsernameEditState.Button.SUBMIT_DISABLED)
  }

  @Test
  fun `a discriminator the user typed is reserved as typed`() = runTest(testDispatcher) {
    every { UsernameEditRepository.getUsername() } returns "spider.42"
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameEditEvent.DiscriminatorChanged("77"))
    awaitReservation()

    coVerify { UsernameEditRepository.reserveUsername("spider", "77") }
    assertThat(viewModel.state.value.username).isEqualTo("spider.77")
  }

  @Test
  fun `a taken discriminator the user typed says the number isn't available`() = runTest(testDispatcher) {
    every { UsernameEditRepository.getUsername() } returns "spider.42"
    coEvery { UsernameEditRepository.reserveUsername(any(), any()) } returns Result.failure(UsernameSetResult.USERNAME_UNAVAILABLE)
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameEditEvent.DiscriminatorChanged("77"))
    awaitReservation()

    assertThat(viewModel.state.value.error).isEqualTo(UsernameEditState.UsernameError.DISCRIMINATOR_NOT_AVAILABLE)
  }

  @Test
  fun `a failed reservation over the network shows a snackbar`() = runTest(testDispatcher) {
    coEvery { UsernameEditRepository.reserveUsername(any(), any()) } returns Result.failure(UsernameSetResult.NETWORK_ERROR)
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameEditEvent.NicknameChanged("spider"))
    awaitReservation()

    assertThat(viewModel.state.value.snackbar).isEqualTo(UsernameEditState.Snackbar.NetworkFailure)
    assertThat(viewModel.state.value.reserving).isFalse()
  }

  @Test
  fun `clearing an existing username offers to delete it`() = runTest(testDispatcher) {
    every { UsernameEditRepository.getUsername() } returns "spider.42"
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameEditEvent.NicknameChanged(""))

    assertThat(viewModel.state.value.button).isEqualTo(UsernameEditState.Button.DELETE)
  }

  @Test
  fun `submitting a first username saves it and navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    reserve(viewModel, "spider")
    viewModel.onEvent(UsernameEditEvent.SubmitClicked)

    coVerify { UsernameEditRepository.confirmUsernameAndCreateNewLink(Username("spider.42")) }
    verify { UsernameEditRepository.markUsernameSet() }
    assertThat(actions).containsExactly(UsernameEditAction.UsernameSaved)
    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `changing an existing username asks for confirmation first`() = runTest(testDispatcher) {
    every { UsernameEditRepository.getUsername() } returns "spider.42"
    val viewModel = createViewModel()

    reserve(viewModel, "venom")
    viewModel.onEvent(UsernameEditEvent.SubmitClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(UsernameEditState.Dialog.ConfirmReset)
    coVerify(exactly = 0) { UsernameEditRepository.confirmUsernameAndCreateNewLink(any()) }

    viewModel.onEvent(UsernameEditEvent.ResetConfirmed)

    assertThat(viewModel.state.value.dialog).isEqualTo(UsernameEditState.Dialog.None)
    coVerify { UsernameEditRepository.confirmUsernameAndCreateNewLink(Username("venom.42")) }
    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `dismissing the confirmation leaves the username alone`() = runTest(testDispatcher) {
    every { UsernameEditRepository.getUsername() } returns "spider.42"
    val viewModel = createViewModel()

    reserve(viewModel, "venom")
    viewModel.onEvent(UsernameEditEvent.SubmitClicked)
    viewModel.onEvent(UsernameEditEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(UsernameEditState.Dialog.None)
    coVerify(exactly = 0) { UsernameEditRepository.confirmUsernameAndCreateNewLink(any()) }
    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `changing only the case keeps the link and needs no confirmation`() = runTest(testDispatcher) {
    every { UsernameEditRepository.getUsername() } returns "spider.42"
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameEditEvent.NicknameChanged("Spider"))
    awaitReservation()
    viewModel.onEvent(UsernameEditEvent.SubmitClicked)

    coVerify(exactly = 0) { UsernameEditRepository.reserveUsername(any(), any()) }
    coVerify { UsernameEditRepository.updateUsernameDisplayForCurrentLink(Username("Spider.42")) }
    assertThat(viewModel.state.value.dialog).isEqualTo(UsernameEditState.Dialog.None)
  }

  @Test
  fun `a taken username at submit shows a snackbar and stays`() = runTest(testDispatcher) {
    coEvery { UsernameEditRepository.confirmUsernameAndCreateNewLink(any()) } returns UsernameSetResult.USERNAME_UNAVAILABLE
    val viewModel = createViewModel()

    reserve(viewModel, "spider")
    viewModel.onEvent(UsernameEditEvent.SubmitClicked)

    assertThat(viewModel.state.value.snackbar).isEqualTo(UsernameEditState.Snackbar.Taken)
    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `submitting while offline shows a snackbar without trying`() = runTest(testDispatcher) {
    every { UsernameEditRepository.isNetworkConnected() } returns false
    val viewModel = createViewModel()

    reserve(viewModel, "spider")
    viewModel.onEvent(UsernameEditEvent.SubmitClicked)

    assertThat(viewModel.state.value.snackbar).isEqualTo(UsernameEditState.Snackbar.NetworkFailure)
    coVerify(exactly = 0) { UsernameEditRepository.confirmUsernameAndCreateNewLink(any()) }
  }

  @Test
  fun `deleting the username tells the user and navigates back`() = runTest(testDispatcher) {
    every { UsernameEditRepository.getUsername() } returns "spider.42"
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(UsernameEditEvent.NicknameChanged(""))
    viewModel.onEvent(UsernameEditEvent.DeleteClicked)

    coVerify { UsernameEditRepository.deleteUsernameAndLink() }
    assertThat(actions).containsExactly(UsernameEditAction.UsernameDeleted)
    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `a failed delete shows a snackbar and offers to delete again`() = runTest(testDispatcher) {
    every { UsernameEditRepository.getUsername() } returns "spider.42"
    coEvery { UsernameEditRepository.deleteUsernameAndLink() } returns UsernameDeleteResult.NETWORK_ERROR
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameEditEvent.NicknameChanged(""))
    viewModel.onEvent(UsernameEditEvent.DeleteClicked)

    assertThat(viewModel.state.value.snackbar).isEqualTo(UsernameEditState.Snackbar.NetworkFailure)
    assertThat(viewModel.state.value.button).isEqualTo(UsernameEditState.Button.DELETE)
    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `recovery reserves the current username straight away`() = runTest(testDispatcher) {
    every { UsernameEditRepository.getUsername() } returns "spider.42"

    createViewModel(mode = UsernameEditMode.RECOVERY)
    awaitReservation()

    coVerify { UsernameEditRepository.reserveUsername("spider", "42") }
  }

  @Test
  fun `recovering the same username asks for confirmation first`() = runTest(testDispatcher) {
    every { UsernameEditRepository.getUsername() } returns "spider.42"
    val viewModel = createViewModel(mode = UsernameEditMode.RECOVERY)
    awaitReservation()

    viewModel.onEvent(UsernameEditEvent.SubmitClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(UsernameEditState.Dialog.ConfirmSameUsernameRecovery)

    viewModel.onEvent(UsernameEditEvent.SameUsernameRecoveryConfirmed)

    coVerify { UsernameEditRepository.confirmUsernameAndCreateNewLink(Username("spider.42")) }
  }

  @Test
  fun `leaving recovery finishes the activity`() = runTest(testDispatcher) {
    every { UsernameEditRepository.getUsername() } returns "spider.42"
    val viewModel = createViewModel(mode = UsernameEditMode.RECOVERY)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(UsernameEditEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(UsernameEditAction.Finish)
    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `the discriminator is capped at the maximum length`() = runTest(testDispatcher) {
    every { UsernameEditRepository.getUsername() } returns "spider.42"
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameEditEvent.DiscriminatorChanged("1234567890123"))

    assertThat(viewModel.state.value.discriminator).isEqualTo("123456789")
  }

  @Test
  fun `unsaved edits are restored from the saved state`() = runTest(testDispatcher) {
    val savedStateHandle = SavedStateHandle()
    createViewModel(savedStateHandle = savedStateHandle).onEvent(UsernameEditEvent.NicknameChanged("venom"))

    val restored = createViewModel(savedStateHandle = savedStateHandle)
    awaitReservation()

    assertThat(restored.state.value.nickname).isEqualTo("venom")
    coVerify { UsernameEditRepository.reserveUsername("venom", null) }
  }

  @Test
  fun `LearnMoreClicked opens the link`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(UsernameEditEvent.LearnMoreClicked("https://support.signal.org"))

    assertThat(actions).containsExactly(UsernameEditAction.OpenBrowserLink("https://support.signal.org"))
  }

  @Test
  fun `SnackbarDismissed clears the snackbar`() = runTest(testDispatcher) {
    every { UsernameEditRepository.isNetworkConnected() } returns false
    val viewModel = createViewModel()

    reserve(viewModel, "spider")
    viewModel.onEvent(UsernameEditEvent.SubmitClicked)
    viewModel.onEvent(UsernameEditEvent.SnackbarDismissed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(UsernameEditState.Snackbar.None)
    assertThat(viewModel.state.value.username).isEqualTo("spider.42")
    assertThat(viewModel.state.value.error).isNull()
    assertThat(viewModel.state.value.showDiscriminator).isTrue()
  }

  private fun TestScope.reserve(viewModel: UsernameEditViewModel, nickname: String) {
    viewModel.onEvent(UsernameEditEvent.NicknameChanged(nickname))
    awaitReservation()
  }

  /** Lets the pause before a reservation pass, and the reservation run. */
  private fun TestScope.awaitReservation() {
    advanceTimeBy(UsernameEditViewModel.RESERVATION_DEBOUNCE_MILLIS + 1)
    runCurrent()
  }

  private fun createViewModel(
    mode: UsernameEditMode = UsernameEditMode.NORMAL,
    savedStateHandle: SavedStateHandle = SavedStateHandle()
  ): UsernameEditViewModel {
    return UsernameEditViewModel(
      mode = mode,
      parentEventEmitter = { navigationEvents += it },
      savedStateHandle = savedStateHandle
    )
  }

  private fun TestScope.collectActions(actions: Flow<UsernameEditAction>): List<UsernameEditAction> {
    val collected = mutableListOf<UsernameEditAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
